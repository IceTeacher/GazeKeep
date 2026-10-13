package cn.rainafter.app.gazekeep.controller

import android.content.Context
import android.os.Build
import cn.rainafter.app.gazekeep.ui.contract.SystemInfo

internal fun buildSystemInfo(
  versionName: String,
  release: String,
  sdkInt: Int,
  manufacturer: String,
  model: String,
  fingerprint: String,
): SystemInfo {
  val trimmedManufacturer = manufacturer.trim()
  val trimmedModel = model.trim()
  val deviceModel = when {
    trimmedModel.isEmpty() -> trimmedManufacturer.ifEmpty { "-" }
    trimmedManufacturer.isEmpty() -> trimmedModel
    trimmedModel.startsWith(trimmedManufacturer, ignoreCase = true) -> trimmedModel
    else -> "$trimmedManufacturer $trimmedModel"
  }
  return SystemInfo(
    appVersion = "v$versionName",
    androidVersion = "Android $release (API $sdkInt)",
    deviceModel = deviceModel,
    systemFingerprint = fingerprint.ifBlank { "-" },
  )
}

internal fun readSystemInfo(context: Context): SystemInfo {
  val pkgInfo = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0)
  }.getOrNull()
  val versionName = pkgInfo?.versionName ?: "1.0.0"
  return buildSystemInfo(
    versionName = versionName,
    release = Build.VERSION.RELEASE ?: "-",
    sdkInt = Build.VERSION.SDK_INT,
    manufacturer = Build.MANUFACTURER ?: "",
    model = Build.MODEL ?: "",
    fingerprint = Build.FINGERPRINT ?: "-",
  )
}
