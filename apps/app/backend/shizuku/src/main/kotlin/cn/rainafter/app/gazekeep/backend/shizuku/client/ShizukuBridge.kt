package cn.rainafter.app.gazekeep.backend.shizuku.client

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import cn.rainafter.app.gazekeep.backend.shizuku.ShellService
import cn.rainafter.app.gazekeep.backend.shizuku.protocol.IGazeKeepShell
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** 封装 Shizuku 进程级 Binder 与 UserService 绑定能力，隔离静态 API 以便测试与生命周期串行化。 */
internal interface ShizukuBridge {
  interface ListenerHandle

  fun pingBinder(): Boolean
  fun isPreV11(): Boolean
  fun hasPermission(): Boolean
  fun addListeners(onBinderReceived: () -> Unit, onBinderDead: () -> Unit): ListenerHandle
  fun removeListeners(handle: ListenerHandle)
  fun bindUserService(tag: String, onConnected: (IGazeKeepShell?) -> Unit, onDisconnected: () -> Unit)
  fun unbindUserService(tag: String, remove: Boolean)
}

/** 串行化进程内前后两代 Shizuku 后端的解绑与重绑，防止旧会话异步 removeUserService 误杀新服务。 */
internal class ShizukuLifecycleCoordinator {
  val mutex = Mutex()
  private val nextId = AtomicLong(0L)

  @Volatile private var pendingCleanup: Job? = null

  fun nextTag(): String = "gazekeep-shell-${nextId.incrementAndGet()}"

  fun trackCleanup(job: Job) {
    pendingCleanup = job
    job.invokeOnCompletion {
      if (pendingCleanup === job) {
        pendingCleanup = null
      }
    }
  }

  suspend fun awaitPendingCleanup(timeoutMs: Long = CLEANUP_WAIT_TIMEOUT_MS) {
    val job = pendingCleanup ?: return
    withTimeoutOrNull(timeoutMs) { job.join() }
  }

  private companion object {
    const val CLEANUP_WAIT_TIMEOUT_MS = 3_000L
  }
}

internal class AndroidShizukuBridge(private val context: Context, private val userServiceVersion: Int) :
  ShizukuBridge {
  private data class Handle(val received: Shizuku.OnBinderReceivedListener, val dead: Shizuku.OnBinderDeadListener) :
    ShizukuBridge.ListenerHandle

  private val connections = ConcurrentHashMap<String, ServiceConnection>()

  override fun pingBinder(): Boolean = Shizuku.pingBinder()

  override fun isPreV11(): Boolean = Shizuku.isPreV11()

  override fun hasPermission(): Boolean = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

  override fun addListeners(onBinderReceived: () -> Unit, onBinderDead: () -> Unit): ShizukuBridge.ListenerHandle {
    val received = Shizuku.OnBinderReceivedListener { onBinderReceived() }
    val dead = Shizuku.OnBinderDeadListener { onBinderDead() }
    Shizuku.addBinderReceivedListenerSticky(received)
    Shizuku.addBinderDeadListener(dead)
    return Handle(received, dead)
  }

  override fun removeListeners(handle: ShizukuBridge.ListenerHandle) {
    val typed = handle as? Handle ?: return
    Shizuku.removeBinderReceivedListener(typed.received)
    Shizuku.removeBinderDeadListener(typed.dead)
  }

  override fun bindUserService(tag: String, onConnected: (IGazeKeepShell?) -> Unit, onDisconnected: () -> Unit) {
    val connection = object : ServiceConnection {
      override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        onConnected(IGazeKeepShell.Stub.asInterface(service))
      }

      override fun onServiceDisconnected(name: ComponentName?) {
        onDisconnected()
      }
    }
    connections[tag] = connection
    Shizuku.bindUserService(args(tag), connection)
  }

  override fun unbindUserService(tag: String, remove: Boolean) {
    val connection = connections[tag]
    Shizuku.unbindUserService(args(tag), connection, remove)
    if (remove) {
      connections.remove(tag)
    }
  }

  private fun args(tag: String): Shizuku.UserServiceArgs =
    Shizuku.UserServiceArgs(ComponentName(context, ShellService::class.java))
      .daemon(false)
      .processNameSuffix("shell")
      .tag(tag)
      .version(userServiceVersion)
}
