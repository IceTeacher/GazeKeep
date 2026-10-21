package cn.rainafter.app.gazekeep.backend.lsposed

import android.os.IBinder
import android.os.Parcel
import cn.rainafter.app.gazekeep.core.backend.ExtendResult
import cn.rainafter.app.gazekeep.core.backend.ExtendToken
import cn.rainafter.app.gazekeep.core.backend.KeepAwakeBackend
import cn.rainafter.app.gazekeep.core.backend.TimeoutDeadline
import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.core.model.Readiness
import cn.rainafter.app.gazekeep.core.model.SetupStep
import cn.rainafter.app.gazekeep.xposed.protocol.BridgeProtocol
import cn.rainafter.app.gazekeep.xposed.protocol.IGazeKeepClient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class LsposedKeepAwakeBackend internal constructor(
  private val leadMillis: Long,
  private val hideCameraIndicator: Boolean,
  // 主机测试注入本地 Binder，生产路径始终经 ServiceManager 解析 power 服务。
  private val resolvePower: () -> IBinder?,
) : KeepAwakeBackend {
  constructor(leadMillis: Long = 8_000L, hideCameraIndicator: Boolean = false) :
    this(leadMillis, hideCameraIndicator, SystemPowerBinderResolver()::resolve)
  private val _readiness = MutableStateFlow<Readiness>(Readiness.NeedsSetup(SetupStep.InstallFramework))
  private val _deadlines = MutableSharedFlow<TimeoutDeadline>(extraBufferCapacity = 8)
  private var binder: IBinder? = null
  private var callback: Callback? = null

  override val id: BackendId = BackendId.Lsposed
  override val readiness: StateFlow<Readiness> = _readiness.asStateFlow()
  val deadlines: SharedFlow<TimeoutDeadline> = _deadlines.asSharedFlow()

  /** 设置页只探测桥接状态，不能抢占运行服务的单客户端回调。 */
  fun checkReadiness(): Readiness {
    val power = resolvePower()
    val readiness = when {
      power == null -> Readiness.NeedsSetup(SetupStep.InstallFramework)
      ping(power)?.mainReady != true -> Readiness.NeedsSetup(SetupStep.Reboot)
      else -> Readiness.Ready
    }
    _readiness.value = readiness
    return readiness
  }

  fun checkIndicatorHookReadiness(): Boolean {
    val power = resolvePower() ?: return false
    return ping(power)?.indicatorReady == true
  }

  /** 将实验性隐藏相机隐私指示器开关状态实时同步至 system_server。 */
  fun syncHideCameraIndicator(enabled: Boolean): Boolean {
    val power = binder ?: resolvePower() ?: return false
    val status = ping(power) ?: return false
    if (!status.mainReady || (enabled && !status.indicatorReady)) return false
    return transact(power, BridgeProtocol.TX_SET_HIDE_INDICATOR) { data ->
      data.writeInt(if (enabled) 1 else 0)
    } == BridgeProtocol.RESULT_OK
  }

  override suspend fun attach() {
    val power = resolvePower()
    if (power == null) {
      _readiness.value = Readiness.NeedsSetup(SetupStep.InstallFramework)
      return
    }
    binder = power
    val client = Callback { deadline -> _deadlines.tryEmit(deadline) }
    callback = client
    if (ping(power)?.mainReady != true) {
      binder = null
      callback = null
      _readiness.value = Readiness.NeedsSetup(SetupStep.Reboot)
      return
    }
    val result = transact(power, BridgeProtocol.TX_REGISTER) { data ->
      data.writeStrongBinder(client.asBinder())
      data.writeLong(leadMillis)
      data.writeInt(if (hideCameraIndicator) 1 else 0)
    }
    _readiness.value =
      if (result == BridgeProtocol.RESULT_OK) Readiness.Ready else Readiness.NeedsSetup(SetupStep.Reboot)
  }

  override suspend fun detach() = detachImmediately()

  /** Service 生命周期结束时不再启动会被立即取消的协程。 */
  fun detachImmediately() {
    val power = binder ?: return
    transact(power, BridgeProtocol.TX_UNREGISTER)
    binder = null
    callback = null
    _readiness.value = Readiness.NeedsSetup(SetupStep.InstallFramework)
  }

  override suspend fun extend(token: ExtendToken): ExtendResult {
    val seq =
      token as? ExtendToken.Seq ?: return ExtendResult.Failed(IllegalArgumentException("LSPosed requires seq"))
    val power = binder ?: return ExtendResult.Failed(IllegalStateException("bridge is not attached"))
    val result = transact(power, BridgeProtocol.TX_EXTEND) { it.writeLong(seq.value) }
    return when (result) {
      BridgeProtocol.RESULT_OK -> ExtendResult.Done
      BridgeProtocol.RESULT_STALE -> ExtendResult.Stale
      else -> ExtendResult.Failed(IllegalStateException("LSPosed bridge rejected extend"))
    }
  }

  override suspend fun release() = Unit

  private fun transact(binder: IBinder, code: Int, write: ((Parcel) -> Unit)? = null): Int {
    val data = Parcel.obtain()
    val reply = Parcel.obtain()
    return try {
      data.writeInterfaceToken(BridgeProtocol.DESCRIPTOR)
      write?.invoke(data)
      if (!binder.transact(code, data, reply, 0)) return BridgeProtocol.RESULT_UNAVAILABLE
      reply.readException()
      reply.readInt()
    } catch (_: Throwable) {
      BridgeProtocol.RESULT_UNAVAILABLE
    } finally {
      data.recycle()
      reply.recycle()
    }
  }

  private data class PingStatus(val mainReady: Boolean, val indicatorReady: Boolean)

  private fun ping(power: IBinder): PingStatus? {
    val data = Parcel.obtain()
    val reply = Parcel.obtain()
    return try {
      data.writeInterfaceToken(BridgeProtocol.DESCRIPTOR)
      if (!power.transact(BridgeProtocol.TX_PING, data, reply, 0)) return null
      reply.readException()
      val protocol = reply.readInt()
      val mainReady = reply.readInt() == BridgeProtocol.HOOK_READY
      reply.readString()
      val indicatorReady = reply.dataAvail() >= Int.SIZE_BYTES &&
        reply.readInt() == BridgeProtocol.HOOK_READY
      if (protocol != BridgeProtocol.PROTOCOL_VERSION) null else PingStatus(mainReady, indicatorReady)
    } catch (_: Throwable) {
      null
    } finally {
      data.recycle()
      reply.recycle()
    }
  }

  private class Callback(private val onDeadline: (TimeoutDeadline) -> Unit) : IGazeKeepClient.Stub() {
    override fun onPreTimeout(
      seq: Long,
      screenOffAtUptime: Long,
      screenDimAtUptime: Long,
      topPackage: String?,
      lastExternalActivityUptime: Long,
    ) {
      onDeadline(
        TimeoutDeadline(
          screenOffAtUptime = screenOffAtUptime,
          screenDimAtUptime = screenDimAtUptime.takeIf { it > 0L && it <= screenOffAtUptime },
          token = ExtendToken.Seq(seq),
          foregroundPackage = topPackage,
          lastExternalActivityUptime = lastExternalActivityUptime.takeIf { it >= 0L },
          backend = BackendId.Lsposed,
        ),
      )
    }

    override fun onHookStatus(status: Int) = Unit
  }
}
