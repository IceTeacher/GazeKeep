package cn.rainafter.app.gazekeep.core.engine

import cn.rainafter.app.gazekeep.core.backend.ExtendToken
import cn.rainafter.app.gazekeep.core.backend.TimeoutDeadline
import cn.rainafter.app.gazekeep.core.model.Sensitivity

data class CapturePlan(
  val maxFrames: Int = 3,
  val frameIntervalMs: Long = 150L,
  val abortAtUptime: Long,
  val sensitivity: Sensitivity,
)

sealed interface EngineEffect {
  data class ScheduleWake(val atUptime: Long) : EngineEffect
  data object CancelWake : EngineEffect
  data class ScheduleHoldLimit(val startUptime: Long, val atUptime: Long) : EngineEffect
  data object CancelHoldLimit : EngineEffect
  data class RunGates(val deadline: TimeoutDeadline) : EngineEffect
  data class StartCapture(val plan: CapturePlan) : EngineEffect
  data object CancelCapture : EngineEffect
  data class Extend(val token: ExtendToken) : EngineEffect
  data object Release : EngineEffect

  /** 放弃保亮的释放：后端须立即恢复临时设置，不得推迟到之后的熄屏释放。 */
  data object ForceRelease : EngineEffect
  data class Record(val record: DecisionRecord) : EngineEffect
  data class Notify(val alert: Alert) : EngineEffect
}
