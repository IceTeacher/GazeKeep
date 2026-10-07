package cn.rainafter.app.gazekeep.backend.shizuku.client

import cn.rainafter.app.gazekeep.backend.shizuku.ShellService
import cn.rainafter.app.gazekeep.backend.shizuku.protocol.IGazeKeepShell
import cn.rainafter.app.gazekeep.backend.shizuku.protocol.PowerSnapshot
import cn.rainafter.app.gazekeep.backend.shizuku.restore.PendingTimeoutRestoreStore
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import cn.rainafter.app.gazekeep.core.backend.ExtendResult
import cn.rainafter.app.gazekeep.core.backend.ExtendToken
import cn.rainafter.app.gazekeep.core.backend.KeepAwakeBackend
import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.core.model.Readiness
import cn.rainafter.app.gazekeep.core.model.SetupStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

/** Shizuku 路径：优先在 shell UserService 内调用 IPowerManager.userActivity。 */
class ShizukuKeepAwakeBackend(
    private val context: Context,
    private val userServiceVersion: Int = 1,
    private val fallbackExtensionMs: Int = 60_000,
) : KeepAwakeBackend {
    override val id: BackendId = BackendId.Shizuku
    private val _readiness = MutableStateFlow<Readiness>(Readiness.NeedsSetup(SetupStep.StartShizuku))
    override val readiness: StateFlow<Readiness> = _readiness.asStateFlow()

    private var shell: IGazeKeepShell? = null
    private var bound = false
    private var lastSequence = Long.MIN_VALUE
    private val restoreStore = PendingTimeoutRestoreStore(context)

    private val args = Shizuku.UserServiceArgs(ComponentName(context, ShellService::class.java))
        .daemon(false)
        .processNameSuffix("shell")
        .tag("gazekeep-shell")
        .version(userServiceVersion)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val remote = IGazeKeepShell.Stub.asInterface(service)
            if (remote == null || runCatching { remote.version() }.getOrNull() != IGazeKeepShell.Stub.PROTOCOL_VERSION) {
                shell = null
                _readiness.value = Readiness.Unavailable("shell protocol mismatch")
                return
            }
            shell = remote
            _readiness.value = Readiness.Ready
            restorePending(remote)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            shell = null
            bound = false
            _readiness.value = Readiness.NeedsSetup(SetupStep.StartShizuku)
        }
    }

    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDead = Shizuku.OnBinderDeadListener {
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
        runCatching {
            if (bound) Shizuku.unbindUserService(args, connection, true)
        }
        bound = false
        shell = null
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeBinderDeadListener(binderDead)
        _readiness.value = Readiness.NeedsSetup(SetupStep.StartShizuku)
    }

    override suspend fun extend(token: ExtendToken): ExtendResult {
        if (token is ExtendToken.Seq && token.value <= lastSequence) return ExtendResult.Stale
        val remote = shell ?: return ExtendResult.Failed(IllegalStateException("Shizuku shell unavailable"))
        if (token is ExtendToken.Seq) lastSequence = token.value
        return try {
            remote.userActivity(android.os.SystemClock.uptimeMillis())
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

    override suspend fun release() {
        val remote = shell ?: return
        val original = restoreStore.read() ?: return
        runCatching {
            remote.setScreenOffTimeout(original)
            restoreStore.clear()
        }
    }

    private fun extendWithTimeout(remote: IGazeKeepShell): ExtendResult {
        return runCatching {
            val original = restoreStore.read() ?: remote.getScreenOffTimeout().also(restoreStore::write)
            remote.setScreenOffTimeout((original + fallbackExtensionMs).coerceAtMost(Int.MAX_VALUE))
            _readiness.value = Readiness.Ready
            ExtendResult.Done
        }.getOrElse { fallbackError ->
            _readiness.value = Readiness.Unavailable("userActivity denied; timeout fallback failed")
            ExtendResult.Failed(fallbackError)
        }
    }

    private fun restorePending(remote: IGazeKeepShell) {
        val original = restoreStore.read() ?: return
        runCatching {
            remote.setScreenOffTimeout(original)
            restoreStore.clear()
        }
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
}
