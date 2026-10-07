package cn.rainafter.app.gazekeep.backend.shizuku.oracle

import cn.rainafter.app.gazekeep.backend.shizuku.protocol.PowerSnapshot
object PowerDumpParser {
    // HyperOS 在字段名后追加了 (excludingAttention)，AOSP 则没有括号后缀。
    private val lastActivity = Regex("mLastUserActivityTime(?:\\([^)]*\\))?\\s*=\\s*(\\d+)")
    private val timeout = Regex("mScreenOffTimeoutSetting\\s*=\\s*(\\d+)")
    private val effectiveTimeout = Regex("Screen off timeout\\s*[:=]\\s*(\\d+)")
    private val dim = Regex("(?:Screen dim duration|mScreenDimDuration)\\s*[:=]\\s*(\\d+)")

    fun parse(dump: String): PowerSnapshot {
        val wakeLock = dump.contains("SCREEN_BRIGHT_WAKE_LOCK") ||
            dump.contains("FULL_WAKE_LOCK") ||
            dump.contains("WAKE_LOCK_STAY_AWAKE") ||
            dump.contains("WAKE_LOCK_SCREEN_BRIGHT")
        val interactive = Regex("mWakefulness\\s*=\\s*(Awake|Dreaming)").containsMatchIn(dump) ||
            Regex("mInteractive\\s*=\\s*true").containsMatchIn(dump)
        return PowerSnapshot(
            lastUserActivityUptime = lastActivity.find(dump)?.groupValues?.get(1)?.toLongOrNull(),
            screenOffTimeoutMs = effectiveTimeout.find(dump)?.groupValues?.get(1)?.toIntOrNull()
                ?: timeout.find(dump)?.groupValues?.get(1)?.toIntOrNull(),
            dimDurationMs = dim.find(dump)?.groupValues?.get(1)?.toIntOrNull(),
            hasScreenWakeLock = wakeLock,
            interactive = interactive,
        )
    }
}
