package cn.rainafter.app.gazekeep.backend.lsposed

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import cn.rainafter.app.gazekeep.core.backend.ExtendResult
import cn.rainafter.app.gazekeep.core.backend.ExtendToken
import cn.rainafter.app.gazekeep.core.backend.TimeoutDeadline
import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.core.model.Readiness
import cn.rainafter.app.gazekeep.core.model.SetupStep
import cn.rainafter.app.gazekeep.xposed.protocol.BridgeProtocol
import cn.rainafter.app.gazekeep.xposed.protocol.IGazeKeepClient
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 以本地 Binder 模拟 system_server 侧桥接，按 BridgeProtocol 的真实 Parcel 布局应答。 */
private class FakePowerBridge(
  var protocol: Int = BridgeProtocol.PROTOCOL_VERSION,
  var mainHook: Int = BridgeProtocol.HOOK_READY,
  var indicatorHook: Int? = BridgeProtocol.HOOK_READY,
  var register: Int = BridgeProtocol.RESULT_OK,
  var extend: Int = BridgeProtocol.RESULT_OK,
  var failure: Throwable? = null,
) : Binder() {
  val codes = mutableListOf<Int>()
  val extendedSeqs = mutableListOf<Long>()
  var client: IGazeKeepClient? = null

  override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
    codes += code
    failure?.let { throw it }
    data.enforceInterface(BridgeProtocol.DESCRIPTOR)
    reply!!.writeNoException()
    when (code) {
      BridgeProtocol.TX_PING -> {
        reply.writeInt(protocol)
        reply.writeInt(mainHook)
        reply.writeString("candidate")
        indicatorHook?.let(reply::writeInt)
      }

      BridgeProtocol.TX_REGISTER -> {
        client = IGazeKeepClient.Stub.asInterface(data.readStrongBinder())
        reply.writeInt(register)
      }

      BridgeProtocol.TX_EXTEND -> {
        extendedSeqs += data.readLong()
        reply.writeInt(extend)
      }

      else -> reply.writeInt(BridgeProtocol.RESULT_OK)
    }
    return true
  }
}

@RunWith(RobolectricTestRunner::class)
class LsposedKeepAwakeBackendTest {
  private fun backend(power: IBinder?) =
    LsposedKeepAwakeBackend(8_000L, hideCameraIndicator = false, resolvePower = { power })

  @Test
  fun missingPowerServiceRequiresFramework() = runTest {
    val backend = backend(null)
    assertEquals(Readiness.NeedsSetup(SetupStep.InstallFramework), backend.checkReadiness())
    backend.attach()
    assertEquals(Readiness.NeedsSetup(SetupStep.InstallFramework), backend.readiness.value)
    assertIs<ExtendResult.Failed>(backend.extend(ExtendToken.Seq(1L)))
  }

  @Test
  fun protocolMismatchOrUnreadyHookNeverRegisters() = runTest {
    for (bridge in listOf(
      FakePowerBridge(protocol = BridgeProtocol.PROTOCOL_VERSION - 1),
      FakePowerBridge(mainHook = 0),
    )) {
      val backend = backend(bridge)
      assertEquals(Readiness.NeedsSetup(SetupStep.Reboot), backend.checkReadiness())
      backend.attach()
      assertEquals(Readiness.NeedsSetup(SetupStep.Reboot), backend.readiness.value)
      assertFalse(BridgeProtocol.TX_REGISTER in bridge.codes)
      assertIs<ExtendResult.Failed>(backend.extend(ExtendToken.Seq(1L)))
    }
  }

  @Test
  fun readinessProbeDoesNotTakeOverTheServiceCallback() {
    val bridge = FakePowerBridge()
    assertEquals(Readiness.Ready, backend(bridge).checkReadiness())
    assertEquals(listOf(BridgeProtocol.TX_PING), bridge.codes)
  }

  @Test
  fun rejectedRegistrationIsNotReady() = runTest {
    val backend = backend(FakePowerBridge(register = BridgeProtocol.RESULT_DENIED))
    backend.attach()
    assertEquals(Readiness.NeedsSetup(SetupStep.Reboot), backend.readiness.value)
  }

  @Test
  fun extendForwardsSeqAndMapsStaleAndRejectionWithoutDone() = runTest {
    val bridge = FakePowerBridge()
    val backend = backend(bridge)
    backend.attach()
    assertEquals(Readiness.Ready, backend.readiness.value)
    assertEquals(ExtendResult.Done, backend.extend(ExtendToken.Seq(42L)))
    bridge.extend = BridgeProtocol.RESULT_STALE
    assertEquals(ExtendResult.Stale, backend.extend(ExtendToken.Seq(41L)))
    for (result in listOf(BridgeProtocol.RESULT_DENIED, BridgeProtocol.RESULT_UNAVAILABLE)) {
      bridge.extend = result
      assertIs<ExtendResult.Failed>(backend.extend(ExtendToken.Seq(43L)))
    }
    assertEquals(listOf(42L, 41L, 43L, 43L), bridge.extendedSeqs)
    assertIs<ExtendResult.Failed>(backend.extend(ExtendToken.None))
    assertEquals(4, bridge.extendedSeqs.size)
  }

  @Test
  fun binderFailureFailsClosed() = runTest {
    val bridge = FakePowerBridge()
    val backend = backend(bridge)
    backend.attach()
    bridge.failure = RemoteException("system_server died")
    assertIs<ExtendResult.Failed>(backend.extend(ExtendToken.Seq(7L)))
    assertEquals(Readiness.NeedsSetup(SetupStep.Reboot), backend.checkReadiness())
    assertFalse(backend.syncHideCameraIndicator(enabled = false))
  }

  @Test
  fun detachUnregistersAndRejectsLaterExtend() = runTest {
    val bridge = FakePowerBridge()
    val backend = backend(bridge)
    backend.attach()
    backend.detach()
    assertTrue(BridgeProtocol.TX_UNREGISTER in bridge.codes)
    assertIs<ExtendResult.Failed>(backend.extend(ExtendToken.Seq(1L)))
  }

  @Test
  fun preTimeoutCallbackPublishesSeqDeadlineAndDropsInvalidOptionalTimes() = runTest {
    val bridge = FakePowerBridge()
    val backend = backend(bridge)
    val deadlines = mutableListOf<TimeoutDeadline>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { backend.deadlines.collect { deadlines += it } }
    backend.attach()
    val client = bridge.client!!
    client.onPreTimeout(9L, 60_000L, 48_000L, "com.example.reader", 1_000L)
    client.onPreTimeout(10L, 60_000L, 70_000L, null, -1L)
    assertEquals(2, deadlines.size)
    val (valid, invalid) = deadlines
    assertEquals(ExtendToken.Seq(9L), valid.token)
    assertEquals(48_000L, valid.screenDimAtUptime)
    assertEquals("com.example.reader", valid.foregroundPackage)
    assertEquals(1_000L, valid.lastExternalActivityUptime)
    assertEquals(BackendId.Lsposed, valid.backend)
    assertNull(invalid.screenDimAtUptime)
    assertNull(invalid.lastExternalActivityUptime)
  }

  @Test
  fun hidingIndicatorRequiresIndicatorHookButShowingDoesNot() {
    val bridge = FakePowerBridge(indicatorHook = null)
    val backend = backend(bridge)
    assertFalse(backend.checkIndicatorHookReadiness())
    assertFalse(backend.syncHideCameraIndicator(enabled = true))
    assertFalse(BridgeProtocol.TX_SET_HIDE_INDICATOR in bridge.codes)
    assertTrue(backend.syncHideCameraIndicator(enabled = false))
    assertTrue(BridgeProtocol.TX_SET_HIDE_INDICATOR in bridge.codes)
  }
}
