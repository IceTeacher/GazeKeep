package cn.rainafter.app.gazekeep.controller

import org.junit.Test
import kotlin.test.assertEquals

class SystemInfoFormatterTest {
  @Test
  fun `does not duplicate manufacturer when model already starts with manufacturer`() {
    val info = buildSystemInfo(
      versionName = "1.0",
      versionCode = 1L,
      release = "14",
      sdkInt = 34,
      manufacturer = "Xiaomi",
      model = "Xiaomi 12 Pro Dimensity",
      fingerprint = "Xiaomi/daumier/daumier:14/UP1A.231005.007/OS2.0.6.0.ULGCNXM:user/release-keys",
    )
    assertEquals("v1.0 (1)", info.appVersion)
    assertEquals("Android 14 (API 34)", info.androidVersion)
    assertEquals("Xiaomi 12 Pro Dimensity", info.deviceModel)
    assertEquals(
      "Xiaomi/daumier/daumier:14/UP1A.231005.007/OS2.0.6.0.ULGCNXM:user/release-keys",
      info.systemFingerprint,
    )
  }

  @Test
  fun `prefixes manufacturer when model only contains model code`() {
    val info = buildSystemInfo(
      versionName = "1.0",
      versionCode = 1L,
      release = "15",
      sdkInt = 35,
      manufacturer = "Xiaomi",
      model = "23127PN0CC",
      fingerprint = "Xiaomi/shennong/shennong:15/AP3A.240905.015.A2/OS2.0.100.0:user/release-keys",
    )
    assertEquals("Xiaomi 23127PN0CC", info.deviceModel)
  }
}
