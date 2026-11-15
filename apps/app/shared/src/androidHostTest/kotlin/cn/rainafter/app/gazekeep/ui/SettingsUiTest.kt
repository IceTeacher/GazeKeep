package cn.rainafter.app.gazekeep.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import cn.rainafter.app.gazekeep.core.model.AppFilterMode
import cn.rainafter.app.gazekeep.ui.contract.AppLanguage
import cn.rainafter.app.gazekeep.ui.testing.UiTestSession
import cn.rainafter.app.gazekeep.ui.testing.launchApp
import cn.rainafter.app.gazekeep.ui.testing.readyState
import cn.rainafter.app.gazekeep.ui.testing.text
import gazekeep.shared.generated.resources.Res
import gazekeep.shared.generated.resources.about_dep_libxposed
import gazekeep.shared.generated.resources.about_feedback
import gazekeep.shared.generated.resources.about_github
import gazekeep.shared.generated.resources.about_website
import gazekeep.shared.generated.resources.nav_settings
import gazekeep.shared.generated.resources.settings_about
import gazekeep.shared.generated.resources.settings_app_count
import gazekeep.shared.generated.resources.settings_app_scope
import gazekeep.shared.generated.resources.settings_app_scope_all
import gazekeep.shared.generated.resources.settings_app_scope_blacklist
import gazekeep.shared.generated.resources.settings_app_scope_blacklist_summary
import gazekeep.shared.generated.resources.settings_app_scope_whitelist
import gazekeep.shared.generated.resources.settings_app_scope_whitelist_summary
import gazekeep.shared.generated.resources.settings_apps
import gazekeep.shared.generated.resources.settings_back
import gazekeep.shared.generated.resources.settings_background_policy
import gazekeep.shared.generated.resources.settings_blacklist_empty
import gazekeep.shared.generated.resources.settings_hold_10
import gazekeep.shared.generated.resources.settings_hold_unlimited
import gazekeep.shared.generated.resources.settings_language
import gazekeep.shared.generated.resources.settings_language_english
import gazekeep.shared.generated.resources.settings_language_system
import gazekeep.shared.generated.resources.settings_low_battery_level
import gazekeep.shared.generated.resources.settings_low_battery_off
import gazekeep.shared.generated.resources.settings_low_battery_threshold
import gazekeep.shared.generated.resources.settings_low_battery_threshold_summary
import gazekeep.shared.generated.resources.settings_max_hold
import gazekeep.shared.generated.resources.settings_need_self_test
import gazekeep.shared.generated.resources.settings_power_save_pause
import gazekeep.shared.generated.resources.settings_privacy
import gazekeep.shared.generated.resources.settings_self_test
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "+zh-rCN")
class SettingsUiTest {
  @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

  private fun UiTestSession.choose(title: String, option: String) {
    click(title)
    // 下拉项绘制在页面浮层中，与当前值同名时取浮层里最后绘制的可点击项。
    rule.onAllNodes(hasText(option) and hasClickAction()).onLast().performClick()
    rule.waitForIdle()
  }

  @Test
  fun maxHoldCanBeSetToUnlimitedAndBack() {
    val app = rule.launchApp(readyState())
    app.openTab(text(Res.string.nav_settings))
    app.choose(text(Res.string.settings_max_hold), text(Res.string.settings_hold_unlimited))
    assertEquals(0, app.controller.state.value.config.maxHoldMinutes)
    app.choose(text(Res.string.settings_max_hold), text(Res.string.settings_hold_10))
    assertEquals(10, app.controller.state.value.config.maxHoldMinutes)
  }

  @Test
  fun appScopeSwitchesModeAndShowsListEntryOnlyWhenScoped() {
    val app = rule.launchApp(readyState())
    app.openTab(text(Res.string.nav_settings))
    app.assertShown(text(Res.string.settings_app_scope_whitelist))
    app.assertShown(text(Res.string.settings_app_count, 0))

    app.choose(text(Res.string.settings_app_scope), text(Res.string.settings_app_scope_blacklist))
    assertEquals(AppFilterMode.Blacklist, app.controller.state.value.config.appFilterMode)
    app.assertShown(text(Res.string.settings_app_scope_blacklist_summary))
    app.assertShown(text(Res.string.settings_blacklist_empty))

    app.choose(text(Res.string.settings_app_scope), text(Res.string.settings_app_scope_all))
    assertEquals(AppFilterMode.All, app.controller.state.value.config.appFilterMode)
    app.assertNotShown(text(Res.string.settings_apps))

    app.choose(text(Res.string.settings_app_scope), text(Res.string.settings_app_scope_whitelist))
    assertEquals(AppFilterMode.Whitelist, app.controller.state.value.config.appFilterMode)
    app.assertShown(text(Res.string.settings_apps))
  }

  @Test
  fun languageFollowsSystemByDefaultAndSwitchesThroughPlatform() {
    val app = rule.launchApp(readyState())
    app.openTab(text(Res.string.nav_settings))
    app.assertShown(text(Res.string.settings_language))
    app.assertShown(text(Res.string.settings_language_system))
    app.choose(text(Res.string.settings_language), text(Res.string.settings_language_english))
    assertEquals(listOf("setAppLanguage:English"), app.actions.calls)
    assertEquals(AppLanguage.English, app.actions.appLanguage())
  }

  @Test
  fun backgroundPolicyEntryOpensRomSettingsWhenPlatformHasIt() {
    val app = rule.launchApp(readyState(), romBackgroundPolicy = true)
    app.openTab(text(Res.string.nav_settings))
    app.click(text(Res.string.settings_background_policy))
    assertEquals(listOf("openRomBackgroundPolicy"), app.actions.calls)
  }

  @Test
  fun backgroundPolicyEntryHiddenWithoutRomSettings() {
    val app = rule.launchApp(readyState())
    app.openTab(text(Res.string.nav_settings))
    app.assertNotShown(text(Res.string.settings_background_policy))
  }

  @Test
  fun languageEntryHiddenWhenHostControlsLanguage() {
    val app = rule.launchApp(readyState(), language = null)
    app.openTab(text(Res.string.nav_settings))
    app.assertNotShown(text(Res.string.settings_language))
  }

  @Test
  fun selfTestEntryExplainsWhyServiceIsNotReady() {
    val app = rule.launchApp(readyState().copy(selfTestPassed = false))
    app.openTab(text(Res.string.nav_settings))
    app.assertShown(text(Res.string.settings_self_test))
    app.assertShown(text(Res.string.settings_need_self_test))
  }

  @Test
  fun settingsTabNoLongerListsPrivacy() {
    val app = rule.launchApp(readyState())
    app.openTab(text(Res.string.nav_settings))
    // 隐私页只从首页隐私卡片与关于页进入。
    app.assertShown(text(Res.string.settings_about))
    app.assertNotShown(text(Res.string.settings_privacy))
  }

  @Test
  fun aboutLinksOpenProjectRepositoryFeedbackAndWebsite() {
    val app = rule.launchApp(readyState())
    app.openTab(text(Res.string.nav_settings))
    app.click(text(Res.string.settings_about))
    app.click(text(Res.string.about_github))
    app.click(text(Res.string.about_feedback))
    app.click(text(Res.string.about_website))
    assertEquals(
      listOf(
        "openUrl:https://github.com/IceTeacher/GazeKeep",
        "openUrl:https://github.com/IceTeacher/GazeKeep/issues/24",
        "openUrl:https://gazekeep.rainafter.cn",
      ),
      app.actions.calls,
    )
  }

  @Test
  @Config(qualifiers = "+zh-rCN-land")
  fun aboutBackButtonStaysPinnedWhileScrolling() {
    val app = rule.launchApp(readyState())
    app.openTab(text(Res.string.nav_settings))
    app.click(text(Res.string.settings_about))
    val back = rule.onNodeWithContentDescription(text(Res.string.settings_back))
    val before = back.getUnclippedBoundsInRoot()
    // 横屏下内容超出一屏，滚到最后一项确保页面确实发生了滚动。
    rule.onNodeWithText(text(Res.string.about_dep_libxposed)).performScrollTo()
    rule.waitForIdle()
    assertEquals(before, back.getUnclippedBoundsInRoot())
  }

  @Test
  fun powerSaveAndLowBatterySettingsUpdateConfig() {
    val app = rule.launchApp(readyState())
    app.openTab(text(Res.string.nav_settings))
    val powerSave = text(Res.string.settings_power_save_pause)
    rule.onNodeWithText(powerSave).performScrollTo()
    app.click(powerSave)
    assertEquals(false, app.controller.state.value.config.pauseOnPowerSave)

    val lowBattery = text(Res.string.settings_low_battery_threshold)
    rule.onNodeWithText(lowBattery).performScrollTo()
    app.click(lowBattery)
    app.click(text(Res.string.settings_low_battery_level, 20))
    assertEquals(20, app.controller.state.value.config.lowBatteryThreshold)
    app.assertShown(text(Res.string.settings_low_battery_level, 20))
    app.assertShown(text(Res.string.settings_low_battery_threshold_summary))

    app.click(lowBattery)
    app.click(text(Res.string.settings_low_battery_off))
    assertEquals(0, app.controller.state.value.config.lowBatteryThreshold)
  }

  @Test
  @Config(qualifiers = "+en", fontScale = 1.3f)
  fun englishLargeFontScopeSummaryRemainsReadable() {
    val base = readyState()
    val app = rule.launchApp(base.copy(config = base.config.copy(appFilterMode = AppFilterMode.Whitelist)))
    app.openTab(text(Res.string.nav_settings))
    rule.onNodeWithText(text(Res.string.settings_app_scope)).performScrollTo()
    val summaryNode = rule.onNodeWithText(text(Res.string.settings_app_scope_whitelist_summary))
    summaryNode.assertIsDisplayed()
    val titleBounds = rule.onNodeWithText(text(Res.string.settings_app_scope)).getUnclippedBoundsInRoot()
    val summaryBounds = summaryNode.getUnclippedBoundsInRoot()
    assertTrue((summaryBounds.right - summaryBounds.left) >= (titleBounds.right - titleBounds.left) * 0.8f)
  }
}
