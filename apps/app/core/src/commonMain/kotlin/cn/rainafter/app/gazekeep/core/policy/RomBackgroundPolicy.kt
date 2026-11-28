package cn.rainafter.app.gazekeep.core.policy

/**
 * 需额外引导用户放开后台省电策略的 ROM 家族。
 * 仅 Xiaomi 系保留已真机实测的私有策略页直达，其余未实机验证家族统一直达系统应用详情页。
 */
enum class RomBackgroundPolicy {
  Xiaomi,
  Oplus,
  Vivo,
  Samsung,
  HuaweiHonor,
}

fun resolveRomBackgroundPolicy(manufacturer: String, brand: String = ""): RomBackgroundPolicy? {
  val identifiers = listOf(manufacturer, brand)
    .map { it.trim().lowercase() }
    .filter { it.isNotEmpty() }
  for (id in identifiers) {
    when (id) {
      "xiaomi", "redmi", "poco", "blackshark" -> return RomBackgroundPolicy.Xiaomi
      "oppo", "realme", "oneplus", "oplus" -> return RomBackgroundPolicy.Oplus
      "vivo", "iqoo" -> return RomBackgroundPolicy.Vivo
      "samsung" -> return RomBackgroundPolicy.Samsung
      "huawei", "honor" -> return RomBackgroundPolicy.HuaweiHonor
    }
  }
  return null
}
