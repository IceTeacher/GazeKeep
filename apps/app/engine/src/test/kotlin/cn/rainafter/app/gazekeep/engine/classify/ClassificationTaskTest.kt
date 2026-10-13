package cn.rainafter.app.gazekeep.engine.classify

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClassificationTaskTest {
  @Test fun sdkCancellationIsAClassificationFailure() = runBlocking<Unit> {
    val error = assertFailsWith<Exception> {
      awaitClassificationResult<String> { _, _, cancelled -> cancelled() }
    }
    assertFalse(error is CancellationException)
  }

  @Test fun callerCancellationRejectsLateSdkSuccess() = runBlocking {
    coroutineScope {
      lateinit var success: (String) -> Unit
      val awaiting = async(start = CoroutineStart.UNDISPATCHED) {
        awaitClassificationResult<String> { onSuccess, _, _ -> success = onSuccess }
      }
      awaiting.cancel()
      success("late")
      assertFailsWith<CancellationException> { awaiting.await() }
      assertTrue(awaiting.isCancelled)
    }
  }
}
