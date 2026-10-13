package cn.rainafter.app.gazekeep.ui.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThirdPartyNoticesTest {
  private val notices = """
    GazeKeep 第三方依赖说明
    JSR-330 来源：Copyright (C) 2009 The JSR-330 Expert Group。

    androidx.activity:activity:1.13.0
      Apache-2.0
    top.yukonga.miuix.kmp:miuix-ui-android:0.9.4
      Apache-2.0
    org.checkerframework:checker-qual:3.43.0
      MIT

    许可文本与必要声明

    ===== Apache-2.0.txt =====

    Apache License

    TERMS AND CONDITIONS

    ===== checker-qual-LICENSE.txt =====
    Checker Framework qualifiers
    Copyright by the Checker Framework developers

    MIT License:

    Permission is hereby granted

    THE SOFTWARE IS PROVIDED "AS IS"
  """.trimIndent()

  @Test
  fun `dependency search excludes unrelated entries and preserves license appendix`() {
    val result = filterThirdPartyNotices(notices, "MIUIX")
    assertTrue(result.contains("top.yukonga.miuix.kmp:miuix-ui-android:0.9.4"))
    assertFalse(result.contains("androidx.activity:activity:1.13.0"))
    assertFalse(result.contains("org.checkerframework:checker-qual:3.43.0"))
    assertTrue(result.contains("Copyright (C) 2009 The JSR-330 Expert Group"))
    assertTrue(result.contains("TERMS AND CONDITIONS"))
    assertTrue(result.contains("THE SOFTWARE IS PROVIDED"))
  }

  @Test
  fun `license search preserves paragraphs and disclaimer with CRLF input`() {
    val result = filterThirdPartyNotices(notices.replace("\n", "\r\n"), "  checker-qual-LICENSE  ")
    assertEquals(
      notices.substringAfter("===== checker-qual-LICENSE.txt =====").trim(),
      result.substringAfter("===== checker-qual-LICENSE.txt =====").trim(),
    )
    assertTrue(result.contains("Copyright by the Checker Framework developers"))
    assertTrue(result.contains("Permission is hereby granted"))
    assertTrue(result.contains("THE SOFTWARE IS PROVIDED"))
    assertFalse(result.contains("===== Apache-2.0.txt ====="))
    assertFalse(result.contains("org.checkerframework:checker-qual:3.43.0"))
  }

  @Test
  fun `blank search preserves original text and unmatched search returns empty`() {
    assertEquals(notices, filterThirdPartyNotices(notices, "  "))
    assertEquals("", filterThirdPartyNotices(notices, "nonexistent-library"))
    assertEquals("", filterThirdPartyNotices("", "miuix"))
  }
}
