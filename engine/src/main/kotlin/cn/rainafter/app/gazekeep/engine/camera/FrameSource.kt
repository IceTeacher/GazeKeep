package cn.rainafter.app.gazekeep.engine.camera

import androidx.camera.core.ImageProxy
import cn.rainafter.app.gazekeep.core.model.Sensitivity

data class CapturePlan(
    val maxFrames: Int = 3,
    val frameIntervalMs: Long = 150L,
    val abortAtUptime: Long,
    val sensitivity: Sensitivity = Sensitivity.Standard,
)

sealed interface CaptureOutcome {
    data class Completed(val frames: Int) : CaptureOutcome
    data class Aborted(val reason: AbortReason) : CaptureOutcome
}

enum class AbortReason {
    Deadline,
    CameraInUse,
    CameraDisabled,
    Cancelled,
    Error,
}

interface FrameSource {
    /** 回调只在 analyzer 线程同步执行，返回后图像立即关闭。 */
    suspend fun capture(plan: CapturePlan, onFrame: (ImageProxy) -> Boolean): CaptureOutcome
}
