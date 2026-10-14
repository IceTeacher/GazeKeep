package cn.rainafter.app.gazekeep.engine.camera

import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 串行化采集与清理；close 取消本源全部操作，包括排队者，不取消调用方 scope。 */
internal class CaptureOperationGate : AutoCloseable {
  private val mutex = Mutex()
  private val guard = Any()
  private var closed = false
  private val pending = mutableSetOf<Job>()

  suspend fun <T> run(block: suspend () -> T): T? = coroutineScope {
    val job = currentCoroutineContext()[Job]!!
    val allowed = synchronized(guard) {
      if (closed) {
        false
      } else {
        pending += job
        true
      }
    }
    if (!allowed) return@coroutineScope null
    try {
      mutex.withLock {
        currentCoroutineContext().ensureActive()
        block()
      }
    } finally {
      synchronized(guard) { pending -= job }
    }
  }

  override fun close() {
    val jobs = synchronized(guard) {
      closed = true
      pending.toList()
    }
    jobs.forEach { it.cancel() }
  }
}
