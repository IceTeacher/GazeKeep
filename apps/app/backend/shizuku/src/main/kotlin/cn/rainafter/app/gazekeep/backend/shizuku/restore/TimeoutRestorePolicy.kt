package cn.rainafter.app.gazekeep.backend.shizuku.restore

import cn.rainafter.app.gazekeep.backend.shizuku.protocol.PowerSnapshot

object TimeoutRestorePolicy {
  private const val MARGIN_MS = 1_000L

  /**
   * 亮屏时写回原值，系统会按上次用户活动重新计时；若已超过原值，屏幕会在用户注视中立即熄灭。
   * 这种情况推迟到熄屏或停止时恢复。电源状态未知时不推迟。
   */
  fun wouldExpireImmediately(snapshot: PowerSnapshot?, nowUptime: Long, original: Int): Boolean {
    val lastActivity = snapshot?.lastUserActivityUptime ?: return false
    return snapshot.interactive && nowUptime - lastActivity >= original - MARGIN_MS
  }
}
