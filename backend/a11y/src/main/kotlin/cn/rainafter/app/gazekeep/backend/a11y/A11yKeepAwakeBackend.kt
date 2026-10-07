package cn.rainafter.app.gazekeep.backend.a11y

import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.accessibilityservice.AccessibilityService
import cn.rainafter.app.gazekeep.core.backend.ExtendResult
import cn.rainafter.app.gazekeep.core.backend.ExtendToken
import cn.rainafter.app.gazekeep.core.backend.KeepAwakeBackend
import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.core.model.Readiness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class A11yKeepAwakeBackend(
    private val service: AccessibilityService,
    private val onExternalActivity: () -> Unit = {},
) : KeepAwakeBackend {
    private enum class KeepAwakeMode { Pulse, Hold }

    // 已验证：小米 12 Pro（2207122MC，HyperOS V816，Android 14）脉冲释放不会重置完整熄屏周期。
    private val mode = KeepAwakeMode.Hold
    override val id: BackendId = BackendId.Accessibility
    private val _readiness = MutableStateFlow<Readiness>(Readiness.NeedsSetup(cn.rainafter.app.gazekeep.core.model.SetupStep.EnableAccessibilityService))
    override val readiness: StateFlow<Readiness> = _readiness.asStateFlow()

    private var windowManager: WindowManager? = null
    private var overlay: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var attached = false
    private var lastSequence: Long = Long.MIN_VALUE
    private var keepScreenOn = false

    override suspend fun attach() = withContext(Dispatchers.Main.immediate) {
        if (attached) return@withContext
        val manager = service.getSystemService(WindowManager::class.java)
        val view = TouchProbeView(service, onExternalActivity).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }
        val layout = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START; alpha = 0.01f }
        manager.addView(view, layout)
        windowManager = manager
        overlay = view
        params = layout
        attached = true
        _readiness.value = Readiness.Ready
    }

    override suspend fun detach() = withContext(Dispatchers.Main.immediate) { detachImmediately() }

    /** 引擎或无障碍服务销毁时由主线程直接移除，防止保亮标记残留。 */
    fun detachImmediately() {
        val view = overlay
        if (view != null) runCatching { windowManager?.removeViewImmediate(view) }
        overlay = null
        params = null
        windowManager = null
        attached = false
        keepScreenOn = false
        _readiness.value = Readiness.NeedsSetup(cn.rainafter.app.gazekeep.core.model.SetupStep.EnableAccessibilityService)
    }

    override suspend fun extend(token: ExtendToken): ExtendResult = withContext(Dispatchers.Main.immediate) {
        if (!attached || overlay == null || params == null) return@withContext ExtendResult.Failed(IllegalStateException("overlay unavailable"))
        if (token is ExtendToken.Seq && token.value <= lastSequence) return@withContext ExtendResult.Stale
        if (token is ExtendToken.Seq) lastSequence = token.value
        val updated = runCatching { setKeepScreenOn(true) }.getOrDefault(false)
        if (!updated) return@withContext ExtendResult.Failed(IllegalStateException("overlay unavailable"))
        if (mode == KeepAwakeMode.Pulse) {
            delay(300L)
            runCatching { setKeepScreenOn(false) }
        }
        ExtendResult.Done
    }

    override suspend fun release() = withContext(Dispatchers.Main.immediate) {
        if (mode == KeepAwakeMode.Hold) runCatching { setKeepScreenOn(false) }
    }

    private fun setKeepScreenOn(enabled: Boolean): Boolean {
        val view = overlay ?: return false
        val layout = params ?: return false
        if (keepScreenOn == enabled) return true
        layout.flags = if (enabled) {
            layout.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        } else {
            layout.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
        }
        windowManager?.updateViewLayout(view, layout) ?: return false
        keepScreenOn = enabled
        return true
    }

    private class TouchProbeView(
        context: android.content.Context,
        private val onExternalActivity: () -> Unit,
    ) : View(context) {
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE ||
                event.actionMasked == MotionEvent.ACTION_DOWN
            ) {
                onExternalActivity()
            }
            return true
        }
    }
}
