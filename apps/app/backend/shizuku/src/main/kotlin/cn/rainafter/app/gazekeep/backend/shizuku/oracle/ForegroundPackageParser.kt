package cn.rainafter.app.gazekeep.backend.shizuku.oracle

/** 只接受明确 resumed activity 行，不以历史任务或窗口标题猜测前台。 */
object ForegroundPackageParser {
  private val resumed =
    Regex(
      "(?:topResumedActivity|mResumedActivity|ResumedActivity):?\\s*[=:]?\\s*ActivityRecord\\{[^}]*?\\s([A-Za-z0-9_.]+)/[^}]*\\}",
    )
  fun parse(text: String): String? = text.lineSequence().mapNotNull {
    resumed.find(it)?.groupValues?.get(1)
  }.distinct().toList().singleOrNull()
}
