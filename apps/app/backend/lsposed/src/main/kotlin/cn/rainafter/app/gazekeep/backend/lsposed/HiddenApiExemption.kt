package cn.rainafter.app.gazekeep.backend.lsposed

/**
 * 隐藏 API 豁免在进程内只需成功一次。HiddenApiBypass 以非线程安全的 HashSet 拼接前缀，
 * 并发调用会把 null 传入 VMRuntime 原生方法导致进程崩溃，因此串行化且成功后不再调用；失败时下次重试。
 */
internal class HiddenApiExemption(private val apply: () -> Boolean) {
  @Volatile private var applied = false

  fun ensure(): Boolean {
    if (applied) return true
    synchronized(this) {
      if (!applied) applied = runCatching(apply).getOrDefault(false)
      return applied
    }
  }
}
