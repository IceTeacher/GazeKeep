package cn.rainafter.app.gazekeep.notification

import cn.rainafter.app.gazekeep.core.engine.DecisionRecord
import java.io.File
import java.util.UUID

/** 每条记录独立保存，清理不需读取包名或其他记录内容。调用方使用单一 IO 队列。 */
class DecisionLogStore(private val directory: File, private val clock: () -> Long = System::currentTimeMillis) {
  private var enabled = false

  @Synchronized fun setEnabled(value: Boolean) {
    enabled = value
    if (!value) {
      directory.listFiles()?.filter { it.extension == "log" }?.forEach { check(it.delete()) }
    } else {
      cleanup()
    }
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
    cleanup()
    check(directory.isDirectory || directory.mkdirs())
    val packageName = record.foregroundPackage?.takeIf { PACKAGE.matches(it) }
    val now = clock()
    directory.resolve("$now-${UUID.randomUUID()}.log").writeText(
      listOf(
        now, record.uptime, packageName, record.attention, record.reason, record.skipReason,
        record.abortReason, record.frameReason, record.backend, record.extended, record.latencyMs,
      )
        .joinToString("\t") { it?.toString() ?: "-" } + "\n",
    )
  }

  companion object {
    const val RETENTION_MS = 86_400_000L
    private val PACKAGE = Regex("[A-Za-z0-9_.]+")
  }
}
