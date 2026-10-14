package cn.rainafter.app.gazekeep.engine.classify

/** 关闭请求阻止新调用，已开始的分类先归还图像使用权，再关闭 SDK。 */
internal class ClassifierLifetime(private val release: () -> Unit) : AutoCloseable {
  private var active = 0
  private var closed = false
  private var released = false

  suspend fun <T> use(block: suspend () -> T): T {
    synchronized(this) {
      check(!closed) { "classifier closed" }
      active++
    }
    try {
      return block()
    } finally {
      val dispose = synchronized(this) {
        active--
        shouldRelease()
      }
      if (dispose) release()
    }
  }

  override fun close() {
    val dispose = synchronized(this) {
      closed = true
      shouldRelease()
    }
    if (dispose) release()
  }

  private fun shouldRelease(): Boolean {
    if (!closed || active != 0 || released) return false
    released = true
    return true
  }
}
