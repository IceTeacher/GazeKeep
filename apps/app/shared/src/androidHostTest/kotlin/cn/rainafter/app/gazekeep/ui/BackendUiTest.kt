package cn.rainafter.app.gazekeep.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.core.model.BackendLock
import cn.rainafter.app.gazekeep.core.model.Readiness
import cn.rainafter.app.gazekeep.core.model.SetupStep
import cn.rainafter.app.gazekeep.ui.testing.launchApp
import cn.rainafter.app.gazekeep.ui.testing.readyState
import cn.rainafter.app.gazekeep.ui.testing.text
import gazekeep.shared.generated.resources.Res
import gazekeep.shared.generated.resources.backend_open_app_info
import gazekeep.shared.generated.resources.backend_restricted_hint
import gazekeep.shared.generated.resources.m2_accessibility_open_settings
import gazekeep.shared.generated.resources.nav_settings
import gazekeep.shared.generated.resources.settings_backend
import gazekeep.shared.generated.resources.settings_open_shizuku
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "+zh-rCN")
class BackendUiTest {
  @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun autoModeWithoutReadyBackendShowsSetupAndAppInfoActions() {
    val base = readyState()
    val app = rule.launchApp(
      base.copy(
        config = base.config.copy(backendLock = BackendLock.Auto),
        activeBackend = null,
        backends = mapOf(
          BackendId.Lsposed to Readiness.Unavailable("none"),
          BackendId.Shizuku to Readiness.NeedsSetup(SetupStep.StartShizuku),
          BackendId.Accessibility to Readiness.NeedsSetup(SetupStep.EnableAccessibilityService),
        ),
      ),
    )
    app.openTab(text(Res.string.nav_settings))
    app.click(text(Res.string.settings_backend))
    app.assertShown(text(Res.string.settings_open_shizuku))
    app.assertShown(text(Res.string.m2_accessibility_open_settings))
    app.assertShown(text(Res.string.backend_open_app_info))
    app.assertShown(text(Res.string.backend_restricted_hint))

    app.click(text(Res.string.backend_open_app_info))
    assertTrue("openAppDetailsSettings" in app.actions.calls)
  }

  @Test
  fun accessibilityLockShowsRestrictedHintAndAppInfoAction() {
    val base = readyState()
    val app = rule.launchApp(
      base.copy(
        config = base.config.copy(backendLock = BackendLock.Accessibility),
        activeBackend = null,
        backends = mapOf(
          BackendId.Lsposed to Readiness.Unavailable("none"),
          BackendId.Shizuku to Readiness.Ready,
          BackendId.Accessibility to Readiness.NeedsSetup(SetupStep.EnableAccessibilityService),
        ),
      ),
    )
    app.openTab(text(Res.string.nav_settings))
    app.click(text(Res.string.settings_backend))
    app.assertNotShown(text(Res.string.settings_open_shizuku))
    app.assertShown(text(Res.string.m2_accessibility_open_settings))
    app.assertShown(text(Res.string.backend_open_app_info))
    app.click(text(Res.string.backend_open_app_info))
    assertTrue("openAppDetailsSettings" in app.actions.calls)
  }
}
