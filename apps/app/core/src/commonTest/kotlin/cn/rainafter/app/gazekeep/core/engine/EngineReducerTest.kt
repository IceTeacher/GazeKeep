package cn.rainafter.app.gazekeep.core.engine

import cn.rainafter.app.gazekeep.core.backend.ExtendResult
import cn.rainafter.app.gazekeep.core.backend.ExtendToken
import cn.rainafter.app.gazekeep.core.backend.TimeoutDeadline
import cn.rainafter.app.gazekeep.core.model.Attention
import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.core.model.GazeConfig
import cn.rainafter.app.gazekeep.core.policy.GateDecision
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
    val gating = EngineReducer.reduce(armed, EngineEvent.WakeTimerFired, 12_000L).snapshot
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
}
