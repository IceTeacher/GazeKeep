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
  fun formatLogTimestamp(epochMillis: Long): String

  /** 当前应用内语言；返回 null 表示语言由宿主控制（如官网演示），设置页不提供切换。 */
  fun appLanguage(): AppLanguage?
  fun setAppLanguage(language: AppLanguage)

  @Composable fun HandleBack(enabled: Boolean, onBack: () -> Unit)

  @Composable fun SelfTestPreview(modifier: Modifier)

  @Composable fun AppIcon(packageName: String, modifier: Modifier)
}
