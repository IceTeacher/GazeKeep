package cn.rainafter.app.gazekeep.engine.capture

import android.os.SystemClock
import androidx.camera.core.ImageProxy
import cn.rainafter.app.gazekeep.core.engine.CapturePlan
import cn.rainafter.app.gazekeep.core.model.Attention
import cn.rainafter.app.gazekeep.core.model.FrameVerdict
import cn.rainafter.app.gazekeep.core.policy.AttentionVoter
import cn.rainafter.app.gazekeep.core.policy.VoteState
import cn.rainafter.app.gazekeep.engine.camera.CaptureOutcome
import cn.rainafter.app.gazekeep.engine.camera.FrameSource
import cn.rainafter.app.gazekeep.engine.classify.FrameClassifier
import kotlinx.coroutines.runBlocking

data class AttentionCaptureResult(
  val attention: Attention,
  val frames: List<FrameVerdict>,
  val elapsedMs: Long,
  val outcome: CaptureOutcome,
)

class AttentionCaptureSession(
  private val frameSource: FrameSource,
  private val classifier: FrameClassifier,
  private val clock: () -> Long = { SystemClock.uptimeMillis() },
) {
  suspend fun capture(plan: CapturePlan): AttentionCaptureResult {
    val started = clock()
    val voter = AttentionVoter(maxFrames = plan.maxFrames, need = 2)
    val outcome = frameSource.capture(plan) { image: ImageProxy ->
      val verdict = runBlocking { classifier.classify(image, plan.sensitivity) }
      when (val state = voter.offer(verdict)) {
        is VoteState.Decided -> false
        VoteState.Continue -> true
      }
    }
    val state = voter.finish()
    val attention = (state as? VoteState.Decided)?.attention ?: Attention.Unknown
    return AttentionCaptureResult(
      attention = attention,
      frames = voter.frames,
      elapsedMs = clock() - started,
      outcome = outcome,
    )
  }
}
