package cn.rainafter.app.gazekeep.core.engine

import cn.rainafter.app.gazekeep.core.backend.ExtendResult
import cn.rainafter.app.gazekeep.core.backend.ExtendToken
import cn.rainafter.app.gazekeep.core.backend.TimeoutDeadline
import cn.rainafter.app.gazekeep.core.model.Attention
import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.core.model.FrameVerdict
import cn.rainafter.app.gazekeep.core.model.GazeConfig
import cn.rainafter.app.gazekeep.core.policy.GateDecision
import cn.rainafter.app.gazekeep.core.policy.SkipReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EngineReducerTest {
  private val deadline = TimeoutDeadline(20_000L, ExtendToken.Seq(7L), "demo.app")

  private fun ready(
    context: EngineContext = EngineContext(),
    state: EngineState = EngineState.Yielded(YieldReason.WaitingDeadline),
    config: GazeConfig = GazeConfig(enabled = true),
  ) = EngineSnapshot(state, context, config, BackendId.Accessibility)

  private fun capturing(snapshot: EngineSnapshot = ready()): EngineSnapshot {
    val armed = EngineReducer.reduce(snapshot, EngineEvent.DeadlineUpdated(deadline), 0L).snapshot
    val gating = EngineReducer.reduce(
      armed,
      EngineEvent.ScheduledWakeFired((armed.state as EngineState.Armed).wakeAt),
      12_000L,
    ).snapshot
    return EngineReducer.reduce(gating, EngineEvent.GateResult(GateDecision.Proceed), 12_100L).snapshot
  }

  @Test
  fun longTimeoutChecksBeforeDimmingAndKeepsRealScreenOffDeadline() {
    val longDeadline = deadline.copy(screenOffAtUptime = 60_000L, screenDimAtUptime = 48_000L)
    val armed = EngineReducer.reduce(ready(), EngineEvent.DeadlineUpdated(longDeadline), 0L)
    assertEquals(40_000L, assertIs<EngineState.Armed>(armed.snapshot.state).wakeAt)
    assertEquals(listOf(EngineEffect.ScheduleWake(40_000L)), armed.effects)

    // 系统在降亮前通知 App 后，reducer 不能再等到熄屏前八秒。
    val gating = EngineReducer.reduce(ready(), EngineEvent.DeadlineUpdated(longDeadline), 40_000L)
    assertEquals(longDeadline, assertIs<EngineState.Gating>(gating.snapshot.state).deadline)
    val capture = EngineReducer.reduce(gating.snapshot, EngineEvent.GateResult(GateDecision.Proceed), 40_100L)
    assertEquals(41_700L, assertIs<EngineState.Capturing>(capture.snapshot.state).abortAt)
    val result = EngineReducer.reduce(
      capture.snapshot,
      EngineEvent.CaptureFinished(Attention.Looking, emptyList(), 500L),
      40_600L,
    )
    assertEquals(listOf(EngineEffect.Extend(longDeadline.token)), result.effects)
  }

  @Test
  fun unknownBeforeDimmingStillYieldsWithoutExtending() {
    val longDeadline = deadline.copy(screenOffAtUptime = 60_000L, screenDimAtUptime = 48_000L)
    val gating = EngineReducer.reduce(ready(), EngineEvent.DeadlineUpdated(longDeadline), 40_000L)
    val capture = EngineReducer.reduce(gating.snapshot, EngineEvent.GateResult(GateDecision.Proceed), 40_100L)
    val result = EngineReducer.reduce(
      capture.snapshot,
      EngineEvent.CaptureFinished(Attention.Unknown, emptyList(), 500L),
      40_600L,
    )
    assertEquals(EngineState.Yielded(YieldReason.NotLooking(Attention.Unknown)), result.snapshot.state)
    assertTrue(result.effects.none { it is EngineEffect.Extend || it is EngineEffect.ScheduleWake })
    assertTrue(result.effects.any { it is EngineEffect.Release })
  }

  @Test
  fun missingOrInvalidDimmingTimeUsesScreenOffSchedule() {
    for (dimAt in listOf(null, -1L, 0L, 70_000L)) {
      val d = deadline.copy(screenOffAtUptime = 60_000L, screenDimAtUptime = dimAt)
      val result = EngineReducer.reduce(ready(), EngineEvent.DeadlineUpdated(d), 0L)
      assertEquals(52_000L, assertIs<EngineState.Armed>(result.snapshot.state).wakeAt)
    }
  }

  @Test
  fun latePreDimCallbackUsesRealScreenOffWindow() {
    val d = deadline.copy(screenOffAtUptime = 60_000L, screenDimAtUptime = 48_000L)
    val usable = EngineReducer.reduce(ready(), EngineEvent.DeadlineUpdated(d), 49_000L)
    assertIs<EngineState.Gating>(usable.snapshot.state)
    val tooLate = EngineReducer.reduce(ready(), EngineEvent.DeadlineUpdated(d), 58_000L)
    assertEquals(EngineState.Yielded(YieldReason.TooLate), tooLate.snapshot.state)
    assertTrue(tooLate.effects.none { it is EngineEffect.StartCapture || it is EngineEffect.Extend })
  }

  @Test
  fun unknownNeverProducesExtend() {
    val transition = EngineReducer.reduce(
      capturing(),
      EngineEvent.CaptureFinished(Attention.Unknown, emptyList(), 500L),
      13_000L,
    )
    assertIs<EngineState.Yielded>(transition.snapshot.state)
    assertTrue(transition.effects.none { it is EngineEffect.Extend })
    assertTrue(transition.effects.any { it is EngineEffect.Release })
  }

  @Test
  fun userActivityCancelsCaptureAndNeverExtends() {
    val transition = EngineReducer.reduce(capturing(), EngineEvent.UserActivity(13_100L), 13_100L)
    assertEquals(EngineState.Yielded(YieldReason.WaitingDeadline), transition.snapshot.state)
    assertTrue(transition.effects.any { it is EngineEffect.CancelCapture })
    assertTrue(transition.effects.none { it is EngineEffect.Extend })
  }

  @Test
  fun userActivityBeforeNewDeadlineCancelsOldCaptureAndArmsNewWindow() {
    val activity = EngineReducer.reduce(capturing(), EngineEvent.UserActivity(13_100L), 13_100L)
    assertTrue(activity.effects.any { it is EngineEffect.CancelCapture })
    val newDeadline = deadline.copy(screenOffAtUptime = 73_100L, screenDimAtUptime = 55_100L)
    val scheduled = EngineReducer.reduce(activity.snapshot, EngineEvent.DeadlineUpdated(newDeadline), 13_100L)
    assertEquals(47_100L, assertIs<EngineState.Armed>(scheduled.snapshot.state).wakeAt)
    assertEquals(listOf(EngineEffect.ScheduleWake(47_100L)), scheduled.effects)
    val staleCapture = EngineReducer.reduce(
      scheduled.snapshot,
      EngineEvent.CaptureFinished(Attention.Looking, emptyList(), 500L),
      13_200L,
    )
    assertIs<EngineState.Armed>(staleCapture.snapshot.state)
    assertTrue(staleCapture.effects.none { it is EngineEffect.Extend })
  }

  @Test
  fun maxHoldYieldsOnNextGate() {
    val config = GazeConfig(enabled = true, maxHoldMinutes = 10)
    val context = EngineContext(holdStartUptime = 0L, lookingStreak = 3, forcedYieldPending = true)
    val gate = EngineReducer.reduce(
      ready(context = context, state = EngineState.Gating(deadline), config = config),
      EngineEvent.GateResult(GateDecision.Proceed),
      600_001L,
    )
    assertEquals(EngineState.Yielded(YieldReason.MaxHold), gate.snapshot.state)
    assertTrue(gate.effects.none { it is EngineEffect.StartCapture })
    assertTrue(EngineEffect.Release in gate.effects)
    assertTrue(gate.snapshot.context.forcedYieldPending)
  }

  @Test
  fun skippedGateReleasesAnExistingHold() {
    val result = EngineReducer.reduce(
      ready(state = EngineState.Gating(deadline)),
      EngineEvent.GateResult(GateDecision.Skip(SkipReason.CameraBusy)),
      12_000L,
    )
    assertTrue(EngineEffect.Release in result.effects)
  }

  @Test
  fun jitteredDeadlineAfterSkippedGateDoesNotGateAgain() {
    // 锁屏亮屏时系统每次只把截止时间推后几毫秒，不能每次都重新门控。
    val skipped = EngineReducer.reduce(
      EngineReducer.reduce(ready(), EngineEvent.DeadlineUpdated(deadline), 15_000L).snapshot,
      EngineEvent.GateResult(GateDecision.Skip(SkipReason.Locked)),
      15_010L,
    ).snapshot
    val jitter = EngineReducer.reduce(
      skipped,
      EngineEvent.DeadlineUpdated(deadline.copy(screenOffAtUptime = deadline.screenOffAtUptime + 5L)),
      15_200L,
    )
    assertIs<EngineState.Yielded>(jitter.snapshot.state)
    assertTrue(jitter.effects.isEmpty())
  }

  @Test
  fun newDeadlineCycleOrTouchAfterSkippedGateGatesAgain() {
    val skipped = EngineReducer.reduce(
      EngineReducer.reduce(ready(), EngineEvent.DeadlineUpdated(deadline), 15_000L).snapshot,
      EngineEvent.GateResult(GateDecision.Skip(SkipReason.Locked)),
      15_010L,
    ).snapshot
    val extended = deadline.copy(screenOffAtUptime = 31_000L)
    val nextCycle = EngineReducer.reduce(skipped, EngineEvent.DeadlineUpdated(extended), 26_000L)
    assertEquals(listOf(EngineEffect.RunGates(extended)), nextCycle.effects)

    val touched = deadline.copy(
      screenOffAtUptime = deadline.screenOffAtUptime + 5L,
      lastExternalActivityUptime = 15_100L,
    )
    val afterTouch = EngineReducer.reduce(skipped, EngineEvent.DeadlineUpdated(touched), 15_200L)
    assertIs<EngineState.Gating>(afterTouch.snapshot.state)
  }

  @Test
  fun unrelatedUnknownDoesNotCountAsDarkness() {
    val result = EngineReducer.reduce(
      capturing(ready(context = EngineContext(darkUnknownStreak = 1))),
      EngineEvent.CaptureFinished(Attention.Unknown, emptyList(), 500L),
      13_000L,
    )
    assertEquals(0, result.snapshot.context.darkUnknownStreak)
  }

  @Test
  fun forcedYieldIgnoresNewDeadlinesUntilScreenOffOrTouch() {
    val result = EngineReducer.reduce(
      ready(context = EngineContext(forcedYieldPending = true), state = EngineState.Yielded(YieldReason.MaxHold)),
      EngineEvent.DeadlineUpdated(deadline),
      0L,
    )
    assertEquals(EngineState.Yielded(YieldReason.MaxHold), result.snapshot.state)
    assertTrue(result.effects.none { it is EngineEffect.ScheduleWake || it is EngineEffect.RunGates })
  }

  @Test
  fun backoffUsesTwoFramesAndTheEffectiveLead() {
    val start = ready(context = EngineContext(lookingStreak = 2))
    val armed = EngineReducer.reduce(start, EngineEvent.DeadlineUpdated(deadline), 0L).snapshot
    assertEquals(15_000L, assertIs<EngineState.Armed>(armed.state).wakeAt)
    val gating = EngineReducer.reduce(
      armed,
      EngineEvent.ScheduledWakeFired((armed.state as EngineState.Armed).wakeAt),
      15_000L,
    ).snapshot
    val captured = EngineReducer.reduce(gating, EngineEvent.GateResult(GateDecision.Proceed), 15_000L)
    assertEquals(2, captured.effects.filterIsInstance<EngineEffect.StartCapture>().single().plan.maxFrames)
  }

  @Test
  fun holdTimerReleasesAndRejectsLateCaptureAndOldTimer() {
    val active = capturing(ready(context = EngineContext(holdStartUptime = 100L)))
    val expired = EngineReducer.reduce(active, EngineEvent.HoldLimitReached(100L), 1_800_100L)
    assertEquals(EngineState.Yielded(YieldReason.MaxHold), expired.snapshot.state)
    assertTrue(EngineEffect.Release in expired.effects)
    val late = EngineReducer.reduce(
      expired.snapshot,
      EngineEvent.CaptureFinished(Attention.Looking, emptyList(), 10L),
      1_800_101L,
    )
    assertTrue(late.effects.none { it is EngineEffect.Extend })
    assertEquals(active, EngineReducer.reduce(active, EngineEvent.HoldLimitReached(99L), 200L).snapshot)
  }

  @Test
  fun onlyTwoDarkVotesAccumulateDarkFailures() {
    val frame = FrameVerdict.NotLooking(
      FrameVerdict.Reason.TooDark,
    )
    val result = EngineReducer.reduce(
      capturing(),
      EngineEvent.CaptureFinished(Attention.Unknown, listOf(frame, frame), 20L),
      13_000L,
    )
    assertEquals(1, result.snapshot.context.darkUnknownStreak)
  }

  @Test
  fun oldCaptureTokenCannotExtendANewRound() {
    val active = capturing()
    val stale = EngineReducer.reduce(
      active,
      EngineEvent.CaptureFinished(Attention.Looking, emptyList(), 10L, ExtendToken.Seq(6L)),
      13_000L,
    )
    assertEquals(active, stale.snapshot)
    assertTrue(stale.effects.isEmpty())
  }

  @Test
  fun independentExternalActivityResetsLimitAndCancelsOldCapture() {
    val held =
      ready(
        context = EngineContext(holdStartUptime = 100L, forcedYieldPending = true, lastExternalActivityUptime = 10L),
        state = EngineState.Yielded(YieldReason.MaxHold),
      )
    val touched = EngineReducer.reduce(
      held,
      EngineEvent.DeadlineUpdated(deadline.copy(lastExternalActivityUptime = 500L)),
      500L,
    )
    assertFalse(touched.snapshot.context.forcedYieldPending)
    assertEquals(null, touched.snapshot.context.holdStartUptime)
    assertTrue(EngineEffect.CancelCapture in touched.effects)
    assertTrue(EngineEffect.Release in touched.effects)
    assertIs<EngineState.Armed>(touched.snapshot.state)
  }

  @Test
  fun firstExtendSchedulesLimitAndOldWakeCannotStartNewWindow() {
    val extending = EngineReducer.reduce(
      capturing(),
      EngineEvent.CaptureFinished(Attention.Looking, emptyList(), 25L),
      13_000L,
    ).snapshot
    val done = EngineReducer.reduce(extending, EngineEvent.ExtendFinished(ExtendResult.Done), 13_001L)
    assertEquals(
      EngineEffect.ScheduleHoldLimit(13_001L, 1_813_001L),
      done.effects.filterIsInstance<EngineEffect.ScheduleHoldLimit>().single(),
    )
    val armed = EngineReducer.reduce(ready(), EngineEvent.DeadlineUpdated(deadline), 0L).snapshot
    assertEquals(armed, EngineReducer.reduce(armed, EngineEvent.ScheduledWakeFired(11_999L), 12_000L).snapshot)
  }

  @Test
  fun screenOffReleasesAndStopsCapture() {
    val transition = EngineReducer.reduce(capturing(), EngineEvent.ScreenOff, 13_000L)
    assertEquals(EngineState.ScreenOff, transition.snapshot.state)
    assertTrue(transition.effects.any { it is EngineEffect.CancelCapture })
    assertTrue(transition.effects.any { it is EngineEffect.Release })
  }

  @Test
  fun switchingBackendDuringCaptureCannotExtend() {
    val transition = EngineReducer.reduce(capturing(), EngineEvent.BackendSelected(BackendId.Shizuku), 13_000L)
    assertTrue(transition.effects.none { it is EngineEffect.Extend })
    assertTrue(transition.effects.any { it is EngineEffect.Release })
    assertIs<EngineState.Yielded>(transition.snapshot.state)
  }

  @Test
  fun staleExtendIsYieldedAndNeverMarkedExtended() {
    val extending = EngineReducer.reduce(
      capturing(),
      EngineEvent.CaptureFinished(Attention.Looking, emptyList(), 500L),
      13_000L,
    ).snapshot
    val transition = EngineReducer.reduce(extending, EngineEvent.ExtendFinished(ExtendResult.Stale), 13_050L)
    assertEquals(EngineState.Yielded(YieldReason.ExtendFailed), transition.snapshot.state)
    val record = transition.effects.filterIsInstance<EngineEffect.Record>().single().record
    assertFalse(record.extended)
    assertEquals(DecisionReason.StaleExtend, record.reason)
  }

  @Test
  fun cameraAbortNotifiesWithoutExtending() {
    val transition = EngineReducer.reduce(
      capturing(),
      EngineEvent.CaptureAborted(AbortReason.CameraInUse),
      13_000L,
    )
    assertTrue(transition.effects.any { it == EngineEffect.Notify(Alert.CameraUnavailable) })
    assertTrue(transition.effects.none { it is EngineEffect.Extend })
  }

  @Test
  fun missingCameraPermissionNotifiesWhenBackendSelected() {
    val snapshot = ready().copy(cameraPermissionGranted = false)
    val transition = EngineReducer.reduce(snapshot, EngineEvent.BackendSelected(BackendId.Accessibility), 0L)
    assertEquals(EngineState.Disabled, transition.snapshot.state)
    assertTrue(transition.effects.any { it == EngineEffect.Notify(Alert.CameraPermissionLost) })
  }

  @Test
  fun smallOrZeroLeadBackoffNeverIncreasesConfiguredLead() {
    val twoSecondLead = ready(
      config = GazeConfig(enabled = true, leadMillis = 2_000L),
      context = EngineContext(lookingStreak = 2),
    )
    val armedTwo = EngineReducer.reduce(twoSecondLead, EngineEvent.DeadlineUpdated(deadline), 0L).snapshot
    assertEquals(17_500L, assertIs<EngineState.Armed>(armedTwo.state).wakeAt)

    val zeroLead = ready(
      config = GazeConfig(enabled = true, leadMillis = 0L),
      context = EngineContext(lookingStreak = 2),
    )
    val armedZero = EngineReducer.reduce(
      zeroLead,
      EngineEvent.DeadlineUpdated(deadline.copy(screenDimAtUptime = 15_000L)),
      0L,
    ).snapshot
    assertEquals(15_000L, assertIs<EngineState.Armed>(armedZero.state).wakeAt)
  }

  @Test
  fun unlimitedMaxHoldDoesNotScheduleOrTriggerHoldLimit() {
    val config = GazeConfig(enabled = true, maxHoldMinutes = 0)
    val extending = EngineReducer.reduce(
      capturing(ready(config = config)),
      EngineEvent.CaptureFinished(Attention.Looking, emptyList(), 25L),
      13_000L,
    ).snapshot
    val done = EngineReducer.reduce(extending, EngineEvent.ExtendFinished(ExtendResult.Done), 13_001L)
    assertTrue(done.effects.none { it is EngineEffect.ScheduleHoldLimit })
    assertFalse(done.snapshot.context.forcedYieldPending)

    val longHeld = ready(
      config = config,
      context = EngineContext(holdStartUptime = 100L, lookingStreak = 5),
      state = EngineState.Gating(deadline),
    )
    val gated = EngineReducer.reduce(longHeld, EngineEvent.GateResult(GateDecision.Proceed), 99_999_999L)
    assertIs<EngineState.Capturing>(gated.snapshot.state)
    val timerIgnored = EngineReducer.reduce(gated.snapshot, EngineEvent.HoldLimitReached(100L), 99_999_999L)
    assertIs<EngineState.Capturing>(timerIgnored.snapshot.state)
  }
}
