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

/**
 * 分段耗时，只用于调试日志与功耗基线：开始到首帧（含 provider、绑定与相机打开）、
 * 每帧分类、末帧分类结束到采集返回（含解绑与 analyzer 排空）。没有帧时为 null/空。
 */
data class CaptureTimings(
  val firstFrameMs: Long? = null,
  val classifyMs: List<Long> = emptyList(),
  val tailMs: Long? = null,
)

data class AttentionCaptureResult(
  val attention: Attention,
  val frames: List<FrameVerdict>,
  val elapsedMs: Long,
  val outcome: CaptureOutcome,
  val timings: CaptureTimings = CaptureTimings(),
)

class AttentionCaptureSession(
  private val frameSource: FrameSource,
  private val classifier: FrameClassifier,
  private val clock: () -> Long = { SystemClock.uptimeMillis() },
) {
  suspend fun capture(plan: CapturePlan): AttentionCaptureResult {
    val started = clock()
    val voter = AttentionVoter(maxFrames = plan.maxFrames, need = 2)
    var firstFrameAt: Long? = null
    var lastClassifiedAt: Long? = null
    val classifyMs = mutableListOf<Long>()
    val outcome = frameSource.capture(plan) { image: ImageProxy ->
      val frameAt = clock()
      if (firstFrameAt == null) firstFrameAt = frameAt
      val verdict = runBlocking { classifier.classify(image, plan.sensitivity) }
      val classifiedAt = clock()
      classifyMs += classifiedAt - frameAt
      lastClassifiedAt = classifiedAt
      when (val state = voter.offer(verdict)) {
        is VoteState.Decided -> false
        VoteState.Continue -> true
      }
    }
    val finished = clock()
    val state = voter.finish()
    val attention = (state as? VoteState.Decided)?.attention ?: Attention.Unknown
    return AttentionCaptureResult(
      attention = attention,
      frames = voter.frames,
      elapsedMs = finished - started,
      outcome = outcome,
      timings = CaptureTimings(
        firstFrameMs = firstFrameAt?.let { it - started },
        classifyMs = classifyMs.toList(),
        tailMs = lastClassifiedAt?.let { finished - it },
      ),
    )
  }
}
