package cn.rainafter.app.gazekeep.backend.shizuku.restore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TimeoutRestoreCoordinatorTest {
  private var pending: Int? = null
  private var timeout = 30_000
  private var writes = 0
  private fun coordinator(save: Boolean = true, clear: Boolean = true, accept: Boolean = true) =
    TimeoutRestoreCoordinator(
      { pending },
      {
        if (save) pending = it
        save
      },
      {
        if (clear) pending = null
        clear
      },
      { timeout },
      {
        writes++
        if (accept) timeout = it
      },
    )

  @Test fun failedPersistenceNeverChangesSystemTimeout() {
    assertFailsWith<IllegalStateException> { coordinator(save = false).extend(60_000) }
    assertEquals(30_000, timeout)
    assertEquals(0, writes)
  }

  @Test fun restartRestoresBeforeClearingAndCanRetry() {
    coordinator().extend(60_000)
    assertEquals(30_000, pending)
    assertEquals(90_000, timeout)
    coordinator().restore()
    assertEquals(30_000, timeout)
    assertEquals(null, pending)
  }

  @Test fun failedReadbackKeepsRecoveryRecord() {
    coordinator().extend(60_000)
    assertFailsWith<IllegalStateException> { coordinator(accept = false).restore() }
    assertEquals(30_000, pending)
  }

  @Test fun failedClearIsReportedAndRecoveryRemainsRetryable() {
    coordinator().extend(60_000)
    assertFailsWith<IllegalStateException> { coordinator(clear = false).restore() }
    assertEquals(30_000, timeout)
    assertEquals(30_000, pending)
    coordinator().restore()
    assertEquals(null, pending)
  }

  @Test fun extensionCannotOverflowOrReplaceTheOriginalValue() {
    timeout = Int.MAX_VALUE - 10
    coordinator().extend(60_000)
    assertEquals(Int.MAX_VALUE, timeout)
    assertEquals(Int.MAX_VALUE - 10, pending)
    coordinator().extend(60_000)
    assertEquals(Int.MAX_VALUE - 10, pending)
  }
}
