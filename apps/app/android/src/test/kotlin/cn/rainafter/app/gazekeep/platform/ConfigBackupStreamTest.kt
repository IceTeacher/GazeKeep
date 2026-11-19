package cn.rainafter.app.gazekeep.platform

import cn.rainafter.app.gazekeep.core.model.ConfigBackupCodec
import cn.rainafter.app.gazekeep.ui.contract.ConfigReadResult
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ConfigBackupStreamTest {
  @Test
  fun readBoundedUtf8ReadsValidPayloadUpToExactLimit() {
    val exact = "a".repeat(ConfigBackupCodec.MAX_BACKUP_BYTES)
    val result = assertIs<ConfigReadResult.Loaded>(
      readBoundedUtf8(ByteArrayInputStream(exact.toByteArray(Charsets.UTF_8))),
    )
    assertEquals(exact, result.content)

    val chinese = """{"schemaVersion":1,"note":"中文"}"""
    val utf8Result = assertIs<ConfigReadResult.Loaded>(
      readBoundedUtf8(ByteArrayInputStream(chinese.toByteArray(Charsets.UTF_8))),
    )
    assertEquals(chinese, utf8Result.content)
  }

  @Test
  fun readBoundedUtf8RejectsPayloadExceedingLimit() {
    val tooLarge = ByteArray(ConfigBackupCodec.MAX_BACKUP_BYTES + 1) { 'a'.code.toByte() }
    assertEquals(
      ConfigReadResult.TooLarge,
      readBoundedUtf8(ByteArrayInputStream(tooLarge)),
    )
  }
}
