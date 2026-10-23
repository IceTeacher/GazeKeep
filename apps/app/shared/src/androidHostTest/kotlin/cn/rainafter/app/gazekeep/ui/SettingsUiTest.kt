package cn.rainafter.app.gazekeep.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import cn.rainafter.app.gazekeep.ui.contract.AppLanguage
import cn.rainafter.app.gazekeep.ui.testing.UiTestSession
import cn.rainafter.app.gazekeep.ui.testing.launchApp
import cn.rainafter.app.gazekeep.ui.testing.readyState
import cn.rainafter.app.gazekeep.ui.testing.text
import gazekeep.shared.generated.resources.Res
import gazekeep.shared.generated.resources.about_dep_libxposed
import gazekeep.shared.generated.resources.about_github
import gazekeep.shared.generated.resources.about_website
import gazekeep.shared.generated.resources.nav_settings
import gazekeep.shared.generated.resources.settings_about
import gazekeep.shared.generated.resources.settings_back
import gazekeep.shared.generated.resources.settings_hold_10
import gazekeep.shared.generated.resources.settings_hold_unlimited
import gazekeep.shared.generated.resources.settings_language
import gazekeep.shared.generated.resources.settings_language_english
import gazekeep.shared.generated.resources.settings_language_system
import gazekeep.shared.generated.resources.settings_max_hold
import gazekeep.shared.generated.resources.settings_need_self_test
import gazekeep.shared.generated.resources.settings_self_test
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals

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
  fun aboutLinksOpenProjectRepositoryAndWebsite() {
    val app = rule.launchApp(readyState())
    app.openTab(text(Res.string.nav_settings))
    app.click(text(Res.string.settings_about))
    app.click(text(Res.string.about_github))
    app.click(text(Res.string.about_website))
    assertEquals(
      listOf("openUrl:https://github.com/IceTeacher/GazeKeep", "openUrl:https://gazekeep.rainafter.cn"),
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
}
