package cn.rainafter.app.gazekeep.engine.camera

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CaptureOwnershipCoordinatorTest {
  @Test fun selfTestCancelsServiceAndWaitsForItsCleanupBeforeBinding() = runBlocking(Dispatchers.Unconfined) {
    val owner = CaptureOwnershipCoordinator()
    val cleanup = CompletableDeferred<Unit>()
    val cleanupStarted = CompletableDeferred<Unit>()
    val events = mutableListOf<String>()
    val service = async(start = CoroutineStart.UNDISPATCHED) {
      owner.capture {
        try {
          CompletableDeferred<Unit>().await()
        } finally {
          withContext(NonCancellable) {
            events += "unbind"
            cleanupStarted.complete(Unit)
            cleanup.await()
            events += "drained"
          }
        }
      }
    }
    val lease = owner.claimSelfTest()
    cleanupStarted.await()
    val selfTest = async(start = CoroutineStart.UNDISPATCHED) {
      owner.capture(lease) {
        events += "bind"
        true
      }
    }
    assertEquals(listOf("unbind"), events)
    assertFalse(selfTest.isCompleted)
    assertNull(owner.capture { error("service must not bind") })
    cleanup.complete(Unit)
    assertNull(service.await())
    assertEquals(true, selfTest.await())
    assertEquals(listOf("unbind", "drained", "bind"), events)
    lease.close()
    assertEquals(true, owner.capture { true })
  }

  @Test fun oldLeaseCannotClearOrUseNewSelfTestOwnership() = runBlocking(Dispatchers.Unconfined) {
    val owner = CaptureOwnershipCoordinator()
    val old = owner.claimSelfTest()
    val next = owner.claimSelfTest()
    old.close()
    assertTrue(owner.isSelfTestActive())
    assertNull(owner.capture(old) { error("old self-test must not bind") })
    assertNull(owner.capture { error("service must not bind") })
    assertEquals(true, owner.capture(next) { true })
    next.close()
    next.close()
    assertFalse(owner.isSelfTestActive())
  }

  @Test fun callerCancellationPropagatesAndDoesNotLeakOwnership() = runBlocking(Dispatchers.Unconfined) {
    val owner = CaptureOwnershipCoordinator()
    val capture = async(start = CoroutineStart.UNDISPATCHED) { owner.capture { CompletableDeferred<Unit>().await() } }
    capture.cancel()
    capture.join()
    assertTrue(capture.isCancelled)
    assertEquals(true, owner.capture { true })
  }
}
