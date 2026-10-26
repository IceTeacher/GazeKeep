package cn.rainafter.app.gazekeep.backend.shizuku.client

internal object ExtendSequence {
  /**
   * 序号取系统上次用户活动时间。userActivity 会推进它，因此重复序号视为过期；
   * 临时超时退路不产生活动，退路生效期间允许同一序号继续延长。
   */
  fun isStale(value: Long, last: Long, timeoutFallbackActive: Boolean): Boolean =
    value < last || (value == last && !timeoutFallbackActive)
}
