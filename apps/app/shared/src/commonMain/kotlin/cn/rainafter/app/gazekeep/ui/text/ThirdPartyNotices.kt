package cn.rainafter.app.gazekeep.ui.text

internal fun filterThirdPartyNotices(notices: String, query: String): String {
  if (query.isBlank()) return notices
  val search = query.trim()
  val normalized = notices.replace("\r\n", "\n")
  val boundaries = (listOf(0) + noticeSectionHeader.findAll(normalized).map { it.range.first } + normalized.length)
    .distinct()
  val sections = boundaries.zipWithNext { start, end -> normalized.substring(start, end).trim() }
    .filter { it.isNotEmpty() }
  val dependencies = sections.filter {
    dependencyHeader.matches(it.lineSequence().first()) && it.contains(search, ignoreCase = true)
  }
  // 前言和许可附录含必要声明，依赖命中时完整附上，避免按关键词切碎正文或遗漏版权信息。
  val selected = if (dependencies.isNotEmpty()) {
    sections.filter { !dependencyHeader.matches(it.lineSequence().first()) || it in dependencies }
  } else {
    sections.filter { it.contains(search, ignoreCase = true) }
  }
  return selected.joinToString("\n\n")
}

private val dependencyHeader = Regex("[a-zA-Z0-9_.-]+:[a-zA-Z0-9_.-]+:[^\\s]+")
private val noticeSectionHeader = Regex(
  "^(?:${dependencyHeader.pattern}|===== [^\\n]+ =====|许可文本与必要声明)$",
  RegexOption.MULTILINE,
)
