package cn.rainafter.app.gazekeep.notification

import cn.rainafter.app.gazekeep.ui.contract.LogEntry
import cn.rainafter.app.gazekeep.ui.contract.LogLevel
import kotlin.test.Test
import kotlin.test.assertEquals

class LogOrderTest {
  @Test fun lateEnqueueWithEarlierEventTimeDoesNotJumpAhead() {
    val earlier = entry(timestampMs = 1_000L, sequence = 2L, message = "earlier")
    val later = entry(timestampMs = 2_000L, sequence = 1L, message = "later")
    val published = insertNewestFirst(insertNewestFirst(emptyList(), later), earlier)
    assertEquals(listOf(later, earlier), published)
  }

  @Test fun sameEventTimeKeepsHigherWriteSequenceFirst() {
    val first = entry(timestampMs = 1_000L, sequence = 1L, message = "first")
    val second = entry(timestampMs = 1_000L, sequence = 2L, message = "second")
    val published = insertNewestFirst(insertNewestFirst(emptyList(), second), first)
    assertEquals(listOf(second, first), published)
  }

  private fun entry(timestampMs: Long, sequence: Long, message: String) = LogEntry(
    id = "$timestampMs-$sequence-00000000-0000-0000-0000-000000000000",
    timestampMs = timestampMs,
    level = LogLevel.Info,
    tag = "Decision",
    source = "cn.rainafter.app.gazekeep",
    target = "system",
    message = message,
  )
}
