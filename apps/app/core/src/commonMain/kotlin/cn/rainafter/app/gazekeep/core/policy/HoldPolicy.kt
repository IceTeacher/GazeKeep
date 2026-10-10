package cn.rainafter.app.gazekeep.core.policy

import cn.rainafter.app.gazekeep.core.model.GazeConfig

object HoldPolicy {
  fun markExtend(context: PolicyContext, now: Long, config: GazeConfig): PolicyContext {
    val start = context.holdStartUptime ?: now
    val maxHoldMs = config.maxHoldMinutes * 60_000L
    return context.copy(
      holdStartUptime = start,
      lookingStreak = context.lookingStreak + 1,
      forcedYieldPending = maxHoldMs > 0L && now - start >= maxHoldMs,
      darkUnknownStreak = 0,
    )
  }

  fun markNotLooking(context: PolicyContext): PolicyContext = context.copy(
    lookingStreak = 0,
    darkUnknownStreak = 0,
  )

  fun markUnknown(context: PolicyContext, isDark: Boolean = false): PolicyContext = context.copy(
    lookingStreak = 0,
    darkUnknownStreak = if (isDark) context.darkUnknownStreak + 1 else 0,
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
    lookingStreak >= 2 -> CaptureSettings(
      leadMillis = minOf(config.leadMillis, (config.leadMillis - 3_000L).coerceAtLeast(4_000L)),
      maxFrames = 2,
    )

    else -> CaptureSettings(config.leadMillis)
  }
}
