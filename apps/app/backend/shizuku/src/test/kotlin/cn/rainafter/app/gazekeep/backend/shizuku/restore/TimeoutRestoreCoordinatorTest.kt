package cn.rainafter.app.gazekeep.backend.shizuku.restore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimeoutRestoreCoordinatorTest {
  private var pending: PendingRestore? = null
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
    assertEquals(30_000, pending?.original)
    assertEquals(60_000, timeout)
    assertTrue(coordinator().restore())
    assertEquals(30_000, timeout)
    assertEquals(null, pending)
  }

  @Test fun failedReadbackKeepsRecoveryRecord() {
    coordinator().extend(60_000)
    assertFailsWith<IllegalStateException> { coordinator(accept = false).restore() }
    assertEquals(30_000, pending?.original)
  }

  @Test fun failedClearIsReportedAndRecoveryRemainsRetryable() {
    coordinator().extend(60_000)
    assertFailsWith<IllegalStateException> { coordinator(clear = false).restore() }
    assertEquals(30_000, timeout)
    assertEquals(30_000, pending?.original)
    assertTrue(coordinator().restore())
    assertEquals(null, pending)
  }

  @Test fun extensionCannotOverflowOrReplaceTheOriginalValue() {
    timeout = Int.MAX_VALUE - 10
    coordinator().extend(60_000, elapsedMs = Long.MAX_VALUE / 2)
    assertEquals(Int.MAX_VALUE, timeout)
    assertEquals(Int.MAX_VALUE - 10, pending?.original)
    coordinator().extend(60_000)
    assertEquals(Int.MAX_VALUE - 10, pending?.original)
  }

  @Test fun repeatedExtensionsMoveTheDeadlineForwardFromTheUnchangedLastActivity() {
    timeout = 15_000
    coordinator().extend(60_000, elapsedMs = 7_000)
    assertEquals(67_000, timeout)
    coordinator().extend(60_000, elapsedMs = 59_000)
    assertEquals(119_000, timeout)
    assertEquals(PendingRestore(original = 15_000, extended = 119_000, previous = 67_000), pending)
  }

  @Test fun extensionNeverShortensBelowTheOriginalTimeout() {
    timeout = 600_000
    coordinator().extend(60_000, elapsedMs = 1_000)
    assertEquals(600_000, timeout)
  }

  @Test fun restoreKeepsAValueTheUserChangedDuringTheExtension() {
    timeout = 15_000
    coordinator().extend(60_000, elapsedMs = 7_000)
    timeout = 30_000
    val before = writes
    assertTrue(coordinator().restore())
    assertEquals(30_000, timeout)
    assertEquals(before, writes)
    assertEquals(null, pending)
  }

  @Test fun userChangeBeforeTheNextExtensionBecomesTheNewOriginal() {
    timeout = 15_000
    coordinator().extend(60_000, elapsedMs = 7_000)
    timeout = 30_000
    coordinator().extend(60_000, elapsedMs = 20_000)
    assertEquals(80_000, timeout)
    assertEquals(30_000, pending?.original)
    assertTrue(coordinator().restore())
    assertEquals(30_000, timeout)
  }

  @Test fun crashBetweenPersistingAndWritingStillRestoresThePreviousExtension() {
    timeout = 67_000
    pending = PendingRestore(original = 15_000, extended = 119_000, previous = 67_000)
    assertTrue(coordinator().restore())
    assertEquals(15_000, timeout)
    assertEquals(null, pending)
  }

  @Test fun legacyRecordWithoutAppliedValuesAlwaysRestores() {
    timeout = 42_000
    pending = PendingRestore(original = 15_000)
    assertTrue(coordinator().restore())
    assertEquals(15_000, timeout)
  }

  @Test fun deferredRestoreKeepsTheRecordUntilAForcedOrLaterRestore() {
    timeout = 15_000
    coordinator().extend(60_000, elapsedMs = 7_000)
    val before = writes
    assertFalse(coordinator().restore { true })
    assertEquals(67_000, timeout)
    assertEquals(before, writes)
    assertEquals(15_000, pending?.original)
    assertTrue(coordinator().restore())
    assertEquals(15_000, timeout)
    assertEquals(null, pending)
  }

  @Test fun deferralIsNotConsultedWhenNothingNeedsWriting() {
    timeout = 30_000
    coordinator().extend(60_000)
    timeout = 45_000
    assertTrue(coordinator().restore { error("should not defer a user value") })
    assertEquals(45_000, timeout)
  }
}
