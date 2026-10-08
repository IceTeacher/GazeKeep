package cn.rainafter.app.gazekeep.core.engine

import cn.rainafter.app.gazekeep.core.backend.TimeoutDeadline
import cn.rainafter.app.gazekeep.core.model.Attention
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
