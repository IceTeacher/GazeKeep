package cn.rainafter.app.gazekeep.controller

import android.content.Context
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import cn.rainafter.app.gazekeep.ui.contract.SystemInfo

internal fun buildSystemInfo(
  versionName: String,
  versionCode: Long,
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
    appVersion = "v$versionName ($versionCode)",
    androidVersion = "Android $release (API $sdkInt)",
    deviceModel = deviceModel,
    systemFingerprint = fingerprint.ifBlank { "-" },
  )
}

internal fun readSystemInfo(context: Context): SystemInfo {
  val pkgInfo = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0)
  }.getOrNull()
  val versionName = pkgInfo?.versionName ?: "1.0"
  val versionCode = pkgInfo?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 1L
  return buildSystemInfo(
    versionName = versionName,
    versionCode = versionCode,
    release = Build.VERSION.RELEASE ?: "-",
    sdkInt = Build.VERSION.SDK_INT,
    manufacturer = Build.MANUFACTURER ?: "",
    model = Build.MODEL ?: "",
    fingerprint = Build.FINGERPRINT ?: "-",
  )
}
