package cn.rainafter.app.gazekeep.selftest

import androidx.camera.core.ImageProxy
import cn.rainafter.app.gazekeep.core.engine.CapturePlan
import cn.rainafter.app.gazekeep.core.model.FrameVerdict
import cn.rainafter.app.gazekeep.core.model.Sensitivity
import cn.rainafter.app.gazekeep.engine.camera.CaptureOutcome
import cn.rainafter.app.gazekeep.engine.camera.CaptureOwnershipCoordinator
import cn.rainafter.app.gazekeep.engine.camera.FrameSource
import cn.rainafter.app.gazekeep.engine.classify.FrameClassifier
import cn.rainafter.app.gazekeep.ui.contract.SelfTestStep
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Test
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SelfTestCoordinatorTest {
  @Test fun pauseReleasesServiceAvoidanceAndKeepsPassedStep() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val ownership = CaptureOwnershipCoordinator()
    val coordinator = coordinator(scope, ownership) { onFrame -> repeat(2) { onFrame(image()) } }
    try {
      coordinator.begin()
      coordinator.capture(Sensitivity.Standard)
      assertEquals(SelfTestStep.Away, coordinator.state.value.step)
      coordinator.pause()
      assertFalse(ownership.isSelfTestActive())
      assertEquals(SelfTestStep.Away, coordinator.state.value.step)
    } finally {
      coordinator.close()
      scope.cancel()
    }
  }

  @Test fun cancelledOldCaptureCannotAdvanceRestartedSelfTestOrClearNewRunningState() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    lateinit var old: Continuation<Unit>
    val next = CompletableDeferred<Unit>()
    var rounds = 0
    val coordinator = coordinator(scope) { onFrame ->
      if (rounds++ == 0) suspendCoroutine { old = it } else next.await()
      repeat(2) { onFrame(image()) }
    }
    try {
      coordinator.begin()
      coordinator.capture(Sensitivity.Standard)
      coordinator.begin()
      coordinator.capture(Sensitivity.Standard)
      old.resume(Unit)
      assertEquals(SelfTestStep.Looking, coordinator.state.value.step)
      assertTrue(coordinator.state.value.running)
    } finally {
      coordinator.close()
      scope.cancel()
    }
  }

  @Test fun resumePreservesProgressWithoutAutomaticallyStartingAnotherCapture() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val ownership = CaptureOwnershipCoordinator()
    var captures = 0
    val coordinator = coordinator(scope, ownership) { onFrame ->
      captures++
      repeat(2) { onFrame(image()) }
    }
    try {
      coordinator.begin()
      coordinator.capture(Sensitivity.Standard)
      coordinator.pause()
      coordinator.capture(Sensitivity.Standard)
      assertEquals(1, captures)
      assertFalse(ownership.isSelfTestActive())
      coordinator.resume()
      assertTrue(ownership.isSelfTestActive())
      assertEquals(SelfTestStep.Away, coordinator.state.value.step)
      assertEquals(1, captures)
      coordinator.capture(Sensitivity.Standard)
      assertEquals(2, captures)
    } finally {
      coordinator.close()
      scope.cancel()
    }
  }

  @Test fun closingOldControllerDoesNotReleaseNewControllersSelfTestLease() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val ownership = CaptureOwnershipCoordinator()
    val old = coordinator(scope, ownership) {}
    val next = coordinator(scope, ownership) {}
    try {
      old.begin()
      next.begin()
      old.close()
      old.close()
      old.begin()
      assertTrue(ownership.isSelfTestActive())
      next.cancel()
      assertFalse(ownership.isSelfTestActive())
    } finally {
      old.close()
      next.close()
      scope.cancel()
    }
  }

  private fun image() = Proxy.newProxyInstance(
    ImageProxy::class.java.classLoader,
    arrayOf(ImageProxy::class.java),
  ) { _, _, _ -> null } as ImageProxy

  private fun coordinator(
    scope: CoroutineScope,
    ownership: CaptureOwnershipCoordinator = CaptureOwnershipCoordinator(),
    capture: suspend ((ImageProxy) -> Boolean) -> Unit,
  ) = SelfTestCoordinator(
    scope,
    object : FrameSource {
      override suspend fun capture(plan: CapturePlan, onFrame: (ImageProxy) -> Boolean): CaptureOutcome {
        capture(onFrame)
        return CaptureOutcome.Completed
      }
      override fun close() = Unit
    },
    object : FrameClassifier {
      override suspend fun classify(image: ImageProxy, sensitivity: Sensitivity) = FrameVerdict.Looking
      override fun close() = Unit
    },
    {},
    { 1_000L },
    ownership,
  )

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
