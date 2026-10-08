package cn.rainafter.app.gazekeep.core.policy

import cn.rainafter.app.gazekeep.core.model.GazeConfig

object HoldPolicy {
    fun markExtend(context: PolicyContext, now: Long, config: GazeConfig): PolicyContext {
        val start = context.holdStartUptime ?: now
        return context.copy(
            holdStartUptime = start,
            lookingStreak = context.lookingStreak + 1,
            forcedYieldPending = now - start >= config.maxHoldMinutes * 60_000L,
            darkUnknownStreak = 0,
        )
    }

    fun markNotLooking(context: PolicyContext): PolicyContext = context.copy(
        lookingStreak = 0,
        darkUnknownStreak = 0,
    )

    fun markUnknown(context: PolicyContext): PolicyContext = context.copy(
        lookingStreak = 0,
        darkUnknownStreak = context.darkUnknownStreak + 1,
    )

    fun markExternalActivity(context: PolicyContext, uptime: Long): PolicyContext = context.copy(
        lastExternalActivityUptime = uptime,
        holdStartUptime = null,
        lookingStreak = 0,
        forcedYieldPending = false,
    )
}

data class PolicyContext(
    val holdStartUptime: Long? = null,
    val lastExternalActivityUptime: Long? = null,
    val lookingStreak: Int = 0,
    val darkUnknownStreak: Int = 0,
    val forcedYieldPending: Boolean = false,
)

object LeadBackoff {
    data class CaptureSettings(val leadMillis: Long, val maxFrames: Int = 3)

    fun settings(config: GazeConfig, lookingStreak: Int): CaptureSettings = when {
        lookingStreak >= 2 -> CaptureSettings((config.leadMillis - 3_000L).coerceAtLeast(4_000L))
        else -> CaptureSettings(config.leadMillis)
    }
}
