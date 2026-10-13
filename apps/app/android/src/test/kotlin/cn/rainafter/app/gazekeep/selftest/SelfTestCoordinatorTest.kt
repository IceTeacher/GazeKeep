package cn.rainafter.app.gazekeep.selftest

import androidx.camera.core.ImageProxy
import cn.rainafter.app.gazekeep.core.engine.CapturePlan
import cn.rainafter.app.gazekeep.core.model.FrameVerdict
import cn.rainafter.app.gazekeep.core.model.Sensitivity
import cn.rainafter.app.gazekeep.engine.camera.CaptureOutcome
import cn.rainafter.app.gazekeep.engine.camera.FrameSource
import cn.rainafter.app.gazekeep.engine.classify.FrameClassifier
import cn.rainafter.app.gazekeep.ui.contract.SelfTestStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Test
import java.lang.reflect.Proxy
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SelfTestCoordinatorTest {
  @Test fun failedSaveKeepsSecondStepRetryable() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    var verdict: FrameVerdict = FrameVerdict.Looking
    var saves = 0
    val image = Proxy.newProxyInstance(ImageProxy::class.java.classLoader, arrayOf(ImageProxy::class.java)) { _, _, _ ->
      null
    } as ImageProxy
    val source = object : FrameSource {
      override suspend fun capture(plan: CapturePlan, onFrame: (ImageProxy) -> Boolean): CaptureOutcome {
        repeat(2) { onFrame(image) }
        return CaptureOutcome.Completed
      }
      override fun close() = Unit
    }
    val classifier = object : FrameClassifier {
      override suspend fun classify(image: ImageProxy, sensitivity: Sensitivity) = verdict
      override fun close() = Unit
    }
    val coordinator = SelfTestCoordinator(scope, source, classifier, {
      if (saves++ == 0) throw IllegalStateException("save failed")
    }, { 1_000L })
    try {
      coordinator.begin()
      coordinator.capture(Sensitivity.Standard)
      assertEquals(SelfTestStep.Away, coordinator.state.value.step)
      verdict = FrameVerdict.NotLooking(FrameVerdict.Reason.HeadTurned)
      coordinator.capture(Sensitivity.Standard)
      assertEquals(SelfTestStep.Away, coordinator.state.value.step)
      assertTrue(coordinator.state.value.lastStepFailed)
      coordinator.capture(Sensitivity.Standard)
      assertEquals(SelfTestStep.Complete, coordinator.state.value.step)
      assertEquals(2, saves)
    } finally {
      coordinator.close()
      scope.cancel()
    }
  }
}
