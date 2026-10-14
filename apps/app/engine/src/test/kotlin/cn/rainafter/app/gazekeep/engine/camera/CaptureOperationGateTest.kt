package cn.rainafter.app.gazekeep.engine.camera

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CaptureOperationGateTest {
  @Test fun closeCancelsProviderWaitAndRejectsLateBindingWithoutCancellingCaller() = runBlocking(
    Dispatchers.Unconfined,
  ) {
    val gate = CaptureOperationGate()
    val provider = CompletableDeferred<Unit>()
    var binds = 0
    val capture = async(start = CoroutineStart.UNDISPATCHED) {
      gate.run {
        provider.await()
        binds++
      }
    }
    gate.close()
    gate.close()
    provider.complete(Unit)
    capture.join()
    assertTrue(capture.isCancelled)
    assertEquals(0, binds)
    assertNull(gate.run { error("closed source must not bind") })
    assertTrue(coroutineContext[kotlinx.coroutines.Job]!!.isActive)
  }

  @Test fun closeAlsoCancelsQueuedCaptureWhileOldCleanupIsStillRunning() = runBlocking {
    val gate = CaptureOperationGate()
    val cleaning = CompletableDeferred<Unit>()
    val drained = CompletableDeferred<Unit>()
    val first = async(start = CoroutineStart.UNDISPATCHED) {
      gate.run {
        try {
          CompletableDeferred<Unit>().await()
        } finally {
          withContext(NonCancellable) {
            cleaning.complete(Unit)
            drained.await()
          }
        }
      }
    }
    first.cancel()
    cleaning.await()
    val queued = async(start = CoroutineStart.UNDISPATCHED) { gate.run { error("queued operation must not bind") } }
    try {
      gate.close()
      yield()
      assertTrue(queued.isCancelled)
      assertNull(gate.run { error("closed source must reject immediately") })
    } finally {
      drained.complete(Unit)
      first.join()
      queued.join()
    }
  }

  @Test fun newCaptureWaitsForCancelledCaptureCleanup() = runBlocking(Dispatchers.Unconfined) {
    val gate = CaptureOperationGate()
    val drained = CompletableDeferred<Unit>()
    val cleanupStarted = CompletableDeferred<Unit>()
    val events = mutableListOf<String>()
    val first = async(start = CoroutineStart.UNDISPATCHED) {
      gate.run {
        try {
          CompletableDeferred<Unit>().await()
        } finally {
          withContext(NonCancellable) {
            events += "unbind"
            cleanupStarted.complete(Unit)
            drained.await()
            events += "drained"
          }
        }
      }
    }
    first.cancel()
    cleanupStarted.await()
    val next = async(start = CoroutineStart.UNDISPATCHED) { gate.run { events += "bind" } }
    assertFalse(next.isCompleted)
    assertEquals(listOf("unbind"), events)
    drained.complete(Unit)
    first.join()
    next.await()
    assertEquals(listOf("unbind", "drained", "bind"), events)
    gate.close()
  }
}
