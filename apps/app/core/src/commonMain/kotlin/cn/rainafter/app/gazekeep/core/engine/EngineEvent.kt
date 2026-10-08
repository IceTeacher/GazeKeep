package cn.rainafter.app.gazekeep.core.engine

import cn.rainafter.app.gazekeep.core.backend.ExtendResult
import cn.rainafter.app.gazekeep.core.backend.TimeoutDeadline
import cn.rainafter.app.gazekeep.core.model.Attention
import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.core.model.FrameVerdict
import cn.rainafter.app.gazekeep.core.model.GazeConfig
import cn.rainafter.app.gazekeep.core.policy.GateDecision

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
  data class CaptureFinished(val attention: Attention, val frames: List<FrameVerdict>, val latencyMs: Long) :
    EngineEvent
  data class CaptureAborted(val reason: AbortReason) : EngineEvent
  data class ExtendFinished(val result: ExtendResult) : EngineEvent
  data object CameraPermissionLost : EngineEvent
}
