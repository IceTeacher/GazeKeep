package cn.rainafter.app.gazekeep.engine.camera

import cn.rainafter.app.gazekeep.core.engine.AbortReason
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CaptureLifecycleTest {
  @Test fun cancellationWhileMainThreadBindingIsQueuedNeverCreatesResources() = runBlocking {
    val queued = mutableListOf<Runnable>()
    val main = object : CoroutineDispatcher() {
      override fun dispatch(context: CoroutineContext, block: Runnable) {
        queued += block
      }
    }
    val lifecycle = CaptureLifecycle(main) { 0L }
    var binds = 0
    val capture = async(start = CoroutineStart.UNDISPATCHED) {
      lifecycle.capture(10_000L) {
        {
          binds++
          error("cancelled binding")
        }
      }
    }
    lifecycle.close()
    while (queued.isNotEmpty()) queued.removeAt(0).run()
    capture.join()
    assertTrue(capture.isCancelled)
    assertEquals(0, binds)
  }

  @Test fun successfulAndFailedBindingsAreReleasedOnTheInjectedMainThread() = runBlocking {
    lateinit var mainThread: Thread
    Executors.newSingleThreadExecutor { Thread(it, "capture-main").also { thread -> mainThread = thread } }
      .asCoroutineDispatcher().use { main ->
        val lifecycle = CaptureLifecycle(main) { 0L }
        val threads = mutableListOf<Thread>()
        fun binding(fail: Boolean) = object : CaptureBinding {
          override suspend fun capture(): CaptureOutcome {
            threads += Thread.currentThread()
            if (fail) throw IllegalStateException("bind failed")
            return CaptureOutcome.Completed
          }
          override suspend fun release() {
            threads += Thread.currentThread()
          }
        }
        assertEquals(CaptureOutcome.Completed, lifecycle.capture(10_000L) { { binding(false) } })
        assertFailsWith<IllegalStateException> { lifecycle.capture(10_000L) { { binding(true) } } }
        assertEquals(List(4) { mainThread }, threads)
        lifecycle.close()
      }
  }

  @Test fun expiredPlanNeverRequestsProvider() = runBlocking {
    val lifecycle = CaptureLifecycle(Dispatchers.Unconfined) { 100L }
    assertEquals(CaptureOutcome.Aborted(AbortReason.Deadline), lifecycle.capture(100L) { error("must not open") })
  }

  @Test fun providerCompletingAfterDeadlineDoesNotCreateOrBindResources() = runBlocking {
    var now = 0L
    val lifecycle = CaptureLifecycle(Dispatchers.Unconfined) { now }
    val outcome = lifecycle.capture(10_000L) {
      now = 10_000L
      val create: () -> CaptureBinding = { error("expired request must not bind") }
      create
    }
    assertEquals(CaptureOutcome.Aborted(AbortReason.Deadline), outcome)
  }

  @Test fun closeWhileWaitingDoesNotCancelSharedProviderAndRejectsLateFactory() = runBlocking {
    val lifecycle = CaptureLifecycle(Dispatchers.Unconfined) { 0L }
    val provider = CompletableDeferred<Unit>()
    val capture = async(start = CoroutineStart.UNDISPATCHED) {
      lifecycle.capture(10_000L) {
        provider.await()
        val create: () -> CaptureBinding = { error("cancelled request must not bind") }
        create
      }
    }
    lifecycle.close()
    capture.join()
    assertTrue(capture.isCancelled)
    assertFalse(provider.isCancelled)
    provider.complete(Unit)
    assertEquals(CaptureOutcome.Aborted(AbortReason.Cancelled), lifecycle.capture(10_000L) { error("closed") })
  }

  @Test fun cancellationWaitsForIndependentResourceCleanup() = runBlocking {
    val lifecycle = CaptureLifecycle(Dispatchers.Unconfined) { 0L }
    val released = CompletableDeferred<Unit>()
    val drained = CompletableDeferred<Unit>()
    var releases = 0
    val capture = async(start = CoroutineStart.UNDISPATCHED) {
      lifecycle.capture(10_000L) {
        {
          object : CaptureBinding {
            override suspend fun capture(): CaptureOutcome {
              CompletableDeferred<Unit>().await()
              error("cancelled")
            }
            override suspend fun release() {
              releases++
              released.complete(Unit)
              drained.await()
            }
          }
        }
      }
    }
    capture.cancel()
    released.await()
    assertFalse(capture.isCompleted)
    drained.complete(Unit)
    capture.join()
    assertEquals(1, releases)
    lifecycle.close()
    assertEquals(1, releases)
  }
}
