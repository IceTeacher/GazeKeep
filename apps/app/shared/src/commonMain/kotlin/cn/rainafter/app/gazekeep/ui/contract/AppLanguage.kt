package cn.rainafter.app.gazekeep.ui.contract

/** 应用内语言；System 不覆盖系统语言，其余对应已提供的资源语言。 */
enum class AppLanguage(val languageTag: String?) {
  System(null),
  Chinese("zh-CN"),
  English("en"),
  ;

  companion object {
    /** 按语言列表的首项匹配（兼容 `,` 与 `_` 写法）；空列表或不受支持的语言视为跟随系统。 */
    fun fromLanguageTags(tags: String): AppLanguage {
      val language = tags.substringBefore(',').trim().substringBefore('-').substringBefore('_').lowercase()
      return when (language) {
        "zh" -> Chinese
        "en" -> English
        else -> System
      }
    }
  }
}
