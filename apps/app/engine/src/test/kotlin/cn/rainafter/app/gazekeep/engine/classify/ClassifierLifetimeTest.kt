package cn.rainafter.app.gazekeep.engine.classify

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ClassifierLifetimeTest {
  @Test fun closeWaitsForActiveClassificationAndRejectsNewFrames() = runBlocking(Dispatchers.Unconfined) {
    var closes = 0
    val lifetime = ClassifierLifetime { closes++ }
    val sdkResult = CompletableDeferred<String>()
    val frame = async(start = CoroutineStart.UNDISPATCHED) { lifetime.use { sdkResult.await() } }
    lifetime.close()
    lifetime.close()
    assertEquals(0, closes)
    assertFailsWith<IllegalStateException> { lifetime.use { error("closed detector must not process") } }
    sdkResult.complete("Looking")
    assertEquals("Looking", frame.await())
    assertEquals(1, closes)
    lifetime.close()
    assertEquals(1, closes)
  }

  @Test fun failureReturnsActiveUseAndAllowsDeterministicDisposal() = runBlocking(Dispatchers.Unconfined) {
    var closes = 0
    val lifetime = ClassifierLifetime { closes++ }
    assertFailsWith<IllegalArgumentException> { lifetime.use { throw IllegalArgumentException("SDK failure") } }
    lifetime.close()
    assertEquals(1, closes)
  }
}
