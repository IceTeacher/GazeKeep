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
    data class RunGates(val deadline: TimeoutDeadline) : EngineEffect
    data class StartCapture(val plan: CapturePlan) : EngineEffect
    data object CancelCapture : EngineEffect
    data class Extend(val token: ExtendToken) : EngineEffect
    data object Release : EngineEffect
    data class Record(val record: DecisionRecord) : EngineEffect
    data class Notify(val alert: Alert) : EngineEffect
}
