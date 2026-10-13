package cn.rainafter.app.gazekeep.xposed.entry

/** 仅初始化期间持锁；失败先使 core 失效，再解除已经安装的钩子。 */
internal class BootstrapSession<T : Any> {
  private var attempted = false
  private var active: T? = null

  @Synchronized
  fun start(create: () -> T, install: (T) -> Boolean, close: (T) -> Unit, rollback: () -> Unit): T? {
    active?.let { return it }
    if (attempted) return null
    attempted = true
    var core: T? = null
    try {
      val created = create()
      core = created
      if (install(created)) {
        active = created
        return created
      }
    } catch (_: Throwable) {
      // 包括部分注册失败；所有清理路径都要执行，不能把异常抛回系统。
    }
    core?.let { runCatching { close(it) } }
    runCatching(rollback)
    return null
  }
}
