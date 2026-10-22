package cn.rainafter.app.gazekeep.controller

import kotlin.test.Test
import kotlin.test.assertEquals

// 样本使用虚构厂商、型号与指纹，不保留真实设备信息。
class SystemInfoFormatterTest {
  private fun info(
    manufacturer: String,
    model: String,
    fingerprint: String = "Acme/demo/demo:15/TEST:user/release-keys",
  ) = buildSystemInfo("1.2.3", "15", 35, manufacturer, model, fingerprint)

  @Test
  fun formatsVersionsForDisplay() {
    val info = info("Acme", "Acme Phone 1")
    assertEquals("v1.2.3", info.appVersion)
    assertEquals("Android 15 (API 35)", info.androidVersion)
  }

  @Test
  fun deviceModelPrefixesManufacturerOnlyWhenMissing() {
    assertEquals("Acme Phone 1", info("Acme", "Acme Phone 1").deviceModel)
    assertEquals("ACME Phone 1", info("acme", "ACME Phone 1").deviceModel)
    assertEquals("Acme X100", info(" Acme ", " X100 ").deviceModel)
  }

  @Test
  fun missingFieldsFallBackWithoutBlankOutput() {
    assertEquals("Acme", info("Acme", " ").deviceModel)
    assertEquals("X100", info("", "X100").deviceModel)
    assertEquals("-", info("", "").deviceModel)
    assertEquals("-", info("Acme", "X100", fingerprint = " ").systemFingerprint)
  }
}
