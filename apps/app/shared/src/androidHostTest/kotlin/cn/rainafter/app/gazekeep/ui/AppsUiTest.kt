package cn.rainafter.app.gazekeep.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import cn.rainafter.app.gazekeep.ui.contract.AppEntry
import cn.rainafter.app.gazekeep.ui.contract.AppListLoadState
import cn.rainafter.app.gazekeep.ui.testing.UiTestSession
import cn.rainafter.app.gazekeep.ui.testing.launchApp
import cn.rainafter.app.gazekeep.ui.testing.readyState
import cn.rainafter.app.gazekeep.ui.testing.text
import gazekeep.shared.generated.resources.Res
import gazekeep.shared.generated.resources.nav_settings
import gazekeep.shared.generated.resources.settings_apps
import gazekeep.shared.generated.resources.settings_apps_load_failed
import gazekeep.shared.generated.resources.settings_apps_selected
import gazekeep.shared.generated.resources.settings_no_apps
import gazekeep.shared.generated.resources.settings_retry
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 应用列表只使用虚构包名。 */
internal val SAMPLE_APPS = listOf(
  AppEntry("com.example.reader", "Reader"),
  AppEntry("com.example.browser", "Browser"),
  AppEntry("com.example.docs", "Docs"),
)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "+zh-rCN")
class AppsUiTest {
  @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

  private fun UiTestSession.openApps() {
    openTab(text(Res.string.nav_settings))
    click(text(Res.string.settings_apps))
  }

  private fun search(query: String) {
    rule.onNode(hasSetTextAction()).performTextInput(query)
    rule.waitForIdle()
  }

  @Test
  fun searchMatchesLabelOrPackageAndReportsNoMatch() {
    val app = rule.launchApp(readyState().copy(apps = SAMPLE_APPS, appsLoadState = AppListLoadState.Loaded))
    app.openApps()
    search("browser")
    app.assertShown("Browser")
    app.assertNotShown("Reader")
    search(".nomatch")
    app.assertShown(text(Res.string.settings_no_apps))
  }

  @Test
  fun selectingAnAppUpdatesScopeAndCount() {
    val app = rule.launchApp(readyState().copy(apps = SAMPLE_APPS, appsLoadState = AppListLoadState.Loaded))
    app.openApps()
    app.click("Reader")
    assertEquals(setOf("com.example.reader"), app.controller.state.value.config.packages)
    app.assertShown(text(Res.string.settings_apps_selected, 1))
  }

  @Test
  fun failedLoadOffersRetry() {
    val app = rule.launchApp(readyState().copy(appsLoadState = AppListLoadState.Failed))
    app.openApps()
    app.assertShown(text(Res.string.settings_apps_load_failed))
    val before = app.controller.calls.count { it == "loadApps" }
    app.click(text(Res.string.settings_retry))
    assertTrue(app.controller.calls.count { it == "loadApps" } > before)
  }
}
