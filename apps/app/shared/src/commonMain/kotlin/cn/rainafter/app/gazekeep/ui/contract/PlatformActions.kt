package cn.rainafter.app.gazekeep.ui.contract

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

sealed interface ConfigTransferResult {
  data object Saved : ConfigTransferResult
  data object TooLarge : ConfigTransferResult
  data object Cancelled : ConfigTransferResult
  data object Failed : ConfigTransferResult
}

sealed interface ConfigReadResult {
  data class Loaded(val content: String) : ConfigReadResult
  data object Cancelled : ConfigReadResult
  data object TooLarge : ConfigReadResult
  data object Failed : ConfigReadResult
}

interface PlatformActions {
  fun requestCameraPermission()
  fun requestNotificationPermission()
  fun openAccessibilitySettings()
  fun openAppDetailsSettings()
  fun requestShizukuPermission()
  fun openShizukuApp()
  fun openUrl(url: String)
  fun copyToClipboard(text: String)
  fun shareLogs(text: String)
  fun exportLogs(text: String)
  fun formatLogTimestamp(epochMillis: Long): String

  /** 是否支持通过系统 SAF 导出与导入本地配置；官网演示返回 false。 */
  fun supportsConfigBackup(): Boolean
  fun exportConfig()
  fun importConfig()

  /** 当前应用内语言；返回 null 表示语言由宿主控制（如官网演示），设置页不提供切换。 */
  fun appLanguage(): AppLanguage?
  fun setAppLanguage(language: AppLanguage)

  /** 应用列表是否显示系统应用；只是列表显示偏好，跨启动保留，不影响生效范围。 */
  fun showSystemApps(): Boolean
  fun setShowSystemApps(show: Boolean)

  /** ROM 是否提供需用户放开的后台省电策略入口（如 HyperOS）；false 时设置页不显示该项。 */
  fun hasRomBackgroundPolicy(): Boolean
  fun openRomBackgroundPolicy()

  @Composable fun HandleBack(enabled: Boolean, onBack: () -> Unit)

  @Composable fun SelfTestPreview(modifier: Modifier)

  @Composable fun AppIcon(packageName: String, modifier: Modifier)
}
