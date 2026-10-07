package cn.rainafter.app.gazekeep.engine.m0

import android.os.SystemClock
import androidx.camera.core.ImageProxy
import cn.rainafter.app.gazekeep.core.model.Attention
import cn.rainafter.app.gazekeep.core.model.FrameVerdict
import cn.rainafter.app.gazekeep.core.model.Sensitivity
import cn.rainafter.app.gazekeep.core.policy.AttentionVoter
import cn.rainafter.app.gazekeep.core.policy.VoteState
import cn.rainafter.app.gazekeep.engine.camera.CaptureOutcome
import cn.rainafter.app.gazekeep.engine.camera.CapturePlan
import cn.rainafter.app.gazekeep.engine.camera.FrameSource
import cn.rainafter.app.gazekeep.engine.classify.FrameClassifier
import kotlinx.coroutines.runBlocking

data class M0CaptureResult(
    val attention: Attention,
    val frames: List<FrameVerdict>,
    val elapsedMs: Long,
    val outcome: CaptureOutcome,
)

class M0CaptureSession(
    private val frameSource: FrameSource,
    private val classifier: FrameClassifier,
) {
    suspend fun capture(
        sensitivity: Sensitivity = Sensitivity.Standard,
        maxFrames: Int = 3,
        timeoutMs: Long = 1_600L,
    ): M0CaptureResult {
        val started = SystemClock.uptimeMillis()
        val voter = AttentionVoter(maxFrames = maxFrames, need = 2)
        val plan = CapturePlan(maxFrames = maxFrames, abortAtUptime = started + timeoutMs)
        val outcome = frameSource.capture(plan) { image: ImageProxy ->
            val verdict = runBlocking { classifier.classify(image, sensitivity) }
            when (val state = voter.offer(verdict)) {
                is VoteState.Decided -> false
                VoteState.Continue -> true
            }
        }
        val state = voter.finish()
        val attention = (state as? VoteState.Decided)?.attention ?: Attention.Unknown
        return M0CaptureResult(
            attention = attention,
            frames = voter.frames,
            elapsedMs = SystemClock.uptimeMillis() - started,
            outcome = outcome,
        )
    }
}
