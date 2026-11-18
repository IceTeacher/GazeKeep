package cn.rainafter.app.gazekeep.backend.shizuku.client

import android.content.Context
import cn.rainafter.app.gazekeep.backend.shizuku.protocol.IGazeKeepShell
import cn.rainafter.app.gazekeep.backend.shizuku.protocol.PowerSnapshot
import cn.rainafter.app.gazekeep.backend.shizuku.restore.PendingRestore
import cn.rainafter.app.gazekeep.backend.shizuku.restore.PendingTimeoutRestoreStore
import cn.rainafter.app.gazekeep.backend.shizuku.restore.TimeoutRestoreCoordinator
import cn.rainafter.app.gazekeep.backend.shizuku.restore.TimeoutRestorePolicy
import cn.rainafter.app.gazekeep.core.backend.ExtendResult
import cn.rainafter.app.gazekeep.core.backend.ExtendToken
import cn.rainafter.app.gazekeep.core.backend.KeepAwakeBackend
import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.core.model.Readiness
import cn.rainafter.app.gazekeep.core.model.SetupStep
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Shizuku 路径：优先在 shell UserService 内调用 IPowerManager.userActivity。 */
class ShizukuKeepAwakeBackend internal constructor(
  private val userServiceVersion: Int,
  private val fallbackExtensionMs: Int,
  private val bridge: ShizukuBridge,
  private val readRestore: () -> PendingRestore?,
  private val writeRestore: (PendingRestore) -> Boolean,
  private val clearRestore: () -> Boolean,
  private val lifecycle: ShizukuLifecycleCoordinator,
  private val reconnectorDispatcher: CoroutineDispatcher = Dispatchers.Main,
  private val recoveryDispatcher: CoroutineDispatcher = Dispatchers.IO,
  private val uptimeClock: () -> Long = android.os.SystemClock::uptimeMillis,
  private val bindTimeoutMs: Long = BIND_TIMEOUT_MS,
) : KeepAwakeBackend {
  constructor(
    context: Context,
    userServiceVersion: Int = IGazeKeepShell.Stub.PROTOCOL_VERSION,
    fallbackExtensionMs: Int = 60_000,
  ) : this(
    userServiceVersion = userServiceVersion,
    fallbackExtensionMs = fallbackExtensionMs,
    bridge = AndroidShizukuBridge(context.applicationContext, userServiceVersion),
    readRestore = PendingTimeoutRestoreStore.get(context)::read,
    writeRestore = PendingTimeoutRestoreStore.get(context)::write,
    clearRestore = PendingTimeoutRestoreStore.get(context)::clear,
    lifecycle = sharedLifecycle,
  )

  override val id: BackendId = BackendId.Shizuku
  private val _readiness = MutableStateFlow<Readiness>(Readiness.NeedsSetup(SetupStep.StartShizuku))
  override val readiness: StateFlow<Readiness> = _readiness.asStateFlow()

  private val serviceTag = lifecycle.nextTag()
  private val stateLock = Any()

  @Volatile private var shell: IGazeKeepShell? = null
  private var bound = false
  private var attached = false
  private var detaching = false
  private var restoring = false
  private var listenerHandle: ShizukuBridge.ListenerHandle? = null
  private var bindWatchdogJob: Job? = null
  private var lastSequence = Long.MIN_VALUE

  @Volatile private var ownActivityUptime: Long? = null

  // 退路不产生用户活动；延长前已发生的活动已计入延长后的超时，不能再当作新的外部活动。
  @Volatile private var fallbackExtendedUptime: Long = Long.MIN_VALUE
  fun isExternalActivity(uptime: Long): Boolean = uptime != ownActivityUptime && uptime > fallbackExtendedUptime
  private val operations = timeoutOperations

  // 恢复任务归后端所有，不随检测会话取消。
  private val recoveryScope = CoroutineScope(SupervisorJob() + recoveryDispatcher)
  private val reconnectorScope = CoroutineScope(SupervisorJob() + reconnectorDispatcher)

  // 重连只在 UserService 掉线或绑定超时时调度，detach 只取消待执行的重绑而不废弃作用域。
  private val reconnector = UserServiceReconnector(
    scope = reconnectorScope,
    binderAlive = bridge::pingBinder,
    bind = ::refresh,
  )

  fun hasPendingBind(): Boolean = synchronized(stateLock) {
    attached && !detaching && ((bound && shell == null) || restoring)
  }

  private fun coordinator(remote: IGazeKeepShell) = TimeoutRestoreCoordinator(
    readRestore,
    writeRestore,
    clearRestore,
    remote::getScreenOffTimeout,
    remote::setScreenOffTimeout,
  )

  private fun handleConnected(remote: IGazeKeepShell?) {
    cancelBindWatchdog()
    if (synchronized(stateLock) { detaching || !attached }) return
    if (remote == null || runCatching { remote.version() }.getOrNull() != userServiceVersion) {
      shell = null
      _readiness.value = Readiness.Unavailable("shell protocol mismatch")
      return
    }
    synchronized(stateLock) { restoring = true }
    shell = remote
    reconnector.onConnected()
    _readiness.value = Readiness.Unavailable("restoring original timeout")
    recoveryScope.launch {
      try {
        operations.withLock {
          val restored = runCatching { coordinator(remote).restore(deferWhileWatched(remote)) }.isSuccess
          synchronized(stateLock) { restoring = false }
          if (shell === remote && !synchronized(stateLock) { detaching }) {
            _readiness.value =
              if (restored) Readiness.Ready else Readiness.Unavailable("timeout restore failed")
          }
        }
      } finally {
        synchronized(stateLock) { restoring = false }
      }
    }
  }

  private fun handleDisconnected() {
    cancelBindWatchdog()
    synchronized(stateLock) {
      if (detaching) return
      shell = null
      bound = false
      restoring = false
    }
    _readiness.value = Readiness.NeedsSetup(SetupStep.StartShizuku)
    reconnector.onLost()
  }

  private fun handleBinderReceived() {
    if (!refresh()) reconnector.onLost()
  }

  private fun handleBinderDead() {
    cancelBindWatchdog()
    reconnector.cancel()
    synchronized(stateLock) {
      shell = null
      bound = false
      restoring = false
    }
    _readiness.value = Readiness.NeedsSetup(SetupStep.StartShizuku)
  }

  override suspend fun attach() {
    synchronized(stateLock) {
      if (detaching) return
      attached = true
    }
    // 等前代会话完成解绑后再注册 sticky 监听并绑定，避免旧 removeUserService 误杀正在启动的新 UserService。
    lifecycle.awaitPendingCleanup()
    lifecycle.mutex.withLock {
      if (synchronized(stateLock) { detaching || !attached }) return
      val handle = bridge.addListeners(::handleBinderReceived, ::handleBinderDead)
      val keepHandle = synchronized(stateLock) {
        if (detaching || !attached) {
          false
        } else {
          listenerHandle = handle
          true
        }
      }
      if (!keepHandle) {
        runCatching { bridge.removeListeners(handle) }
        return
      }
      refresh()
    }
  }

  /** 在独立清理作用域中释放超时并解绑 UserService，供下一任 Shizuku 会话等待清理完成。 */
  fun detachInBackground(): Job {
    val job = CoroutineScope(SupervisorJob() + recoveryDispatcher).launch {
      try {
        release()
      } finally {
        runCatching { detach() }
      }
    }
    lifecycle.trackCleanup(job)
    return job
  }

  override suspend fun detach() {
    val handleToRemove = synchronized(stateLock) {
      detaching = true
      attached = false
      val handle = listenerHandle
      listenerHandle = null
      handle
    }
    cancelBindWatchdog()
    reconnector.cancel()
    handleToRemove?.let { runCatching { bridge.removeListeners(it) } }
    lifecycle.mutex.withLock {
      // 停止后不再有熄屏释放，推迟中的恢复必须在断开前完成。
      withContext(recoveryDispatcher) {
        operations.withLock { shell?.let { remote -> runCatching { coordinator(remote).restore() } } }
      }
      val wasBound = synchronized(stateLock) {
        restoring = false
        bound.also { bound = false }
      }
      if (wasBound) {
        // 先 remove=false 清理 ShizukuServiceConnections 客户端缓存，再 remove=true 停止远端 UserService 进程。
        runCatching { bridge.unbindUserService(serviceTag, remove = false) }
        runCatching { bridge.unbindUserService(serviceTag, remove = true) }
      }
      shell = null
      recoveryScope.cancel()
      reconnectorScope.cancel()
      _readiness.value = Readiness.NeedsSetup(SetupStep.StartShizuku)
    }
  }

  override suspend fun extend(token: ExtendToken): ExtendResult = withContext(recoveryDispatcher) {
    operations.withLock {
      extendLocked(token)
    }
  }

  private fun extendLocked(token: ExtendToken): ExtendResult {
    if (_readiness.value != Readiness.Ready) return ExtendResult.Failed(IllegalStateException("shell not ready"))
    if (token is ExtendToken.Seq &&
      ExtendSequence.isStale(token.value, lastSequence, timeoutFallbackActive = readRestore() != null)
    ) {
      return ExtendResult.Stale
    }
    val remote = shell ?: return ExtendResult.Failed(IllegalStateException("Shizuku shell unavailable"))
    if (token is ExtendToken.Seq) lastSequence = token.value
    return try {
      val activityAt = uptimeClock()
      remote.userActivity(activityAt)
      ownActivityUptime = activityAt
      ExtendResult.Done
    } catch (error: Throwable) {
      if (error is SecurityException) {
        extendWithTimeout(remote, token)
      } else {
        _readiness.value = Readiness.NeedsSetup(SetupStep.StartShizuku)
        ExtendResult.Failed(error)
      }
    }
  }

  /** 读取 shell 侧解析后的电源状态，不把原始 dumpsys 文本带回 App。 */
  suspend fun readPower(): PowerSnapshot? = shell?.let { remote ->
    runCatching { remote.readPower() }.getOrNull()
  }

  suspend fun foregroundPackage(): String? = withContext(recoveryDispatcher) {
    if (_readiness.value != Readiness.Ready) null else runCatching { shell?.foregroundPackage() }.getOrNull()
  }

  override suspend fun release() = withContext(recoveryDispatcher) {
    operations.withLock {
      val remote = shell ?: return@withLock
      try {
        coordinator(remote).restore(deferWhileWatched(remote))
      } catch (error: Exception) {
        _readiness.value = Readiness.Unavailable("timeout restore failed")
        throw error
      }
    }
  }

  /** 推迟条件在恢复时即时读取电源状态；读取失败按不推迟处理。 */
  private fun deferWhileWatched(remote: IGazeKeepShell): (Int) -> Boolean = { original ->
    TimeoutRestorePolicy.wouldExpireImmediately(
      runCatching { remote.readPower() }.getOrNull(),
      uptimeClock(),
      original,
    )
  }

  /** 放弃保亮或被切走时不推迟：临时延长不能留给之后不再经过本后端的熄屏释放。 */
  override suspend fun forceRelease() = withContext(recoveryDispatcher) {
    operations.withLock {
      val remote = shell ?: return@withLock
      try {
        coordinator(remote).restore()
      } catch (error: Exception) {
        _readiness.value = Readiness.Unavailable("timeout restore failed")
        throw error
      }
    }
  }

  private fun extendWithTimeout(remote: IGazeKeepShell, token: ExtendToken): ExtendResult = runCatching {
    val now = uptimeClock()
    // PowerDumpOracle 的序号即系统上次用户活动时间。
    val elapsed = (token as? ExtendToken.Seq)?.let { now - it.value } ?: 0L
    coordinator(remote).extend(fallbackExtensionMs, elapsed)
    fallbackExtendedUptime = now
    ExtendResult.Done
  }.getOrElse { fallbackError ->
    _readiness.value = Readiness.Unavailable("userActivity denied; timeout fallback failed")
    ExtendResult.Failed(fallbackError)
  }

  /** 仅在调用 bindUserService 失败时返回 false；未就绪或已绑定都无需重试绑定。 */
  private fun refresh(): Boolean {
    synchronized(stateLock) {
      if (detaching || !attached) return true
    }
    if (!bridge.pingBinder()) {
      cancelBindWatchdog()
      synchronized(stateLock) { bound = false }
      _readiness.value = Readiness.NeedsSetup(SetupStep.StartShizuku)
      return true
    }
    if (bridge.isPreV11() || !bridge.hasPermission()) {
      cancelBindWatchdog()
      synchronized(stateLock) { bound = false }
      _readiness.value = Readiness.NeedsSetup(SetupStep.GrantShizukuPermission)
      return true
    }
    synchronized(stateLock) {
      if (detaching || !attached) return true
      if (bound) return true
      bound = true
    }
    return runCatching { bridge.bindUserService(serviceTag, ::handleConnected, ::handleDisconnected) }
      .onSuccess { scheduleBindWatchdog() }
      .onFailure {
        cancelBindWatchdog()
        synchronized(stateLock) { bound = false }
        _readiness.value = Readiness.Unavailable("bind user service failed")
      }.isSuccess
  }

  private fun scheduleBindWatchdog() {
    val next = reconnectorScope.launch {
      delay(bindTimeoutMs)
      val timedOut = synchronized(stateLock) {
        if (detaching || !attached || !bound || shell != null) {
          false
        } else {
          bound = false
          true
        }
      }
      if (!timedOut) return@launch
      runCatching { bridge.unbindUserService(serviceTag, remove = false) }
      runCatching { bridge.unbindUserService(serviceTag, remove = true) }
      _readiness.value = Readiness.Unavailable("bind user service timed out")
      reconnector.onLost()
    }
    synchronized(stateLock) {
      bindWatchdogJob?.cancel()
      bindWatchdogJob = next
    }
  }

  private fun cancelBindWatchdog() {
    synchronized(stateLock) {
      bindWatchdogJob?.cancel()
      bindWatchdogJob = null
    }
  }

  private companion object {
    val timeoutOperations = Mutex()
    val sharedLifecycle = ShizukuLifecycleCoordinator()
    const val BIND_TIMEOUT_MS = 5_000L
  }
}
