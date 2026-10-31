package cn.rainafter.app.gazekeep.notification

import cn.rainafter.app.gazekeep.ui.contract.LogEntry

/** 文件名第二段是写入序号；与 [DecisionLogStore] 的文件名解析保持同一规则。 */
internal fun logSequence(entry: LogEntry): Long = entry.id.split('-').getOrNull(1)?.toLongOrNull() ?: 0L

/** 最新事件在前；同一时刻再按写入序号和 id，避免并发入队后按落盘顺序覆盖事件顺序。 */
internal val newestFirst: Comparator<LogEntry> =
  compareByDescending<LogEntry> { it.timestampMs }.thenByDescending(::logSequence).thenByDescending { it.id }

internal fun insertNewestFirst(entries: List<LogEntry>, entry: LogEntry): List<LogEntry> {
  val index = entries.indexOfFirst { newestFirst.compare(entry, it) < 0 }.let { if (it < 0) entries.size else it }
  return buildList(entries.size + 1) {
    addAll(entries.subList(0, index))
    add(entry)
    addAll(entries.subList(index, entries.size))
  }
}
