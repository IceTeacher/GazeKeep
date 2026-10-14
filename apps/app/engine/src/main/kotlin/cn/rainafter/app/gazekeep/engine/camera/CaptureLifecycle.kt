package cn.rainafter.app.gazekeep.engine.camera

import cn.rainafter.app.gazekeep.core.engine.AbortReason
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal interface CaptureBinding {
  suspend fun capture(): CaptureOutcome
  suspend fun release()
}

/** provider 等待属于采集预算；资源创建和清理归主线程，取消后仍等待交接完成。 */
internal class CaptureLifecycle(private val main: CoroutineDispatcher, private val clock: () -> Long) : AutoCloseable {
  private val operations = CaptureOperationGate()

  suspend fun capture(abortAt: Long, awaitFactory: suspend () -> (() -> CaptureBinding)): CaptureOutcome =
    operations.run {
      val remaining = abortAt - clock()
      if (remaining <= 0L) return@run CaptureOutcome.Aborted(AbortReason.Deadline)
      withTimeoutOrNull(remaining) {
        val createBinding = awaitFactory()
        withContext(main) {
          currentCoroutineContext().ensureActive()
          if (clock() >= abortAt) return@withContext CaptureOutcome.Aborted(AbortReason.Deadline)
          val resources = createBinding()
          try {
            resources.capture()
          } finally {
            withContext(NonCancellable) { resources.release() }
          }
        }
      } ?: CaptureOutcome.Aborted(AbortReason.Deadline)
    } ?: CaptureOutcome.Aborted(AbortReason.Cancelled)

  override fun close() = operations.close()
}
