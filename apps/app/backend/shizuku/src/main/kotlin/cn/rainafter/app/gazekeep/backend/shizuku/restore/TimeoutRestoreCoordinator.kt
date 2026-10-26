package cn.rainafter.app.gazekeep.backend.shizuku.restore

/**
 * 退路的恢复记录：original 为用户原值；extended 与 previous 为本应用最近两次写入的延长值，
 * 用于区分用户在延长期间手动修改的超时。旧版本记录没有写入值，恢复时一律写回原值。
 */
data class PendingRestore(val original: Int, val extended: Int? = null, val previous: Int? = null) {
  fun wroteByUs(current: Int): Boolean =
    extended == null || current == extended || current == previous || current == original
}

/** 可隔离的恢复协议；Android 存储和 shell 由装配层注入。 */
class TimeoutRestoreCoordinator(
  private val readPending: () -> PendingRestore?,
  private val writePending: (PendingRestore) -> Boolean,
  private val clearPending: () -> Boolean,
  private val getTimeout: () -> Int,
  private val setTimeout: (Int) -> Unit,
) {
  /**
   * 把熄屏点推到当前时刻之后 [extensionMs]：系统从上次用户活动起计时，退路不产生活动，
   * 因此超时取已过时间加延长量，且不低于原值。先落盘再写入，失败时不改系统设置。
   */
  fun extend(extensionMs: Int, elapsedMs: Long = 0L) {
    val pending = readPending()
    val current = getTimeout()
    // 记录存在但当前值不是本应用写入的，说明用户在延长期间改了超时，以新值作为原值。
    val original = if (pending != null && pending.wroteByUs(current)) {
      pending.original
    } else {
      current.also { check(it > 0) { "invalid original timeout" } }
    }
    val extended = maxOf(original.toLong(), elapsedMs.coerceAtLeast(0L) + extensionMs)
      .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    val previous = pending?.extended?.takeIf { pending.original == original }
    check(writePending(PendingRestore(original, extended, previous))) { "timeout restore persistence failed" }
    setTimeout(extended)
    check(getTimeout() == extended) { "timeout extension readback failed" }
  }

  /**
   * 写回原值后读回确认再清记录。[shouldDefer] 为 true 时保留记录与延长值，由之后的恢复完成；
   * 当前值已被用户修改时只清记录。返回 false 表示本次推迟。
   */
  fun restore(shouldDefer: (original: Int) -> Boolean = { false }): Boolean {
    val pending = readPending() ?: return true
    val current = getTimeout()
    if (pending.wroteByUs(current) && current != pending.original) {
      if (shouldDefer(pending.original)) return false
      setTimeout(pending.original)
      check(getTimeout() == pending.original) { "timeout restore readback failed" }
    }
    check(clearPending()) { "timeout restore clear failed" }
    return true
  }
}
