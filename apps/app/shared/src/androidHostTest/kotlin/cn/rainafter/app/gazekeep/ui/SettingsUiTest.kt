package cn.rainafter.app.gazekeep.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import cn.rainafter.app.gazekeep.ui.testing.UiTestSession
import cn.rainafter.app.gazekeep.ui.testing.launchApp
import cn.rainafter.app.gazekeep.ui.testing.readyState
import cn.rainafter.app.gazekeep.ui.testing.text
import gazekeep.shared.generated.resources.Res
import gazekeep.shared.generated.resources.nav_settings
import gazekeep.shared.generated.resources.settings_hold_10
import gazekeep.shared.generated.resources.settings_hold_unlimited
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

  private fun UiTestSession.chooseMaxHold(option: String) {
    click(text(Res.string.settings_max_hold))
    // 下拉项绘制在页面浮层中，与当前值同名时取浮层里最后绘制的可点击项。
    rule.onAllNodes(hasText(option) and hasClickAction()).onLast().performClick()
    rule.waitForIdle()
  }

  @Test
  fun maxHoldCanBeSetToUnlimitedAndBack() {
    val app = rule.launchApp(readyState())
    app.openTab(text(Res.string.nav_settings))
    app.chooseMaxHold(text(Res.string.settings_hold_unlimited))
    assertEquals(0, app.controller.state.value.config.maxHoldMinutes)
    app.chooseMaxHold(text(Res.string.settings_hold_10))
    assertEquals(10, app.controller.state.value.config.maxHoldMinutes)
  }

  @Test
  fun selfTestEntryExplainsWhyServiceIsNotReady() {
    val app = rule.launchApp(readyState().copy(selfTestPassed = false))
    app.openTab(text(Res.string.nav_settings))
    app.assertShown(text(Res.string.settings_self_test))
    app.assertShown(text(Res.string.settings_need_self_test))
  }
}
