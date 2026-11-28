package cn.rainafter.app.gazekeep.core.policy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RomBackgroundPolicyTest {
  @Test
  fun resolvesKnownOemFamiliesFromManufacturerAndBrand() {
    assertEquals(RomBackgroundPolicy.Xiaomi, resolveRomBackgroundPolicy("Xiaomi"))
    assertEquals(RomBackgroundPolicy.Xiaomi, resolveRomBackgroundPolicy("  xiaomi ", "Redmi"))
    assertEquals(RomBackgroundPolicy.Xiaomi, resolveRomBackgroundPolicy("Xiaomi", "POCO"))
    assertEquals(RomBackgroundPolicy.Xiaomi, resolveRomBackgroundPolicy("qualcomm", "BlackShark"))

    assertEquals(RomBackgroundPolicy.Oplus, resolveRomBackgroundPolicy("OPPO"))
    assertEquals(RomBackgroundPolicy.Oplus, resolveRomBackgroundPolicy("OnePlus"))
    assertEquals(RomBackgroundPolicy.Oplus, resolveRomBackgroundPolicy("realme"))
    assertEquals(RomBackgroundPolicy.Oplus, resolveRomBackgroundPolicy("OPLUS"))

    assertEquals(RomBackgroundPolicy.Vivo, resolveRomBackgroundPolicy("vivo"))
    assertEquals(RomBackgroundPolicy.Vivo, resolveRomBackgroundPolicy("vivo", "iQOO"))

    assertEquals(RomBackgroundPolicy.Samsung, resolveRomBackgroundPolicy("samsung"))
    assertEquals(RomBackgroundPolicy.Samsung, resolveRomBackgroundPolicy("SAMSUNG"))

    assertEquals(RomBackgroundPolicy.HuaweiHonor, resolveRomBackgroundPolicy("HUAWEI"))
    assertEquals(RomBackgroundPolicy.HuaweiHonor, resolveRomBackgroundPolicy("HONOR"))
  }

  @Test
  fun returnsNullForUnmatchedManufacturers() {
    assertNull(resolveRomBackgroundPolicy("Google", "Pixel"))
    assertNull(resolveRomBackgroundPolicy("Sony", "Xperia"))
    assertNull(resolveRomBackgroundPolicy("", ""))
  }
}
