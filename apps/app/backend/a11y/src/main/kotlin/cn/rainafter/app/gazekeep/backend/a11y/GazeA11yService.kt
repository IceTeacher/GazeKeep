package cn.rainafter.app.gazekeep.backend.a11y

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
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

    val signals = A11ySignals()
  }

  lateinit var backend: A11yKeepAwakeBackend
    private set
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  @Volatile
  var foregroundPackage: String? = null
    private set

  override fun onServiceConnected() {
    super.onServiceConnected()
    current = this
    _connected.value = true
    backend = A11yKeepAwakeBackend(this) { signals.activity() }
    scope.launch {
      runCatching { backend.attach() }
    }
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    // 只使用事件类型和包名作为前台应用线索，不读取 event.source 或文本。
    when (event?.eventType) {
      AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
        foregroundPackage = event.packageName?.toString()
      }

      AccessibilityEvent.TYPE_TOUCH_INTERACTION_START -> {
        // 部分 HyperOS 版本不会把屏幕外触摸转成 overlay 的 ACTION_OUTSIDE，
        // 无障碍触摸事件是同一条链路中更稳定的活动信号。
        signals.activity()
      }
    }
  }

  override fun onInterrupt() = Unit

  override fun onUnbind(intent: android.content.Intent?): Boolean {
    scope.launch {
      runCatching { backend.detach() }
    }
    signals.unavailable()
    current = null
    _connected.value = false
    return super.onUnbind(intent)
  }

  override fun onDestroy() {
    if (::backend.isInitialized) {
      scope.launch {
        runCatching { backend.detach() }
        scope.cancel()
      }
    } else {
      scope.cancel()
    }
    signals.unavailable()
    current = null
    _connected.value = false
    super.onDestroy()
  }
}
