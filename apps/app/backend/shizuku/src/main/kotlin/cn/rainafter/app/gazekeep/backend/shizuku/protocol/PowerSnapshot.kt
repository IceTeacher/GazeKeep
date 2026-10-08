package cn.rainafter.app.gazekeep.backend.shizuku.protocol

/** shell 侧只回传结构化电源状态，避免把原始 dumpsys 文本跨进程带回 App。 */
data class PowerSnapshot(
    val lastUserActivityUptime: Long?,
    val screenOffTimeoutMs: Int?,
    val dimDurationMs: Int?,
    val hasScreenWakeLock: Boolean,
    val interactive: Boolean,
)
