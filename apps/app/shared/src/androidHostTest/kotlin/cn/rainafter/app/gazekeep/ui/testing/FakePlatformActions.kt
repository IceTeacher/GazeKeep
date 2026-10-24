package cn.rainafter.app.gazekeep.ui.testing

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import cn.rainafter.app.gazekeep.ui.contract.AppLanguage
import cn.rainafter.app.gazekeep.ui.contract.PlatformActions

/** 平台能力的测试实现：返回键走真实 BackHandler，自检预览与应用图标用无内容的占位块，截图不含相机画面。 */
class FakePlatformActions(language: AppLanguage? = AppLanguage.System) : PlatformActions {
  val calls = mutableListOf<String>()
  private var language by mutableStateOf(language)
  var previewMounted = false
    private set

  override fun requestCameraPermission() {
    calls += "requestCameraPermission"
  }

  override fun requestNotificationPermission() {
    calls += "requestNotificationPermission"
  }

  override fun openAccessibilitySettings() {
    calls += "openAccessibilitySettings"
  }

  override fun requestShizukuPermission() {
    calls += "requestShizukuPermission"
  }

  override fun openShizukuApp() {
    calls += "openShizukuApp"
  }

  override fun openUrl(url: String) {
    calls += "openUrl:$url"
  }

  override fun shareLogs(text: String) {
    calls += "shareLogs"
  }

  override fun exportLogs(text: String) {
    calls += "exportLogs"
  }

  override fun appLanguage(): AppLanguage? = language

  override fun setAppLanguage(language: AppLanguage) {
    calls += "setAppLanguage:$language"
    this.language = language
  }

  // 固定 UTC 格式，截图不随运行机器时区变化。
  override fun formatLogTimestamp(epochMillis: Long): String =
    java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")
      .withZone(java.time.ZoneOffset.UTC)
      .format(java.time.Instant.ofEpochMilli(epochMillis))

  @Composable override fun HandleBack(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled, onBack)
  }

  @Composable override fun SelfTestPreview(modifier: Modifier) {
    DisposableEffect(Unit) {
      previewMounted = true
      onDispose { previewMounted = false }
    }
    Box(modifier.background(Color(0xFF2B3A42)).testTag(SELF_TEST_PREVIEW))
  }

  @Composable override fun AppIcon(packageName: String, modifier: Modifier) {
    Box(modifier.background(Color(0xFF80E8CA)))
  }

  companion object {
    const val SELF_TEST_PREVIEW = "self-test-preview"
  }
}
