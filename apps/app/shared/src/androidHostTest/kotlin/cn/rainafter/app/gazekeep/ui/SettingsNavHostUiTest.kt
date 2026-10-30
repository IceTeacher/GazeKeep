package cn.rainafter.app.gazekeep.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import cn.rainafter.app.gazekeep.ui.navigation.SettingsBackStack
import cn.rainafter.app.gazekeep.ui.navigation.SettingsNavHost
import cn.rainafter.app.gazekeep.ui.navigation.SettingsScreen
import cn.rainafter.app.gazekeep.ui.testing.FakeGazeKeepController
import cn.rainafter.app.gazekeep.ui.testing.FakePlatformActions
import cn.rainafter.app.gazekeep.ui.testing.UiTestSession
import cn.rainafter.app.gazekeep.ui.testing.readyState
import cn.rainafter.app.gazekeep.ui.testing.text
import gazekeep.shared.generated.resources.Res
import gazekeep.shared.generated.resources.app_title
import gazekeep.shared.generated.resources.nav_home
import gazekeep.shared.generated.resources.nav_settings
import gazekeep.shared.generated.resources.settings_apps
import gazekeep.shared.generated.resources.settings_backend
import gazekeep.shared.generated.resources.settings_backend_order
import gazekeep.shared.generated.resources.settings_self_test
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** 官网演示复用的导航宿主：外部换页、复位转场与不可进入页面。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "+zh-rCN")
class SettingsNavHostUiTest {
  @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

  private fun launch(unavailable: Set<SettingsScreen> = emptySet()): Pair<UiTestSession, SettingsBackStack> {
    val controller = FakeGazeKeepController(readyState())
    val actions = FakePlatformActions()
    val backStack = SettingsBackStack(mutableStateListOf(SettingsScreen.Home), unavailable)
    rule.setContent {
      MiuixTheme(controller = remember { ThemeController() }) {
        val state by controller.state.collectAsState()
        val test by controller.selfTest.collectAsState()
        SettingsNavHost(backStack, controller, actions, state, test)
      }
    }
    rule.waitForIdle()
    return UiTestSession(rule, controller, actions) to backStack
  }

  /** 暂停时钟时，测试线程的状态写入需要手动提交，才能在后续帧中重组。 */
  private fun show(action: () -> Unit) = rule.runOnIdle {
    action()
    Snapshot.sendApplyNotifications()
  }

  @Test
  fun homeShowsBottomBarWithBothTabs() {
    val (app, _) = launch()
    app.assertShown(text(Res.string.nav_home))
    app.assertShown(text(Res.string.nav_settings))
  }

  @Test
  fun externalShowSwitchesInTheNextFrameWithoutTransition() {
    val (app, backStack) = launch()
    rule.mainClock.autoAdvance = false
    show { backStack.show(SettingsScreen.Apps, animate = false) }
    rule.mainClock.advanceTimeBy(64)
    // 父页面在绘制回执后揭开遮罩，此时不能还停留在 450ms 转场的中途。
    app.assertShown(text(Res.string.settings_apps))
    app.assertNotShown(text(Res.string.app_title))
    rule.mainClock.autoAdvance = true
  }

  @Test
  fun restoreReturnsFromUserNavigationWithTransition() {
    val (app, backStack) = launch()
    app.openTab(text(Res.string.nav_settings))
    app.click(text(Res.string.settings_backend))
    app.assertShown(text(Res.string.settings_backend_order))

    rule.mainClock.autoAdvance = false
    show { backStack.show(SettingsScreen.Home, animate = true) }
    rule.mainClock.advanceTimeBy(120)
    // 转场途中新旧页面同时存在，说明是平滑过渡而不是硬切。
    app.assertShown(text(Res.string.app_title))
    app.assertShown(text(Res.string.settings_backend_order))
    rule.mainClock.autoAdvance = true
    rule.waitForIdle()
    app.assertNotShown(text(Res.string.settings_backend_order))
    assertEquals(SettingsScreen.Home, backStack.current)
    assertEquals(1, backStack.size)
  }

  @Test
  fun unavailableScreenCannotBeEntered() {
    val (app, backStack) = launch(unavailable = setOf(SettingsScreen.SelfTest))
    app.openTab(text(Res.string.nav_settings))
    app.click(text(Res.string.settings_self_test))
    assertEquals(SettingsScreen.Settings, backStack.current)
    assertFalse("beginSelfTest" in app.controller.calls)
  }
}
