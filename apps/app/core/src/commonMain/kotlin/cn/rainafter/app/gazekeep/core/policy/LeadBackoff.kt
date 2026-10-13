package cn.rainafter.app.gazekeep.core.policy

import cn.rainafter.app.gazekeep.core.model.GazeConfig

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
