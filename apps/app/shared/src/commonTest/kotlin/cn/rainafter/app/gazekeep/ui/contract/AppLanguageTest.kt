package cn.rainafter.app.gazekeep.ui.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppLanguageTest {
  @Test
  fun emptyTagsFollowSystem() {
    assertEquals(AppLanguage.System, AppLanguage.fromLanguageTags(""))
    assertEquals(AppLanguage.System, AppLanguage.fromLanguageTags("  "))
    assertNull(AppLanguage.System.languageTag)
  }

  @Test
  fun firstTagDecidesBySupportedLanguageRegardlessOfScriptOrRegion() {
    assertEquals(AppLanguage.Chinese, AppLanguage.fromLanguageTags("zh-CN"))
    assertEquals(AppLanguage.Chinese, AppLanguage.fromLanguageTags("zh-Hans-CN,en-US"))
    assertEquals(AppLanguage.English, AppLanguage.fromLanguageTags("en"))
    assertEquals(AppLanguage.English, AppLanguage.fromLanguageTags("EN_gb,zh-CN"))
  }

  // 系统外部写入了不受支持的语言时，资源本就回退到默认中文，界面按「跟随系统」展示。
  @Test
  fun unsupportedTagsFollowSystem() {
    assertEquals(AppLanguage.System, AppLanguage.fromLanguageTags("fr-FR,en"))
    assertEquals(AppLanguage.System, AppLanguage.fromLanguageTags("und"))
  }

  @Test
  fun explicitLanguagesRoundTrip() {
    for (language in AppLanguage.entries) {
      assertEquals(language, AppLanguage.fromLanguageTags(language.languageTag.orEmpty()))
    }
  }
}
