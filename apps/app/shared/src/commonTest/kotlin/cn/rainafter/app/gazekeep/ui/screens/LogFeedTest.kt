package cn.rainafter.app.gazekeep.ui.screens

import cn.rainafter.app.gazekeep.ui.contract.LogEntry
import cn.rainafter.app.gazekeep.ui.contract.LogLevel
import kotlin.test.Test
import kotlin.test.assertEquals

class LogFeedTest {
  private fun entry(id: String, level: LogLevel = LogLevel.Debug) = LogEntry(
    id = id,
    timestampMs = 0L,
    level = level,
    tag = "Test",
    source = "cn.example.source",
    target = "system",
    message = "message $id",
  )

  // 与 DecisionLogs 一致：最新在前。
  private val old = listOf(entry("b"), entry("a"))
  private val newer = listOf(entry("d"), entry("c")) + old
  private val all: (LogEntry) -> Boolean = { true }

  private fun LogFeed.ids() = shown.map { it.id }

  @Test fun startShowsLatestWithNothingUnseen() {
    val feed = startLogFeed(old)
    assertEquals(listOf("b", "a"), feed.ids())
    assertEquals(0, feed.unseenCount(old, all))
  }

  @Test fun liveAtTopFollowsNewest() {
    val feed = startLogFeed(old).receive(newer, live = true, atTop = true)
    assertEquals(listOf("d", "c", "b", "a"), feed.ids())
    assertEquals(0, feed.unseenCount(newer, all))
  }

  @Test fun liveAwayFromTopKeepsUpdatingButCountsUnseen() {
    val feed = startLogFeed(old).receive(newer, live = true, atTop = false)
    assertEquals(listOf("d", "c", "b", "a"), feed.ids())
    assertEquals(2, feed.unseenCount(newer, all))
  }

  @Test fun returningToTopMarksEverythingSeen() {
    val feed = startLogFeed(old)
      .receive(newer, live = true, atTop = false)
      .receive(newer, live = true, atTop = true)
    assertEquals(0, feed.unseenCount(newer, all))
  }

  @Test fun pausedFreezesShownAndCountsArrivals() {
    val feed = startLogFeed(old).receive(newer, live = false, atTop = true)
    assertEquals(listOf("b", "a"), feed.ids())
    assertEquals(2, feed.unseenCount(newer, all))
  }

  @Test fun catchUpShowsLatestAndClearsUnseen() {
    val feed = startLogFeed(old).receive(newer, live = false, atTop = true).catchUp(newer)
    assertEquals(listOf("d", "c", "b", "a"), feed.ids())
    assertEquals(0, feed.unseenCount(newer, all))
  }

  @Test fun clearingLogsEmptiesFrozenViewAndLaterArrivalsAreUnseen() {
    val cleared = startLogFeed(old).receive(emptyList(), live = false, atTop = true)
    assertEquals(emptyList(), cleared.ids())
    val arrived = listOf(entry("e"))
    val feed = cleared.receive(arrived, live = false, atTop = true)
    assertEquals(emptyList(), feed.ids())
    assertEquals(1, feed.unseenCount(arrived, all))
  }

  @Test fun unseenCountOnlyIncludesEntriesMatchingTheFilter() {
    val latest = listOf(entry("d", LogLevel.Warning), entry("c")) + old
    val feed = startLogFeed(old).receive(latest, live = false, atTop = true)
    assertEquals(1, feed.unseenCount(latest) { it.level == LogLevel.Warning })
  }
}
