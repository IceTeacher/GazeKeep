package cn.rainafter.app.gazekeep.backend.a11y

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import cn.rainafter.app.gazekeep.core.backend.KeepAwakeBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GazeA11yService : AccessibilityService() {
  companion object {
    @Volatile
    var current: GazeA11yService? = null
      private set

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _availableBackend = MutableStateFlow<KeepAwakeBackend?>(null)
    val availableBackend: StateFlow<KeepAwakeBackend?> = _availableBackend.asStateFlow()

    val signals = A11ySignals()
  }

  lateinit var backend: A11yKeepAwakeBackend
    private set
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  private val activityClasses = HashMap<String, Boolean>()
  private val foreground = A11yForeground(::isActivity)

  val foregroundPackage: String?
    get() = foreground.packageName

  override fun onServiceConnected() {
    super.onServiceConnected()
    backend = A11yKeepAwakeBackend(this) { signals.activity() }
    current = this
    _availableBackend.value = backend
    _connected.value = true
    scope.launch {
      runCatching { backend.attach() }
    }
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    // 只使用事件类型、包名和类名作为前台应用线索，不读取 event.source 或文本。
    when (event?.eventType) {
      AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
        foreground.onWindowStateChanged(event.packageName?.toString(), event.className?.toString())
      }

      AccessibilityEvent.TYPE_TOUCH_INTERACTION_START -> {
        // 部分 HyperOS 版本不会把屏幕外触摸转成 overlay 的 ACTION_OUTSIDE，
        // 无障碍触摸事件是同一条链路中更稳定的活动信号。
        signals.activity()
      }
    }
  }

  override fun onInterrupt() = Unit

  // 事件在主线程串行送达；不可见的包（无启动器入口）解析失败，按非 Activity 处理。
  private fun isActivity(packageName: String, className: String): Boolean =
    activityClasses.getOrPut("$packageName/$className") {
      val component = ComponentName(packageName, className)
      try {
        if (Build.VERSION.SDK_INT >= 33) {
          packageManager.getActivityInfo(component, PackageManager.ComponentInfoFlags.of(0))
        } else {
          @Suppress("DEPRECATION")
          packageManager.getActivityInfo(component, 0)
        }
        true
      } catch (_: PackageManager.NameNotFoundException) {
        false
      }
    }

  override fun onUnbind(intent: android.content.Intent?): Boolean {
    disconnect()
    return super.onUnbind(intent)
  }

  override fun onDestroy() {
    disconnect()
    scope.cancel()
    super.onDestroy()
  }

  private fun disconnect() {
    if (::backend.isInitialized) backend.detachImmediately()
    // 旧服务延迟销毁不能清掉已经连接的新实例。
    if (current !== this) return
    current = null
    _connected.value = false
    _availableBackend.value = null
    signals.unavailable()
  }
}
