package cn.rainafter.app.gazekeep.backend.shizuku.restore

import cn.rainafter.app.gazekeep.backend.shizuku.protocol.PowerSnapshot

object TimeoutRestorePolicy {
  // 覆盖默认 8 s 检测提前量与一次采集；剩余更短时引擎来不及检测，只能按 TooLate 放弃。
  private const val MIN_REMAINING_MS = 10_000L

  /**
   * 亮屏时写回原值，系统会按上次用户活动重新计时；剩余时间不足以完成一次检测时，
   * 屏幕会在用户注视中熄灭。这种情况推迟到熄屏或停止时恢复。电源状态未知时不推迟。
   */
  fun wouldExpireImmediately(snapshot: PowerSnapshot?, nowUptime: Long, original: Int): Boolean {
    val lastActivity = snapshot?.lastUserActivityUptime ?: return false
    return snapshot.interactive && lastActivity + original - nowUptime < MIN_REMAINING_MS
  }
}
