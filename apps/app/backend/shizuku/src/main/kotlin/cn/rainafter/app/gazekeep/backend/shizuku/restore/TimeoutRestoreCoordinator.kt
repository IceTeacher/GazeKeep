package cn.rainafter.app.gazekeep.backend.shizuku.restore

/** 可隔离的恢复协议；Android 存储和 shell 由装配层注入。 */
class TimeoutRestoreCoordinator(
  private val readPending: () -> Int?,
  private val writePending: (Int) -> Boolean,
  private val clearPending: () -> Boolean,
  private val getTimeout: () -> Int,
  private val setTimeout: (Int) -> Unit,
) {
  fun extend(extensionMs: Int) {
    val original = readPending() ?: getTimeout().also {
      check(it > 0) { "invalid original timeout" }
      check(writePending(it)) { "timeout restore persistence failed" }
    }
    val extended = (original.toLong() + extensionMs).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    setTimeout(extended)
    check(getTimeout() == extended) { "timeout extension readback failed" }
  }

  fun restore() {
    val original = readPending() ?: return
    setTimeout(original)
    check(getTimeout() == original) { "timeout restore readback failed" }
    check(clearPending()) { "timeout restore clear failed" }
  }
}
