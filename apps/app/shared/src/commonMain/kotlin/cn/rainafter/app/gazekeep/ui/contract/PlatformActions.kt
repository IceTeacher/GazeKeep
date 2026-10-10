package cn.rainafter.app.gazekeep.ui.contract

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

interface PlatformActions {
  fun requestCameraPermission()
  fun requestNotificationPermission()
  fun openAccessibilitySettings()
  fun requestShizukuPermission()
  fun openShizukuApp()
  fun openUrl(url: String)
  fun shareLogs(text: String)
  fun exportLogs(text: String)

  @Composable fun HandleBack(enabled: Boolean, onBack: () -> Unit)

  @Composable fun SelfTestPreview(modifier: Modifier)

  @Composable fun AppIcon(packageName: String, modifier: Modifier)
}
