package cn.rainafter.app.gazekeep.engine.camera

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex

/** 进程内相机交接；自检优先，服务只尝试一次且不排队。 */
class CaptureOwnershipCoordinator {
  private val mutex = Mutex()
  private val guard = Any()
  private var selfTest: Lease? = null
  private var active: Job? = null

  inner class Lease internal constructor() : AutoCloseable {
    override fun close() {
      synchronized(guard) { if (selfTest === this) selfTest = null }
    }
  }

  fun claimSelfTest(): Lease {
    val lease = Lease()
    val previous = synchronized(guard) {
      selfTest = lease
      active
    }
    previous?.cancel()
    return lease
  }

  fun isSelfTestActive(): Boolean = synchronized(guard) { selfTest != null }

  suspend fun <T> capture(lease: Lease? = null, block: suspend () -> T): T? {
    if (lease == null) {
      if (!mutex.tryLock()) return null
    } else {
      mutex.lock()
    }
    val caller = currentCoroutineContext()
    try {
      return coroutineScope {
        val job = currentCoroutineContext()[Job]!!
        val allowed = synchronized(guard) {
          if (selfTest !== lease) {
            false
          } else {
            active = job
            true
          }
        }
        if (!allowed) return@coroutineScope null
        try {
          currentCoroutineContext().ensureActive()
          block()
        } finally {
          synchronized(guard) { if (active === job) active = null }
        }
      }
    } catch (error: CancellationException) {
      // 自检抢占只中止采集，调用方仍需向 reducer 回灌取消结果并释放 Hold。
      caller.ensureActive()
      return null
    } finally {
      mutex.unlock()
    }
  }
}
