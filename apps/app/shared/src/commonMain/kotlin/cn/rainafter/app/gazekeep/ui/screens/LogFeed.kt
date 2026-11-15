package cn.rainafter.app.gazekeep.ui.screens

import cn.rainafter.app.gazekeep.ui.contract.LogEntry

/**
 * 日志页的显示状态：[shown] 是当前列表（暂停实时日志时定格），[seenId] 是用户已看到的最新一条。
 * 输入列表与 DecisionLogs 一致按最新在前排列；未读数由 [seenId] 在最新列表中的位置推出，
 * 不另存计数，避免重读、清理与增量写入交错时计数漂移。
 */
internal data class LogFeed(val shown: List<LogEntry>, val seenId: String?)

internal fun startLogFeed(latest: List<LogEntry>) = LogFeed(latest, latest.firstOrNull()?.id)

/**
 * 合并一次最新日志。实时模式总是换成最新列表；只有停在顶部时才视为看到了最新一条，
 * 离开顶部时由列表按 key 保持视口，新条目计入未读。暂停时列表不变，但清空日志立即生效。
 */
internal fun LogFeed.receive(latest: List<LogEntry>, live: Boolean, atTop: Boolean): LogFeed = when {
  latest.isEmpty() -> LogFeed(emptyList(), null)
  !live -> this
  atTop -> startLogFeed(latest)
  else -> copy(shown = latest)
}

/** 回到最新：显示最新列表并全部标记已读。 */
internal fun LogFeed.catchUp(latest: List<LogEntry>) = startLogFeed(latest)

/** 比已读锚点更新、且符合当前筛选的条目数；锚点已被清理时不把剩余记录当成新日志。 */
internal fun LogFeed.unseenCount(latest: List<LogEntry>, matches: (LogEntry) -> Boolean): Int {
  val boundary = if (seenId == null) latest.size else latest.indexOfFirst { it.id == seenId }
  if (boundary <= 0) return 0
  return latest.subList(0, boundary).count(matches)
}
