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

  @Test fun reportsOpenClassifyAndTailTimings() = runBlocking {
    val image = Proxy.newProxyInstance(ImageProxy::class.java.classLoader, arrayOf(ImageProxy::class.java)) { _, _, _ ->
      null
    } as ImageProxy
    var now = 1_000L
    val source = object : FrameSource {
      override suspend fun capture(plan: CapturePlan, onFrame: (ImageProxy) -> Boolean): CaptureOutcome {
        now += 300L // 相机打开到首帧
        onFrame(image)
        now += 110L // 帧间隔等待
        onFrame(image)
        now += 50L // 解绑与 analyzer 排空
        return CaptureOutcome.Completed
      }
      override fun close() = Unit
    }
    val classifier = object : FrameClassifier {
      override suspend fun classify(image: ImageProxy, sensitivity: Sensitivity): FrameVerdict {
        now += 40L
        return FrameVerdict.Looking
      }
      override fun close() = Unit
    }
    val result = AttentionCaptureSession(source, classifier) { now }
      .capture(CapturePlan(3, 150L, 9_000L, Sensitivity.Standard))
    assertEquals(CaptureTimings(firstFrameMs = 300L, classifyMs = listOf(40L, 40L), tailMs = 50L), result.timings)
    assertEquals(540L, result.elapsedMs)
  }

  @Test fun fastFirstFrameStopsAfterSingleLookingFrame() = runBlocking {
    val image = Proxy.newProxyInstance(ImageProxy::class.java.classLoader, arrayOf(ImageProxy::class.java)) { _, _, _ ->
      null
    } as ImageProxy
    var delivered = 0
    val source = object : FrameSource {
      override suspend fun capture(plan: CapturePlan, onFrame: (ImageProxy) -> Boolean): CaptureOutcome {
        while (delivered < plan.maxFrames) {
          delivered++
          if (!onFrame(image)) break
        }
        return CaptureOutcome.Completed
      }
      override fun close() = Unit
    }
    val classifier = object : FrameClassifier {
      override suspend fun classify(image: ImageProxy, sensitivity: Sensitivity): FrameVerdict = FrameVerdict.Looking
      override fun close() = Unit
    }
    val result = AttentionCaptureSession(source, classifier) { 1_000L }
      .capture(CapturePlan(3, 150L, 9_000L, Sensitivity.Standard, fastFirstFrame = true))
    assertEquals(Attention.Looking, result.attention)
    assertEquals(1, delivered)
    assertEquals(listOf(FrameVerdict.Looking), result.frames)
  }

  @Test fun fastFirstFrameFallsBackToThreeFramesAfterBlink() = runBlocking {
    val image = Proxy.newProxyInstance(ImageProxy::class.java.classLoader, arrayOf(ImageProxy::class.java)) { _, _, _ ->
      null
    } as ImageProxy
    val verdicts = ArrayDeque(
      listOf(
        FrameVerdict.NotLooking(FrameVerdict.Reason.EyesClosed),
        FrameVerdict.Looking,
        FrameVerdict.Looking,
      ),
    )
    var delivered = 0
    val source = object : FrameSource {
      override suspend fun capture(plan: CapturePlan, onFrame: (ImageProxy) -> Boolean): CaptureOutcome {
        while (delivered < plan.maxFrames) {
          delivered++
          if (!onFrame(image)) break
        }
        return CaptureOutcome.Completed
      }
      override fun close() = Unit
    }
    val classifier = object : FrameClassifier {
      override suspend fun classify(image: ImageProxy, sensitivity: Sensitivity): FrameVerdict = verdicts.removeFirst()
      override fun close() = Unit
    }
    val result = AttentionCaptureSession(source, classifier) { 1_000L }
      .capture(CapturePlan(3, 150L, 9_000L, Sensitivity.Standard, fastFirstFrame = true))
    assertEquals(Attention.Looking, result.attention)
    assertEquals(3, delivered)
    assertEquals(3, result.frames.size)
  }
}
