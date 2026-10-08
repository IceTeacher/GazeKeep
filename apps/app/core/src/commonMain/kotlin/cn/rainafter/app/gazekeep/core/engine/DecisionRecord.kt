package cn.rainafter.app.gazekeep.core.engine

import cn.rainafter.app.gazekeep.core.model.Attention
import cn.rainafter.app.gazekeep.core.model.BackendId

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
