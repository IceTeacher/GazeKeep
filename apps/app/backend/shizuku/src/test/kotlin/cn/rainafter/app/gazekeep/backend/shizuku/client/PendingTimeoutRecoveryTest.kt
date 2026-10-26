package cn.rainafter.app.gazekeep.backend.shizuku.client

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PendingTimeoutRecoveryTest {
  @Test fun withoutARecordBootDoesNotConnectToShizuku() = runTest {
    var connections = 0
    assertTrue(
      PendingTimeoutRecovery.recoverIfPending({ false }) {
        connections++
        true
      },
    )
    assertEquals(0, connections)
  }

  @Test fun pendingRecordReportsWhetherTheConnectionRestoredIt() = runTest {
    assertTrue(PendingTimeoutRecovery.recoverIfPending({ true }) { true })
    assertFalse(PendingTimeoutRecovery.recoverIfPending({ true }) { false })
  }
}
