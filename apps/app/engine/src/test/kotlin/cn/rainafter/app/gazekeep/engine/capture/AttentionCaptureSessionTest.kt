package cn.rainafter.app.gazekeep.engine.capture

import androidx.camera.core.ImageProxy
import cn.rainafter.app.gazekeep.core.engine.CapturePlan
import cn.rainafter.app.gazekeep.core.model.Attention
import cn.rainafter.app.gazekeep.core.model.FrameVerdict
import cn.rainafter.app.gazekeep.core.model.Sensitivity
import cn.rainafter.app.gazekeep.engine.camera.CaptureOutcome
import cn.rainafter.app.gazekeep.engine.camera.FrameSource
import cn.rainafter.app.gazekeep.engine.classify.FrameClassifier
import kotlinx.coroutines.runBlocking
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class AttentionCaptureSessionTest {
  @Test fun forwardsWholePlanIncludingIntervalAndAbsoluteDeadline() = runBlocking {
    val plan = CapturePlan(2, 375L, 9_000L, Sensitivity.Loose)
    val image = Proxy.newProxyInstance(ImageProxy::class.java.classLoader, arrayOf(ImageProxy::class.java)) { _, _, _ ->
      null
    } as ImageProxy
    var seen: CapturePlan? = null
    val source = object : FrameSource {
      override suspend fun capture(plan: CapturePlan, onFrame: (ImageProxy) -> Boolean): CaptureOutcome {
        seen = plan
        repeat(2) { onFrame(image) }
        return CaptureOutcome.Completed
      }
      override fun close() = Unit
    }
    val classifier = object : FrameClassifier {
      override suspend fun classify(image: ImageProxy, sensitivity: Sensitivity): FrameVerdict {
        assertEquals(Sensitivity.Loose, sensitivity)
        return FrameVerdict.Looking
      }
      override fun close() = Unit
    }
    val result = AttentionCaptureSession(source, classifier) { 1_000L }.capture(plan)
    assertSame(plan, seen)
    assertEquals(Attention.Looking, result.attention)
    assertEquals(2, result.frames.size)
  }
}
