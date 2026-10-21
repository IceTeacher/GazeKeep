package cn.rainafter.app.gazekeep.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import cn.rainafter.app.gazekeep.ui.testing.FakePlatformActions
import cn.rainafter.app.gazekeep.ui.testing.launchApp
import cn.rainafter.app.gazekeep.ui.testing.readyState
import cn.rainafter.app.gazekeep.ui.testing.text
import gazekeep.shared.generated.resources.Res
import gazekeep.shared.generated.resources.app_title
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "+zh-rCN")
class NavigationUiTest {
  @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun backReturnsThroughTheStackToHome() {
    val app = rule.launchApp(readyState())
    app.openTab(text(Res.string.nav_settings))
    app.click(text(Res.string.settings_backend))
    app.assertShown(text(Res.string.settings_backend_order))
    app.pressBack()
    app.assertNotShown(text(Res.string.settings_backend_order))
    app.assertShown(text(Res.string.settings_self_test))
    app.pressBack()
    app.assertShown(text(Res.string.app_title))
    app.assertNotShown(text(Res.string.settings_self_test))
  }

  @Test
  fun selfTestStartsOnEntryAndIsCancelledWithPreviewReleasedOnLeave() {
    val app = rule.launchApp(readyState())
    app.openTab(text(Res.string.nav_settings))
    app.click(text(Res.string.settings_self_test))
    assertEquals("beginSelfTest", app.controller.calls.last())
    rule.onNodeWithTag(FakePlatformActions.SELF_TEST_PREVIEW).assertExists()
    assertTrue(app.actions.previewMounted)

    app.pressBack()
    assertEquals("cancelSelfTest", app.controller.calls.last())
    assertFalse(app.actions.previewMounted)
  }

  @Test
  fun openingAppListLoadsApps() {
    val app = rule.launchApp(readyState())
    app.openTab(text(Res.string.nav_settings))
    app.click(text(Res.string.settings_apps))
    assertEquals("loadApps", app.controller.calls.last())
  }
}
