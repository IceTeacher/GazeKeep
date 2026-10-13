package cn.rainafter.app.gazekeep.notification

import cn.rainafter.app.gazekeep.core.engine.DecisionReason
import cn.rainafter.app.gazekeep.core.engine.DecisionRecord
import cn.rainafter.app.gazekeep.ui.contract.LogEntry
import cn.rainafter.app.gazekeep.ui.contract.LogLevel
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/** 每条记录独立保存，清理不需读取包名或其他记录内容。调用方使用单一 IO 队列。 */
class DecisionLogStore(private val directory: File, private val clock: () -> Long = System::currentTimeMillis) {
  private var enabled = false
  private val sequence = AtomicLong(0L)

  @Synchronized fun setEnabled(value: Boolean) {
    enabled = value
    if (!value) {
      clear()
    } else {
      cleanup()
    }
  }

  @Synchronized fun clear() {
    directory.listFiles()?.filter { it.extension == "log" }?.forEach { check(it.delete()) }
  }

  @Synchronized fun cleanup() {
    val cutoff = clock() - RETENTION_MS
    directory.listFiles()?.filter { it.extension == "log" }?.forEach { file ->
      val at = file.name.substringBefore('-').toLongOrNull()
      if (at == null || at <= cutoff) check(file.delete())
    }
  }

  @Synchronized fun append(record: DecisionRecord) {
    if (!enabled) return
    val packageName = record.foregroundPackage?.takeIf { PACKAGE.matches(it) }
    val target = packageName ?: record.backend?.name ?: "system"
    val level = when (record.reason) {
      DecisionReason.Extended -> LogLevel.Info

      DecisionReason.NotLooking, DecisionReason.GateSkipped, DecisionReason.MaxHold -> LogLevel.Debug

      DecisionReason.Unknown, DecisionReason.TooLate, DecisionReason.StaleExtend, DecisionReason.Aborted ->
        LogLevel.Warning

      DecisionReason.FailedExtend -> LogLevel.Error
    }
    val details = buildList {
      add("reason=${record.reason}")
      add("attention=${record.attention ?: "-"}")
      add("extended=${record.extended}")
      add("backend=${record.backend ?: "-"}")
      record.latencyMs?.let { add("latencyMs=$it") }
      record.skipReason?.let { add("skip=$it") }
      record.abortReason?.let { add("abort=$it") }
      record.frameReason?.let { add("frame=$it") }
    }.joinToString(" ")
    writeEntry(
      level = level,
      tag = "Decision",
      target = target,
      message = "DecisionRecord [main] $details",
    )
  }

  @Synchronized fun appendLog(level: LogLevel, tag: String, target: String? = "system", message: String) {
    if (!enabled) return
    val safeTag = tag.replace(UNSAFE_CHARS, " ").trim().ifEmpty { "GazeKeep" }
    val safeTarget = target?.takeIf { TARGET.matches(it) } ?: "system"
    val safeMessage = message.replace(UNSAFE_CHARS, " ").trim()
    writeEntry(level = level, tag = safeTag, target = safeTarget, message = safeMessage)
  }

  @Synchronized fun readEntries(): List<LogEntry> {
    if (!enabled) return emptyList()
    cleanup()
    val files = directory.listFiles()?.filter { it.extension == "log" } ?: return emptyList()
    return files
      .mapNotNull { file -> parseFile(file) }
      .sortedWith(compareBy<ParsedFile>({ it.entry.timestampMs }, { it.seq }, { it.entry.id }))
      .map { it.entry }
  }

  private fun writeEntry(level: LogLevel, tag: String, target: String, message: String) {
    cleanup()
    check(directory.isDirectory || directory.mkdirs())
    val now = clock()
    val seq = sequence.incrementAndGet()
    val id = "$now-$seq-${UUID.randomUUID()}"
    directory.resolve("$id.log").writeText(
      listOf(
        FORMAT_V2,
        now.toString(),
        level.name,
        tag,
        SOURCE_PACKAGE,
        target,
        message,
      ).joinToString("\t") + "\n",
    )
  }

  private data class ParsedFile(val seq: Long, val entry: LogEntry)

  private fun parseFile(file: File): ParsedFile? {
    val line = runCatching { file.readText().lineSequence().firstOrNull() }.getOrNull() ?: return null
    val parts = line.split("\t")
    val nameParts = file.nameWithoutExtension.split("-")
    val fileTimestamp = nameParts.firstOrNull()?.toLongOrNull() ?: return null
    val seq = nameParts.getOrNull(1)?.toLongOrNull() ?: 0L
    if (parts.firstOrNull() == FORMAT_V2 && parts.size >= 7) {
      val timestamp = parts[1].toLongOrNull() ?: fileTimestamp
      val level = runCatching { LogLevel.valueOf(parts[2]) }.getOrDefault(LogLevel.Info)
      return ParsedFile(
        seq = seq,
        entry = LogEntry(
          id = file.nameWithoutExtension,
          timestampMs = timestamp,
          level = level,
          tag = parts[3],
          source = parts[4],
          target = parts[5],
          message = parts.subList(6, parts.size).joinToString(" "),
        ),
      )
    }
    return null
  }

  companion object {
    const val RETENTION_MS = 86_400_000L
    private const val FORMAT_V2 = "v2"
    private const val SOURCE_PACKAGE = "cn.rainafter.app.gazekeep"
    private val PACKAGE = Regex("[A-Za-z0-9_.]+")
    private val TARGET = Regex("[A-Za-z0-9_.:-]+")
    private val UNSAFE_CHARS = Regex("[\\t\\r\\n]+")
  }
}
