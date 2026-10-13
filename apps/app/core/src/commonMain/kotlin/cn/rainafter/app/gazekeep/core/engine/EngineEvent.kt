package cn.rainafter.app.gazekeep.core.engine

import cn.rainafter.app.gazekeep.core.backend.ExtendResult
import cn.rainafter.app.gazekeep.core.backend.ExtendToken
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
  data class ScheduledWakeFired(val atUptime: Long) : EngineEvent
  data class HoldLimitReached(val startUptime: Long) : EngineEvent
  data class GateResult(
    val result: GateDecision,
    val foregroundPackage: String? = null,
    val deadline: TimeoutDeadline? = null,
  ) : EngineEvent
  data class CaptureFinished(
    val attention: Attention,
    val frames: List<FrameVerdict>,
    val latencyMs: Long,
    val token: ExtendToken? = null,
  ) : EngineEvent
  data class CaptureAborted(val reason: AbortReason, val token: ExtendToken? = null, val latencyMs: Long? = null) :
    EngineEvent
  data class ExtendFinished(val result: ExtendResult, val token: ExtendToken? = null) : EngineEvent
  data object CameraPermissionLost : EngineEvent
}
