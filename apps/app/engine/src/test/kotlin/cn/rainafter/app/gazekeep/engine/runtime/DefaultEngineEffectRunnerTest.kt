package cn.rainafter.app.gazekeep.engine.runtime

import cn.rainafter.app.gazekeep.core.backend.ExtendResult
import cn.rainafter.app.gazekeep.core.backend.ExtendToken
import cn.rainafter.app.gazekeep.core.backend.KeepAwakeBackend
import cn.rainafter.app.gazekeep.core.backend.TimeoutDeadline
import cn.rainafter.app.gazekeep.core.engine.CapturePlan
import cn.rainafter.app.gazekeep.core.engine.EngineEffect
import cn.rainafter.app.gazekeep.core.engine.EngineEvent
import cn.rainafter.app.gazekeep.core.model.Attention
import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.core.model.GazeConfig
import cn.rainafter.app.gazekeep.core.model.Readiness
import cn.rainafter.app.gazekeep.core.model.Sensitivity
import cn.rainafter.app.gazekeep.core.policy.GateContext
import cn.rainafter.app.gazekeep.core.policy.GateInput
import cn.rainafter.app.gazekeep.engine.camera.CaptureOutcome
import cn.rainafter.app.gazekeep.engine.capture.AttentionCaptureResult
import cn.rainafter.app.gazekeep.engine.gate.GateProbe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultEngineEffectRunnerTest {
  private val plan = CapturePlan(abortAtUptime = 10_000L, sensitivity = Sensitivity.Standard)
  private val looking = AttentionCaptureResult(Attention.Looking, emptyList(), 100L, CaptureOutcome.Completed)

  private class Scheduler : WakeScheduler {
    var pending: (() -> Unit)? = null
    override fun schedule(atUptime: Long, onWake: () -> Unit) {
      pending = onWake
    }
    override fun cancel() {
      pending = null
    }
  }

  private fun runner(
    scope: CoroutineScope,
    scheduler: Scheduler = Scheduler(),
    capture: suspend (CapturePlan) -> AttentionCaptureResult = { error("unexpected capture") },
    gate: GateProbe = object : GateProbe {
      override suspend fun collect(): GateInput = error("unexpected gate")
    },
    onExtended: () -> Unit = { error("must not extend") },
    backend: KeepAwakeBackend? = null,
  ) = DefaultEngineEffectRunner(
    parentScope = scope,
    config = GazeConfig(),
    gateProbe = gate,
    scheduler = scheduler,
    snapshot = { null },
    backend = { backend },
    capture = capture,
    onExtended = onExtended,
    onRecord = {},
    onNotify = {},
  )

  @Test
  fun cancelledCaptureCannotInjectAnAbortIntoTheNextRound() = runBlocking {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val oldCapture = CompletableDeferred<AttentionCaptureResult>()
    var rounds = 0
    val runner = runner(scope, capture = { if (rounds++ == 0) oldCapture.await() else looking })
    val events = mutableListOf<EngineEvent>()
    try {
      runner.run(EngineEffect.StartCapture(plan), events::add)
      runner.run(EngineEffect.CancelCapture, events::add)
      runner.run(EngineEffect.StartCapture(plan), events::add)
      oldCapture.complete(looking)
      assertEquals(listOf<EngineEvent>(EngineEvent.CaptureFinished(Attention.Looking, emptyList(), 100L)), events)
    } finally {
      runner.close()
      scope.cancel()
    }
  }

  @Test
  fun closingSessionCancelsGateCaptureAndWakeWithoutCancellingItsParent() = runBlocking {
    val parent = SupervisorJob()
    val scope = CoroutineScope(parent + Dispatchers.Unconfined)
    val gateResult = CompletableDeferred<GateInput>()
    val captureResult = CompletableDeferred<AttentionCaptureResult>()
    val scheduler = Scheduler()
    val runner = runner(
      scope,
      scheduler,
      capture = { captureResult.await() },
      gate = object : GateProbe {
        override suspend fun collect() = gateResult.await()
      },
    )
    val events = mutableListOf<EngineEvent>()
    try {
      runner.run(EngineEffect.ScheduleWake(1_000L), events::add)
      runner.run(EngineEffect.RunGates(TimeoutDeadline(10_000L)), events::add)
      runner.run(EngineEffect.StartCapture(plan), events::add)
      runner.close()
      captureResult.complete(looking)
      gateResult.complete(GateInput(true, false, null, 0, true, null, null, null))
      assertTrue(events.isEmpty())
      assertEquals(null, scheduler.pending)
      assertTrue(parent.isActive)
    } finally {
      runner.close()
      scope.cancel()
    }
  }

  @Test fun cancelledExtendCannotPublishLateSuccessOrResetTheOracle() = runBlocking {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    lateinit var late: Continuation<ExtendResult>
    var extensions = 0
    val backend = object : KeepAwakeBackend {
      override val id = BackendId.Accessibility
      override val readiness = MutableStateFlow<Readiness>(Readiness.Ready)
      override suspend fun extend(token: ExtendToken): ExtendResult = suspendCoroutine { late = it }
      override suspend fun release() = Unit
      override suspend fun attach() = Unit
      override suspend fun detach() = Unit
    }
    val runner = runner(scope, backend = backend, onExtended = { extensions++ })
    val events = mutableListOf<EngineEvent>()
    try {
      runner.run(EngineEffect.Extend(ExtendToken.None), events::add)
      runner.run(EngineEffect.CancelCapture, events::add)
      late.resume(ExtendResult.Done)
      assertTrue(events.isEmpty())
      assertEquals(0, extensions)
    } finally {
      runner.close()
      scope.cancel()
    }
  }

  @Test
  fun disconnectedBackendFeedsFailureBackWithoutMarkingExtended() = runBlocking {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val runner = runner(scope)
    val events = mutableListOf<EngineEvent>()
    try {
      runner.run(EngineEffect.Extend(ExtendToken.None), events::add)
      val event = assertIs<EngineEvent.ExtendFinished>(events.single())
      assertIs<ExtendResult.Failed>(event.result)
      Unit
    } finally {
      runner.close()
      scope.cancel()
    }
  }
}
