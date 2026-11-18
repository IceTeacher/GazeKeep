package cn.rainafter.app.gazekeep.backend.shizuku.client

import cn.rainafter.app.gazekeep.backend.shizuku.protocol.IGazeKeepShell
import cn.rainafter.app.gazekeep.backend.shizuku.protocol.PowerSnapshot
import cn.rainafter.app.gazekeep.backend.shizuku.restore.PendingRestore
import cn.rainafter.app.gazekeep.core.model.Readiness
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ShizukuKeepAwakeBackendLifecycleTest {
  private class FakeShell(
    private val protocolVersion: Int = IGazeKeepShell.Stub.PROTOCOL_VERSION,
    private val onGetTimeout: () -> Int = { 30_000 },
    private val onSetTimeout: (Int) -> Unit = {},
  ) : IGazeKeepShell {
    override fun asBinder() = error("unused in jvm test")
    override fun foregroundPackage(): String? = null
    override fun version(): Int = protocolVersion
    override fun userActivity(uptime: Long) = Unit
    override fun readPower(): PowerSnapshot = PowerSnapshot(null, null, null, false, true, null)
    override fun getScreenOffTimeout(): Int = onGetTimeout()
    override fun setScreenOffTimeout(millis: Int) = onSetTimeout(millis)
  }

  private class RecordingBridge : ShizukuBridge {
    private data class Binding(val onConnected: (IGazeKeepShell?) -> Unit, val onDisconnected: () -> Unit)

    private class FakeHandle : ShizukuBridge.ListenerHandle

    var binderAlive = true
    var granted = true
    val events = mutableListOf<String>()
    private val bindings = mutableMapOf<String, Binding>()
    private val activeListeners = mutableListOf<() -> Unit>()

    override fun pingBinder(): Boolean = binderAlive
    override fun isPreV11(): Boolean = false
    override fun hasPermission(): Boolean = granted

    override fun addListeners(onBinderReceived: () -> Unit, onBinderDead: () -> Unit): ShizukuBridge.ListenerHandle {
      activeListeners.add(onBinderReceived)
      if (binderAlive) onBinderReceived()
      return FakeHandle()
    }

    override fun removeListeners(handle: ShizukuBridge.ListenerHandle) = Unit

    override fun bindUserService(tag: String, onConnected: (IGazeKeepShell?) -> Unit, onDisconnected: () -> Unit) {
      events += "bind:$tag"
      bindings[tag] = Binding(onConnected, onDisconnected)
    }

    override fun unbindUserService(tag: String, remove: Boolean) {
      events += "unbind:$tag:$remove"
      if (remove) bindings.remove(tag)
    }

    fun deliverConnected(tag: String, shell: IGazeKeepShell? = FakeShell()) {
      bindings[tag]?.onConnected?.invoke(shell)
    }

    fun boundTags(): Set<String> = bindings.keys.toSet()
  }

  private fun backend(
    testScope: TestScope,
    bridge: RecordingBridge,
    lifecycle: ShizukuLifecycleCoordinator,
    readRestore: () -> PendingRestore? = { null },
    writeRestore: (PendingRestore) -> Boolean = { true },
    clearRestore: () -> Boolean = { true },
    bindTimeoutMs: Long = 5_000L,
  ): ShizukuKeepAwakeBackend {
    val dispatcher = StandardTestDispatcher(testScope.testScheduler)
    return ShizukuKeepAwakeBackend(
      userServiceVersion = IGazeKeepShell.Stub.PROTOCOL_VERSION,
      fallbackExtensionMs = 60_000,
      bridge = bridge,
      readRestore = readRestore,
      writeRestore = writeRestore,
      clearRestore = clearRestore,
      lifecycle = lifecycle,
      reconnectorDispatcher = dispatcher,
      recoveryDispatcher = dispatcher,
      uptimeClock = { 1_000L },
      bindTimeoutMs = bindTimeoutMs,
    )
  }

  @Test
  fun newAttachWaitsForOldBackgroundDetachBeforeBinding() = runTest {
    val bridge = RecordingBridge()
    val lifecycle = ShizukuLifecycleCoordinator()
    val restoreGate = CompletableDeferred<Unit>()
    var pendingRestore: PendingRestore? = PendingRestore(30_000, 60_000)

    val oldBackend = backend(
      this,
      bridge,
      lifecycle,
      readRestore = { pendingRestore },
      clearRestore = {
        pendingRestore = null
        true
      },
    )
    oldBackend.attach()
    runCurrent()
    val oldTag = bridge.boundTags().single()
    pendingRestore = null
    bridge.deliverConnected(oldTag, FakeShell())
    runCurrent()
    assertEquals(Readiness.Ready, oldBackend.readiness.value)

    // 注册延迟清理任务模拟 oldSession.close() 的异步 detach。
    val cleanupJob = launch {
      restoreGate.await()
      oldBackend.detach()
    }
    lifecycle.trackCleanup(cleanupJob)

    val newBackend = backend(this, bridge, lifecycle)
    val attachJob = launch { newBackend.attach() }
    runCurrent()

    // 旧会话仍在等待恢复/解绑时，新会话不得提前调用 bindUserService。
    assertEquals(listOf("bind:$oldTag"), bridge.events)
    assertTrue(newBackend.readiness.value !is Readiness.Ready)

    restoreGate.complete(Unit)
    cleanupJob.join()
    attachJob.join()
    runCurrent()

    val newTag = bridge.boundTags().single()
    assertTrue(oldTag != newTag)
    assertEquals(
      listOf(
        "bind:$oldTag",
        "unbind:$oldTag:false",
        "unbind:$oldTag:true",
        "bind:$newTag",
      ),
      bridge.events,
    )

    bridge.deliverConnected(newTag, FakeShell())
    runCurrent()
    assertEquals(Readiness.Ready, newBackend.readiness.value)
    newBackend.detach()
  }

  @Test
  fun bindTimeoutClearsBoundFlagAndTriggersReconnector() = runTest {
    val bridge = RecordingBridge()
    val lifecycle = ShizukuLifecycleCoordinator()
    val shizuku = backend(this, bridge, lifecycle, bindTimeoutMs = 5_000L)

    shizuku.attach()
    runCurrent()
    val tag = bridge.boundTags().single()
    assertTrue(shizuku.hasPendingBind())
    assertEquals(listOf("bind:$tag"), bridge.events)

    // 5 秒内一直没收到 onServiceConnected，应判定超时解绑并触发重连。
    advanceTimeBy(5_000L)
    runCurrent()
    assertFalse(shizuku.hasPendingBind())
    assertIs<Readiness.Unavailable>(shizuku.readiness.value)
    assertEquals(listOf("bind:$tag", "unbind:$tag:false", "unbind:$tag:true"), bridge.events)

    // UserServiceReconnector 首次退避 1 秒后自动重新绑定。
    advanceTimeBy(1_000L)
    runCurrent()
    assertEquals(
      listOf("bind:$tag", "unbind:$tag:false", "unbind:$tag:true", "bind:$tag"),
      bridge.events,
    )
    assertTrue(shizuku.hasPendingBind())

    bridge.deliverConnected(tag, FakeShell())
    runCurrent()
    assertEquals(Readiness.Ready, shizuku.readiness.value)
    assertFalse(shizuku.hasPendingBind())
    shizuku.detach()
  }

  @Test
  fun detachWhileBindPendingCancelsWatchdogWithoutSpuriousRetry() = runTest {
    val bridge = RecordingBridge()
    val lifecycle = ShizukuLifecycleCoordinator()
    val shizuku = backend(this, bridge, lifecycle, bindTimeoutMs = 5_000L)

    shizuku.attach()
    runCurrent()
    val tag = bridge.boundTags().single()
    assertTrue(shizuku.hasPendingBind())

    shizuku.detach()
    runCurrent()
    assertFalse(shizuku.hasPendingBind())

    advanceTimeBy(30_000L)
    runCurrent()
    assertEquals(listOf("bind:$tag", "unbind:$tag:false", "unbind:$tag:true"), bridge.events)
  }
}
