package cn.rainafter.app.gazekeep.core.engine

import cn.rainafter.app.gazekeep.core.backend.ExtendResult
import cn.rainafter.app.gazekeep.core.backend.TimeoutDeadline
import cn.rainafter.app.gazekeep.core.backend.captureWakeAtUptime
import cn.rainafter.app.gazekeep.core.model.Attention
import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.core.model.FrameVerdict
import cn.rainafter.app.gazekeep.core.model.GazeConfig
import cn.rainafter.app.gazekeep.core.policy.GateDecision
import cn.rainafter.app.gazekeep.core.policy.LeadBackoff

/**
 * 引擎唯一修改状态的入口；Android 侧只负责把副作用结果重新投递成事件。
 */
object EngineReducer {
  private const val MIN_CAPTURE_WINDOW_MS = 2_500L

  fun reduce(snapshot: EngineSnapshot, event: EngineEvent, now: Long): Transition = when (event) {
    is EngineEvent.ConfigChanged -> configChanged(snapshot, event.config, now)

    is EngineEvent.BackendSelected -> backendChanged(snapshot, event.backend, now)

    EngineEvent.ScreenOff -> screenOff(snapshot)

    EngineEvent.ScreenOn, EngineEvent.KeyguardDismissed -> screenOn(snapshot)

    is EngineEvent.DeadlineUpdated -> deadlineUpdated(snapshot, event.deadline, now)

    EngineEvent.WakeTimerFired -> wakeTimer(snapshot)

    is EngineEvent.ScheduledWakeFired -> if ((snapshot.state as? EngineState.Armed)?.wakeAt ==
      event.atUptime
    ) {
      wakeTimer(snapshot)
    } else {
      Transition(snapshot)
    }

    is EngineEvent.HoldLimitReached -> if (snapshot.context.holdStartUptime ==
      event.startUptime
    ) {
      maxHold(snapshot, now)
    } else {
      Transition(snapshot)
    }

    is EngineEvent.UserActivity -> userActivity(snapshot, event.uptime)

    is EngineEvent.GateResult -> if (event.deadline != null &&
      (snapshot.state as? EngineState.Gating)?.deadline != event.deadline
    ) {
      Transition(snapshot)
    } else {
      gateResult(
        snapshot.copy(context = snapshot.context.copy(foregroundPackage = event.foregroundPackage)),
        event.result,
        now,
      )
    }

    is EngineEvent.CaptureFinished -> captureFinished(snapshot, event, now)

    is EngineEvent.CaptureAborted -> if (event.token != null &&
      (snapshot.state as? EngineState.Capturing)?.deadline?.token != event.token
    ) {
      Transition(snapshot)
    } else {
      captureAborted(snapshot, event.reason, now, event.latencyMs)
    }

    is EngineEvent.ExtendFinished -> if (event.token != null &&
      (snapshot.state as? EngineState.Extending)?.deadline?.token != event.token
    ) {
      Transition(snapshot)
    } else {
      extendFinished(snapshot, event.result, now)
    }

    EngineEvent.CameraPermissionLost -> disabled(snapshot, Alert.CameraPermissionLost)
  }

  private fun configChanged(snapshot: EngineSnapshot, config: GazeConfig, now: Long): Transition {
    val updated = snapshot.copy(config = config)
    return if (!config.enabled || !updated.cameraPermissionGranted || updated.backend == null) {
      disabled(
        updated,
        when {
          updated.backend == null -> Alert.BackendUnavailable
          !updated.cameraPermissionGranted -> Alert.CameraPermissionLost
          else -> null
        },
      )
    } else {
      Transition(updated)
    }
  }

  private fun backendChanged(snapshot: EngineSnapshot, backend: BackendId?, now: Long): Transition {
    val updated = snapshot.copy(backend = backend)
    return if (backend == null || !updated.config.enabled || !updated.cameraPermissionGranted) {
      disabled(
        updated,
        when {
          backend == null -> Alert.BackendUnavailable
          !updated.cameraPermissionGranted -> Alert.CameraPermissionLost
          else -> null
        },
      )
    } else {
      val effects =
        listOf(EngineEffect.CancelCapture, EngineEffect.CancelWake, EngineEffect.CancelHoldLimit, EngineEffect.Release)
      Transition(
        updated.copy(
          state = EngineState.Yielded(YieldReason.WaitingDeadline),
          context = updated.context.copy(holdStartUptime = null, lookingStreak = 0, darkUnknownStreak = 0),
        ),
        effects,
      )
    }
  }

  private fun screenOff(snapshot: EngineSnapshot): Transition = Transition(
    snapshot.copy(state = EngineState.ScreenOff, context = EngineContext()),
    listOf(EngineEffect.CancelCapture, EngineEffect.CancelWake, EngineEffect.CancelHoldLimit, EngineEffect.Release),
  )

  private fun screenOn(snapshot: EngineSnapshot): Transition {
    if (snapshot.state !is EngineState.ScreenOff) return Transition(snapshot)
    if (!snapshot.config.enabled || snapshot.backend == null || !snapshot.cameraPermissionGranted) {
      return Transition(snapshot.copy(state = EngineState.Disabled))
    }
    return Transition(
      snapshot.copy(
        state = EngineState.Yielded(YieldReason.WaitingDeadline),
        context = snapshot.context.copy(holdStartUptime = null),
      ),
    )
  }

  private fun deadlineUpdated(snapshot: EngineSnapshot, deadline: TimeoutDeadline, now: Long): Transition {
    if (snapshot.state is EngineState.Disabled ||
      snapshot.state is EngineState.ScreenOff
    ) {
      return Transition(snapshot)
    }
    val external = deadline.lastExternalActivityUptime
    if (external != null && external > (snapshot.context.lastExternalActivityUptime ?: -1L)) {
      val activity = userActivity(snapshot, external)
      val next = deadlineUpdated(activity.snapshot, deadline, now)
      return next.copy(effects = activity.effects + next.effects)
    }
    if (snapshot.context.forcedYieldPending) return maxHold(snapshot, now)
    val remaining = deadline.screenOffAtUptime - now
    // 保留真实熄屏截止时间，只将检测提前到降亮之前。
    val wakeAt = deadline.captureWakeAtUptime(
      LeadBackoff.settings(
        snapshot.config,
        snapshot.context.lookingStreak,
      ).leadMillis,
    )
    val updated = snapshot.copy(context = snapshot.context.copy(foregroundPackage = deadline.foregroundPackage))
    return when {
      wakeAt > now -> {
        Transition(
          updated.copy(state = EngineState.Armed(deadline, wakeAt)),
          listOf(EngineEffect.ScheduleWake(wakeAt)),
        )
      }

      remaining > MIN_CAPTURE_WINDOW_MS -> {
        Transition(updated.copy(state = EngineState.Gating(deadline)), listOf(EngineEffect.RunGates(deadline)))
      }

      else -> Transition(
        updated.copy(state = EngineState.Yielded(YieldReason.TooLate)),
        listOf(
          EngineEffect.CancelCapture,
          EngineEffect.Release,
          EngineEffect.Record(updated.record(now, DecisionReason.TooLate, null, false, null)),
        ),
      )
    }
  }

  private fun wakeTimer(snapshot: EngineSnapshot): Transition = when (val state = snapshot.state) {
    is EngineState.Armed -> Transition(
      snapshot.copy(state = EngineState.Gating(state.deadline)),
      listOf(EngineEffect.RunGates(state.deadline)),
    )

    else -> Transition(snapshot)
  }

  private fun userActivity(snapshot: EngineSnapshot, uptime: Long): Transition {
    if (snapshot.state is EngineState.ScreenOff || snapshot.state is EngineState.Disabled) {
      return Transition(
        snapshot.copy(
          context = snapshot.context.copy(lastExternalActivityUptime = uptime, holdStartUptime = null),
        ),
      )
    }
    val context = snapshot.context.copy(
      lastExternalActivityUptime = uptime,
      holdStartUptime = null,
      lookingStreak = 0,
      darkUnknownStreak = 0,
      forcedYieldPending = false,
    )
    return Transition(
      snapshot.copy(state = EngineState.Yielded(YieldReason.WaitingDeadline), context = context),
      listOf(EngineEffect.CancelCapture, EngineEffect.CancelWake, EngineEffect.CancelHoldLimit, EngineEffect.Release),
    )
  }

  private fun gateResult(snapshot: EngineSnapshot, result: GateDecision, now: Long): Transition {
    val state = snapshot.state
    if (state !is EngineState.Gating) return Transition(snapshot)
    if (result is GateDecision.Skip) {
      val next = snapshot.copy(state = EngineState.Yielded(YieldReason.Gate(result.reason)))
      return Transition(
        next,
        listOf(
          EngineEffect.Release,
          EngineEffect.Record(
            next.record(now, DecisionReason.GateSkipped, null, false, null).copy(skipReason = result.reason),
          ),
        ),
      )
    }
    if (snapshot.context.forcedYieldPending || holdExpired(snapshot, now)) return maxHold(snapshot, now)
    val settings = LeadBackoff.settings(
      snapshot.config,
      snapshot.context.lookingStreak,
    )
    val abortAt = minOf(state.deadline.screenOffAtUptime - 200L, now + 1_600L)
    val plan = CapturePlan(settings.maxFrames, 150L, abortAt, snapshot.config.sensitivity)
    return Transition(
      snapshot.copy(state = EngineState.Capturing(state.deadline, abortAt)),
      listOf(EngineEffect.StartCapture(plan)),
    )
  }

  private fun captureFinished(snapshot: EngineSnapshot, event: EngineEvent.CaptureFinished, now: Long): Transition {
    val state = snapshot.state
    if (state !is EngineState.Capturing ||
      (event.token != null && state.deadline.token != event.token)
    ) {
      return Transition(snapshot)
    }
    if (holdExpired(snapshot, now)) return maxHold(snapshot, now)
    if (event.attention == Attention.Looking) {
      return Transition(
        snapshot.copy(
          state = EngineState.Extending(state.deadline),
          context = snapshot.context.copy(captureLatencyMs = event.latencyMs),
        ),
        listOf(EngineEffect.Extend(state.deadline.token)),
      )
    }
    val dark = event.attention == Attention.Unknown && event.frames.count {
      it is FrameVerdict.NotLooking &&
        it.reason == FrameVerdict.Reason.TooDark
    } >= 2
    val context = if (dark) {
      snapshot.context.copy(lookingStreak = 0, darkUnknownStreak = snapshot.context.darkUnknownStreak + 1)
    } else {
      snapshot.context.copy(lookingStreak = 0, darkUnknownStreak = 0)
    }
    val next = snapshot.copy(
      state = EngineState.Yielded(YieldReason.NotLooking(event.attention)),
      context = context,
    )
    val reason = if (event.attention == Attention.Unknown) DecisionReason.Unknown else DecisionReason.NotLooking
    return Transition(
      next,
      listOf(
        EngineEffect.Release,
        EngineEffect.Record(
          next.record(
            now,
            reason,
            event.attention,
            false,
            event.latencyMs,
          ).copy(
            frameReason = event.frames.filterIsInstance<FrameVerdict.NotLooking>().firstOrNull()?.reason,
          ),
        ),
      ),
    )
  }

  private fun captureAborted(
    snapshot: EngineSnapshot,
    reason: AbortReason,
    now: Long,
    latencyMs: Long? = null,
  ): Transition {
    if (snapshot.state !is EngineState.Capturing) return Transition(snapshot)
    val next = snapshot.copy(state = EngineState.Yielded(YieldReason.Aborted(reason)))
    val effects = buildList {
      add(EngineEffect.Release)
      add(
        EngineEffect.Record(
          next.record(now, DecisionReason.Aborted, null, false, latencyMs).copy(abortReason = reason),
        ),
      )
      if (reason == AbortReason.CameraInUse ||
        reason == AbortReason.CameraDisabled ||
        reason == AbortReason.PermissionLost ||
        reason == AbortReason.Error
      ) {
        add(EngineEffect.Notify(Alert.CameraUnavailable))
      }
    }
    return Transition(next, effects)
  }

  private fun extendFinished(snapshot: EngineSnapshot, result: ExtendResult, now: Long): Transition {
    if (snapshot.state !is EngineState.Extending) return Transition(snapshot)
    return when (result) {
      ExtendResult.Done -> {
        val start = snapshot.context.holdStartUptime ?: now
        val forced = now - start >= snapshot.config.maxHoldMinutes * 60_000L
        val context = snapshot.context.copy(
          holdStartUptime = start,
          lookingStreak = snapshot.context.lookingStreak + 1,
          darkUnknownStreak = 0,
          forcedYieldPending = forced,
          extendFailures = 0,
        )
        val next = snapshot.copy(state = EngineState.Yielded(YieldReason.WaitingDeadline), context = context)
        Transition(
          next,
          buildList {
            if (snapshot.context.holdStartUptime ==
              null
            ) {
              add(EngineEffect.ScheduleHoldLimit(start, start + snapshot.config.maxHoldMinutes * 60_000L))
            }
            add(
              EngineEffect.Record(
                next.record(now, DecisionReason.Extended, Attention.Looking, true, snapshot.context.captureLatencyMs),
              ),
            )
          },
        )
      }

      ExtendResult.Stale -> {
        val next = snapshot.copy(state = EngineState.Yielded(YieldReason.ExtendFailed))
        Transition(
          next,
          listOf(
            EngineEffect.Release,
            EngineEffect.Record(next.record(now, DecisionReason.StaleExtend, Attention.Looking, false, null)),
          ),
        )
      }

      is ExtendResult.Failed -> {
        val next = snapshot.copy(
          state = EngineState.Yielded(YieldReason.ExtendFailed),
          context = snapshot.context.copy(extendFailures = snapshot.context.extendFailures + 1),
        )
        val effects = buildList {
          add(EngineEffect.Release)
          add(
            EngineEffect.Record(next.record(now, DecisionReason.FailedExtend, Attention.Looking, false, null)),
          )
          if (next.context.extendFailures >= 2) add(EngineEffect.Notify(Alert.BackendFailed))
        }
        Transition(next, effects)
      }
    }
  }

  private fun holdExpired(snapshot: EngineSnapshot, now: Long): Boolean =
    snapshot.context.holdStartUptime?.let { now - it >= snapshot.config.maxHoldMinutes * 60_000L } == true

  private fun maxHold(snapshot: EngineSnapshot, now: Long): Transition {
    val next = snapshot.copy(
      state = EngineState.Yielded(YieldReason.MaxHold),
      context = snapshot.context.copy(forcedYieldPending = true),
    )
    return Transition(
      next,
      listOf(
        EngineEffect.CancelCapture,
        EngineEffect.CancelWake,
        EngineEffect.CancelHoldLimit,
        EngineEffect.Release,
        EngineEffect.Record(next.record(now, DecisionReason.MaxHold, null, false, null)),
      ),
    )
  }

  private fun disabled(snapshot: EngineSnapshot, alert: Alert?): Transition {
    val next = snapshot.copy(state = EngineState.Disabled)
    val effects = buildList {
      add(EngineEffect.CancelCapture)
      add(EngineEffect.CancelWake)
      add(EngineEffect.CancelHoldLimit)
      add(EngineEffect.Release)
      if (alert != null) add(EngineEffect.Notify(alert))
    }
    return Transition(next, effects)
  }

  private fun EngineSnapshot.record(
    now: Long,
    reason: DecisionReason,
    attention: Attention?,
    extended: Boolean,
    latencyMs: Long?,
  ): DecisionRecord = DecisionRecord(now, context.foregroundPackage, attention, reason, backend, extended, latencyMs)
}
