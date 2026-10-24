package cn.rainafter.app.gazekeep.backend.shizuku.client

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import cn.rainafter.app.gazekeep.backend.shizuku.ShellService
import cn.rainafter.app.gazekeep.backend.shizuku.protocol.IGazeKeepShell
import cn.rainafter.app.gazekeep.backend.shizuku.protocol.PowerSnapshot
import cn.rainafter.app.gazekeep.backend.shizuku.restore.PendingTimeoutRestoreStore
import cn.rainafter.app.gazekeep.backend.shizuku.restore.TimeoutRestoreCoordinator
import cn.rainafter.app.gazekeep.core.backend.ExtendResult
import cn.rainafter.app.gazekeep.core.backend.ExtendToken
import cn.rainafter.app.gazekeep.core.backend.KeepAwakeBackend
import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.core.model.Readiness
import cn.rainafter.app.gazekeep.core.model.SetupStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import rikka.shizuku.Shizuku

/** Shizuku 路径：优先在 shell UserService 内调用 IPowerManager.userActivity。 */
class ShizukuKeepAwakeBackend(
  private val context: Context,
  private val userServiceVersion: Int = IGazeKeepShell.Stub.PROTOCOL_VERSION,
  private val fallbackExtensionMs: Int = 60_000,
) : KeepAwakeBackend {
  override val id: BackendId = BackendId.Shizuku
  private val _readiness = MutableStateFlow<Readiness>(Readiness.NeedsSetup(SetupStep.StartShizuku))
  override val readiness: StateFlow<Readiness> = _readiness.asStateFlow()

  @Volatile private var shell: IGazeKeepShell? = null
  private var bound = false
  private var lastSequence = Long.MIN_VALUE

  @Volatile private var ownActivityUptime: Long? = null
  fun isExternalActivity(uptime: Long): Boolean = uptime != ownActivityUptime
  private val restoreStore = PendingTimeoutRestoreStore.get(context)
  private val operations = timeoutOperations

  // 恢复任务归后端所有，不随检测会话取消。
  private val recoveryScope = kotlinx.coroutines.CoroutineScope(
    kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
  )

  // 重连只在 UserService 掉线时调度，detach 只取消待执行的重绑而不废弃作用域。
  private val reconnector = UserServiceReconnector(
    scope = kotlinx.coroutines.CoroutineScope(
      kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main,
    ),
    binderAlive = Shizuku::pingBinder,
    bind = ::refresh,
  )

  private fun coordinator(remote: IGazeKeepShell) = TimeoutRestoreCoordinator(
    restoreStore::read,
    restoreStore::write,
    restoreStore::clear,
    remote::getScreenOffTimeout,
    remote::setScreenOffTimeout,
  )

  private val args = Shizuku.UserServiceArgs(ComponentName(context, ShellService::class.java))
    .daemon(false)
    .processNameSuffix("shell")
    .tag("gazekeep-shell")
    .version(userServiceVersion)

  private val connection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
      val remote = IGazeKeepShell.Stub.asInterface(service)
      if (remote == null ||
        runCatching { remote.version() }.getOrNull() != IGazeKeepShell.Stub.PROTOCOL_VERSION
      ) {
        shell = null
        _readiness.value = Readiness.Unavailable("shell protocol mismatch")
        return
      }
      shell = remote
      reconnector.onConnected()
      _readiness.value = Readiness.Unavailable("restoring original timeout")
      recoveryScope.launch {
        operations.withLock {
          val restored = runCatching { coordinator(remote).restore() }.isSuccess
          if (shell ===
            remote
          ) {
            _readiness.value =
              if (restored) Readiness.Ready else Readiness.Unavailable("timeout restore failed")
          }
        }
      }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
      shell = null
      bound = false
      _readiness.value = Readiness.NeedsSetup(SetupStep.StartShizuku)
      reconnector.onLost()
    }
  }

  private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }
  private val binderDead = Shizuku.OnBinderDeadListener {
    reconnector.cancel()
    shell = null
    bound = false
    _readiness.value = Readiness.NeedsSetup(SetupStep.StartShizuku)
  }

  override suspend fun attach() {
    Shizuku.addBinderReceivedListenerSticky(binderReceived)
    Shizuku.addBinderDeadListener(binderDead)
    refresh()
  }

  override suspend fun detach() {
    reconnector.cancel()
    runCatching {
      if (bound) Shizuku.unbindUserService(args, connection, true)
    }
    bound = false
    shell = null
    recoveryScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    Shizuku.removeBinderReceivedListener(binderReceived)
    Shizuku.removeBinderDeadListener(binderDead)
    _readiness.value = Readiness.NeedsSetup(SetupStep.StartShizuku)
  }

  override suspend fun extend(token: ExtendToken): ExtendResult =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
      operations.withLock {
        extendLocked(token)
      }
    }

  private fun extendLocked(token: ExtendToken): ExtendResult {
    if (_readiness.value != Readiness.Ready) return ExtendResult.Failed(IllegalStateException("shell not ready"))
    if (token is ExtendToken.Seq && token.value <= lastSequence) return ExtendResult.Stale
    val remote = shell ?: return ExtendResult.Failed(IllegalStateException("Shizuku shell unavailable"))
    if (token is ExtendToken.Seq) lastSequence = token.value
    return try {
      val activityAt = android.os.SystemClock.uptimeMillis()
      remote.userActivity(activityAt)
      ownActivityUptime = activityAt
      ExtendResult.Done
    } catch (error: Throwable) {
      if (error is SecurityException) {
        extendWithTimeout(remote)
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

  suspend fun foregroundPackage(): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    if (_readiness.value != Readiness.Ready) null else runCatching { shell?.foregroundPackage() }.getOrNull()
  }

  override suspend fun release() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
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

  private fun extendWithTimeout(remote: IGazeKeepShell): ExtendResult = runCatching {
    coordinator(remote).extend(fallbackExtensionMs)
    ExtendResult.Done
  }.getOrElse { fallbackError ->
    _readiness.value = Readiness.Unavailable("userActivity denied; timeout fallback failed")
    ExtendResult.Failed(fallbackError)
  }

  private fun refresh() {
    if (!Shizuku.pingBinder()) {
      _readiness.value = Readiness.NeedsSetup(SetupStep.StartShizuku)
      return
    }
    if (Shizuku.isPreV11() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
      _readiness.value = Readiness.NeedsSetup(SetupStep.GrantShizukuPermission)
      return
    }
    if (bound) return
    bound = true
    runCatching { Shizuku.bindUserService(args, connection) }
      .onFailure {
        bound = false
        _readiness.value = Readiness.Unavailable("bind user service failed")
      }
  }
  private companion object {
    val timeoutOperations = kotlinx.coroutines.sync.Mutex()
  }
}
