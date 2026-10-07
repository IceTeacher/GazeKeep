package cn.rainafter.app.gazekeep.core.engine

import cn.rainafter.app.gazekeep.core.backend.ExtendResult
import cn.rainafter.app.gazekeep.core.backend.ExtendToken
import cn.rainafter.app.gazekeep.core.backend.TimeoutDeadline
import cn.rainafter.app.gazekeep.core.backend.captureWakeAtUptime
import cn.rainafter.app.gazekeep.core.model.Attention
import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.core.model.FrameVerdict
import cn.rainafter.app.gazekeep.core.model.GazeConfig
import cn.rainafter.app.gazekeep.core.model.Sensitivity
import cn.rainafter.app.gazekeep.core.policy.GateDecision
import cn.rainafter.app.gazekeep.core.policy.SkipReason

sealed interface EngineState {
    data object Disabled : EngineState
    data object ScreenOff : EngineState
    data class Armed(val deadline: TimeoutDeadline, val wakeAt: Long) : EngineState
    data class Gating(val deadline: TimeoutDeadline) : EngineState
    data class Capturing(val deadline: TimeoutDeadline, val abortAt: Long) : EngineState
    data class Extending(val deadline: TimeoutDeadline) : EngineState
    data class Yielded(val reason: YieldReason) : EngineState
}

sealed interface YieldReason {
    data object WaitingDeadline : YieldReason
    data object TooLate : YieldReason
    data object MaxHold : YieldReason
    data object ExtendFailed : YieldReason
    data object CameraPermission : YieldReason
    data object BackendUnavailable : YieldReason
    data class Gate(val reason: SkipReason) : YieldReason
    data class NotLooking(val attention: Attention) : YieldReason
    data class Aborted(val reason: AbortReason) : YieldReason
}

enum class AbortReason { Deadline, CameraInUse, CameraDisabled, Cancelled, PermissionLost, Error }

sealed interface EngineEvent {
    data class ConfigChanged(val config: GazeConfig) : EngineEvent
    data class BackendSelected(val backend: BackendId?) : EngineEvent
    data object ScreenOn : EngineEvent
    data object ScreenOff : EngineEvent
    data object KeyguardDismissed : EngineEvent
    data class DeadlineUpdated(val deadline: TimeoutDeadline) : EngineEvent
    data class UserActivity(val uptime: Long) : EngineEvent
    data object WakeTimerFired : EngineEvent
    data class GateResult(val result: GateDecision) : EngineEvent
    data class CaptureFinished(
        val attention: Attention,
        val frames: List<FrameVerdict>,
        val latencyMs: Long,
    ) : EngineEvent
    data class CaptureAborted(val reason: AbortReason) : EngineEvent
    data class ExtendFinished(val result: ExtendResult) : EngineEvent
    data object CameraPermissionLost : EngineEvent
}

data class CapturePlan(
    val maxFrames: Int = 3,
    val frameIntervalMs: Long = 150L,
    val abortAtUptime: Long,
    val sensitivity: Sensitivity,
)

sealed interface EngineEffect {
    data class ScheduleWake(val atUptime: Long) : EngineEffect
    data object CancelWake : EngineEffect
    data class RunGates(val deadline: TimeoutDeadline) : EngineEffect
    data class StartCapture(val plan: CapturePlan) : EngineEffect
    data object CancelCapture : EngineEffect
    data class Extend(val token: ExtendToken) : EngineEffect
    data object Release : EngineEffect
    data class Record(val record: DecisionRecord) : EngineEffect
    data class Notify(val alert: Alert) : EngineEffect
}

enum class Alert { BackendUnavailable, CameraPermissionLost, CameraUnavailable, BackendFailed }

enum class DecisionReason {
    GateSkipped,
    TooLate,
    Looking,
    NotLooking,
    Unknown,
    Aborted,
    Extended,
    StaleExtend,
    FailedExtend,
    MaxHold,
}

data class DecisionRecord(
    val uptime: Long,
    val foregroundPackage: String?,
    val attention: Attention?,
    val reason: DecisionReason,
    val backend: BackendId?,
    val extended: Boolean,
    val latencyMs: Long?,
)

data class EngineContext(
    val holdStartUptime: Long? = null,
    val lastExternalActivityUptime: Long? = null,
    val lookingStreak: Int = 0,
    val darkUnknownStreak: Int = 0,
    val forcedYieldPending: Boolean = false,
    val extendFailures: Int = 0,
    val foregroundPackage: String? = null,
)

data class EngineSnapshot(
    val state: EngineState = EngineState.Disabled,
    val context: EngineContext = EngineContext(),
    val config: GazeConfig = GazeConfig(),
    val backend: BackendId? = null,
    val cameraPermissionGranted: Boolean = true,
)

data class Transition(val snapshot: EngineSnapshot, val effects: List<EngineEffect> = emptyList())

/**
 * 引擎唯一修改状态的入口；Android 侧只负责把副作用结果重新投递成事件。
 */
object EngineReducer {
    private const val MIN_CAPTURE_WINDOW_MS = 2_500L

    fun reduce(snapshot: EngineSnapshot, event: EngineEvent, now: Long): Transition {
        return when (event) {
            is EngineEvent.ConfigChanged -> configChanged(snapshot, event.config, now)
            is EngineEvent.BackendSelected -> backendChanged(snapshot, event.backend, now)
            EngineEvent.ScreenOff -> screenOff(snapshot)
            EngineEvent.ScreenOn, EngineEvent.KeyguardDismissed -> screenOn(snapshot)
            is EngineEvent.DeadlineUpdated -> deadlineUpdated(snapshot, event.deadline, now)
            EngineEvent.WakeTimerFired -> wakeTimer(snapshot)
            is EngineEvent.UserActivity -> userActivity(snapshot, event.uptime)
            is EngineEvent.GateResult -> gateResult(snapshot, event.result, now)
            is EngineEvent.CaptureFinished -> captureFinished(snapshot, event, now)
            is EngineEvent.CaptureAborted -> captureAborted(snapshot, event.reason, now)
            is EngineEvent.ExtendFinished -> extendFinished(snapshot, event.result, now)
            EngineEvent.CameraPermissionLost -> disabled(snapshot, Alert.CameraPermissionLost)
        }
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
            val effects = if (snapshot.state is EngineState.Capturing || snapshot.state is EngineState.Extending) {
                listOf(EngineEffect.CancelCapture, EngineEffect.CancelWake, EngineEffect.Release)
            } else {
                emptyList()
            }
            Transition(updated.copy(state = EngineState.Yielded(YieldReason.WaitingDeadline)), effects)
        }
    }

    private fun screenOff(snapshot: EngineSnapshot): Transition = Transition(
        snapshot.copy(state = EngineState.ScreenOff, context = snapshot.context.copy(holdStartUptime = null)),
        listOf(EngineEffect.CancelCapture, EngineEffect.CancelWake, EngineEffect.Release),
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
        if (snapshot.state is EngineState.Disabled || snapshot.state is EngineState.ScreenOff) return Transition(snapshot)
        val remaining = deadline.screenOffAtUptime - now
        // 保留真实熄屏截止时间，只将检测提前到降亮之前。
        val wakeAt = deadline.captureWakeAtUptime(snapshot.config.leadMillis)
        val updated = snapshot.copy(context = snapshot.context.copy(foregroundPackage = deadline.foregroundPackage))
        return when {
            wakeAt > now -> {
                Transition(updated.copy(state = EngineState.Armed(deadline, wakeAt)), listOf(EngineEffect.ScheduleWake(wakeAt)))
            }
            remaining > MIN_CAPTURE_WINDOW_MS -> {
                Transition(updated.copy(state = EngineState.Gating(deadline)), listOf(EngineEffect.RunGates(deadline)))
            }
            else -> Transition(
                updated.copy(state = EngineState.Yielded(YieldReason.TooLate)),
                listOf(EngineEffect.Record(updated.record(now, DecisionReason.TooLate, null, false, null))),
            )
        }
    }

    private fun wakeTimer(snapshot: EngineSnapshot): Transition = when (val state = snapshot.state) {
        is EngineState.Armed -> Transition(snapshot.copy(state = EngineState.Gating(state.deadline)), listOf(EngineEffect.RunGates(state.deadline)))
        else -> Transition(snapshot)
    }

    private fun userActivity(snapshot: EngineSnapshot, uptime: Long): Transition {
        if (snapshot.state is EngineState.ScreenOff || snapshot.state is EngineState.Disabled) {
            return Transition(snapshot.copy(context = snapshot.context.copy(lastExternalActivityUptime = uptime, holdStartUptime = null)))
        }
        val context = snapshot.context.copy(
            lastExternalActivityUptime = uptime,
            holdStartUptime = null,
            lookingStreak = 0,
            forcedYieldPending = false,
        )
        return Transition(
            snapshot.copy(state = EngineState.Yielded(YieldReason.WaitingDeadline), context = context),
            listOf(EngineEffect.CancelCapture, EngineEffect.CancelWake),
        )
    }

    private fun gateResult(snapshot: EngineSnapshot, result: GateDecision, now: Long): Transition {
        val state = snapshot.state
        if (state !is EngineState.Gating) return Transition(snapshot)
        if (result is GateDecision.Skip) {
            val next = snapshot.copy(state = EngineState.Yielded(YieldReason.Gate(result.reason)))
            return Transition(next, listOf(EngineEffect.Record(next.record(now, DecisionReason.GateSkipped, null, false, null))))
        }
        if (snapshot.context.forcedYieldPending) {
            val next = snapshot.copy(
                state = EngineState.Yielded(YieldReason.MaxHold),
                context = snapshot.context.copy(forcedYieldPending = false),
            )
            return Transition(next, listOf(EngineEffect.Record(next.record(now, DecisionReason.MaxHold, null, false, null))))
        }
        val settings = cn.rainafter.app.gazekeep.core.policy.LeadBackoff.settings(
            snapshot.config,
            snapshot.context.lookingStreak,
        )
        val abortAt = minOf(state.deadline.screenOffAtUptime - 200L, now + 1_600L)
        val plan = CapturePlan(settings.maxFrames, 150L, abortAt, snapshot.config.sensitivity)
        return Transition(snapshot.copy(state = EngineState.Capturing(state.deadline, abortAt)), listOf(EngineEffect.StartCapture(plan)))
    }

    private fun captureFinished(snapshot: EngineSnapshot, event: EngineEvent.CaptureFinished, now: Long): Transition {
        val state = snapshot.state
        if (state !is EngineState.Capturing) return Transition(snapshot)
        if (event.attention == Attention.Looking) {
            return Transition(snapshot.copy(state = EngineState.Extending(state.deadline)), listOf(EngineEffect.Extend(state.deadline.token)))
        }
        val context = if (event.attention == Attention.Unknown) {
            snapshot.context.copy(lookingStreak = 0, darkUnknownStreak = snapshot.context.darkUnknownStreak + 1)
        } else {
            snapshot.context.copy(lookingStreak = 0, darkUnknownStreak = 0)
        }
        val next = snapshot.copy(state = EngineState.Yielded(YieldReason.NotLooking(event.attention)), context = context)
        val reason = if (event.attention == Attention.Unknown) DecisionReason.Unknown else DecisionReason.NotLooking
        return Transition(
            next,
            listOf(
                EngineEffect.Release,
                EngineEffect.Record(next.record(now, reason, event.attention, false, event.latencyMs)),
            ),
        )
    }

    private fun captureAborted(snapshot: EngineSnapshot, reason: AbortReason, now: Long): Transition {
        if (snapshot.state !is EngineState.Capturing) return Transition(snapshot)
        val next = snapshot.copy(state = EngineState.Yielded(YieldReason.Aborted(reason)))
        val effects = buildList {
            add(EngineEffect.Release)
            add(EngineEffect.Record(next.record(now, DecisionReason.Aborted, null, false, null)))
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
                Transition(next, listOf(EngineEffect.Record(next.record(now, DecisionReason.Extended, Attention.Looking, true, null))))
            }
            ExtendResult.Stale -> {
                val next = snapshot.copy(state = EngineState.Yielded(YieldReason.ExtendFailed))
                Transition(next, listOf(EngineEffect.Record(next.record(now, DecisionReason.StaleExtend, Attention.Looking, false, null))))
            }
            is ExtendResult.Failed -> {
                val next = snapshot.copy(
                    state = EngineState.Yielded(YieldReason.ExtendFailed),
                    context = snapshot.context.copy(extendFailures = snapshot.context.extendFailures + 1),
                )
                val effects = buildList {
                    add(EngineEffect.Record(next.record(now, DecisionReason.FailedExtend, Attention.Looking, false, null)))
                    if (next.context.extendFailures >= 2) add(EngineEffect.Notify(Alert.BackendFailed))
                }
                Transition(next, effects)
            }
        }
    }

    private fun disabled(snapshot: EngineSnapshot, alert: Alert?): Transition {
        val next = snapshot.copy(state = EngineState.Disabled)
        val effects = buildList {
            add(EngineEffect.CancelCapture)
            add(EngineEffect.CancelWake)
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
