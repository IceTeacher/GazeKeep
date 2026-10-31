package cn.rainafter.app.gazekeep.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import cn.rainafter.app.gazekeep.core.model.Attention
import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.ui.contract.AppListLoadState
import cn.rainafter.app.gazekeep.ui.contract.ControllerState
import cn.rainafter.app.gazekeep.ui.contract.LogEntry
import cn.rainafter.app.gazekeep.ui.contract.LogLevel
import cn.rainafter.app.gazekeep.ui.contract.SelfTestState
import cn.rainafter.app.gazekeep.ui.contract.SelfTestStep
import cn.rainafter.app.gazekeep.ui.testing.UiTestSession
import cn.rainafter.app.gazekeep.ui.testing.launchApp
import cn.rainafter.app.gazekeep.ui.testing.readyState
import cn.rainafter.app.gazekeep.ui.testing.text
import gazekeep.shared.generated.resources.Res
import gazekeep.shared.generated.resources.home_privacy_card_title
import gazekeep.shared.generated.resources.nav_settings
import gazekeep.shared.generated.resources.settings_about
import gazekeep.shared.generated.resources.settings_apps
import gazekeep.shared.generated.resources.settings_backend
import gazekeep.shared.generated.resources.settings_self_test
import gazekeep.shared.generated.resources.settings_view_logs
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test

/**
 * 关键页面截图基线：覆盖明暗、中英、字体放大与横屏的代表组合。
 * 数据全部为虚构样本；自检页使用无内容的占位预览，不含相机画面。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {
  @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

  private val running = readyState().let {
    it.copy(
      config = it.config.copy(enabled = true, whitelistPackages = setOf("com.example.reader")),
      serviceRunning = true,
      activeBackend = BackendId.Shizuku,
      lastDecision = Attention.Looking,
    )
  }

  private fun shoot(
    name: String,
    state: ControllerState = running,
    selfTest: SelfTestState = SelfTestState(),
    path: UiTestSession.() -> Unit = {
    },
  ) {
    val app = rule.launchApp(state, selfTest)
    app.path()
    app.capture(name)
  }

  private fun UiTestSession.settings(entry: Any? = null) {
    openTab(text(Res.string.nav_settings))
    if (entry is org.jetbrains.compose.resources.StringResource) click(text(entry))
  }

  @Test
  @Config(qualifiers = "+zh-rCN")
  fun homeStoppedLightZh() = shoot("home_stopped_light_zh", readyState())

  @Test
  @Config(qualifiers = "+zh-rCN-night")
  fun homeWorkingDarkZh() = shoot("home_working_dark_zh")

  @Test
  @Config(qualifiers = "+en")
  fun homeWorkingLightEn() = shoot("home_working_light_en")

  @Test
  @Config(qualifiers = "+zh-rCN")
  fun settingsLightZh() = shoot("settings_light_zh") { settings() }

  @Test
  @Config(qualifiers = "+zh-rCN")
  fun settingsLargeFontZh() {
    RuntimeEnvironment.setFontScale(1.3f)
    shoot("settings_font130_zh") { settings() }
  }

  @Test
  @Config(qualifiers = "+en")
  fun settingsBottomLightEn() = shoot("settings_bottom_light_en") {
    settings()
    // 语言入口位于首屏之下，滚到页尾覆盖语言与工具入口的英文排版。
    rule.onNodeWithText(text(Res.string.settings_about)).performScrollTo()
  }

  @Test
  @Config(qualifiers = "+zh-rCN-night")
  fun backendDarkZh() = shoot("backend_dark_zh") { settings(Res.string.settings_backend) }

  @Test
  @Config(qualifiers = "+zh-rCN")
  fun appsLightZh() = shoot(
    "apps_light_zh",
    running.copy(apps = SAMPLE_APPS, appsLoadState = AppListLoadState.Loaded),
  ) { settings(Res.string.settings_apps) }

  @Test
  @Config(qualifiers = "+zh-rCN")
  fun selfTestAwayStepLightZh() = shoot(
    "selftest_away_light_zh",
    readyState().copy(selfTestPassed = false),
    SelfTestState(step = SelfTestStep.Away, attention = Attention.Looking),
  ) { settings(Res.string.settings_self_test) }

  @Test
  @Config(qualifiers = "+zh-rCN-night")
  fun logsDarkZh() = shoot(
    "logs_dark_zh",
    running.copy(config = running.config.copy(debugLogEnabled = true), logs = SAMPLE_LOGS),
  ) { settings(Res.string.settings_view_logs) }

  @Test
  @Config(qualifiers = "+en")
  fun privacyLightEn() = shoot("privacy_light_en") { click(text(Res.string.home_privacy_card_title)) }

  @Test
  @Config(qualifiers = "+zh-rCN-land")
  fun aboutLandscapeZh() = shoot("about_land_zh") { settings(Res.string.settings_about) }
}

/** 日志样本只包含枚举类字段与虚构包名，与产品日志约束一致。 */
private val SAMPLE_LOGS = listOf(
  LogEntry("1", 1_760_000_000_000L, LogLevel.Info, "Decision", "Shizuku", "com.example.reader", "Looking, extended"),
  LogEntry("2", 1_760_000_030_000L, LogLevel.Info, "Decision", "Shizuku", "com.example.reader", "Away, yielded"),
  LogEntry("3", 1_760_000_060_000L, LogLevel.Warning, "Capture", "Shizuku", "com.example.docs", "Unknown: too dark"),
)
