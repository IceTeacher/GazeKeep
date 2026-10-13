package cn.rainafter.app.gazekeep.engine.camera

import androidx.camera.core.ImageProxy
import cn.rainafter.app.gazekeep.core.engine.AbortReason
import cn.rainafter.app.gazekeep.core.engine.CapturePlan

sealed interface CaptureOutcome {
  data object Completed : CaptureOutcome
  data class Aborted(val reason: AbortReason) : CaptureOutcome
}

interface FrameSource : AutoCloseable {
  /** 回调只在 analyzer 线程同步执行，返回后图像立即关闭。 */
  suspend fun capture(plan: CapturePlan, onFrame: (ImageProxy) -> Boolean): CaptureOutcome
}
