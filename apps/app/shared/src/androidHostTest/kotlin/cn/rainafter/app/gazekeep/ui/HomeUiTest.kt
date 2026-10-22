package cn.rainafter.app.gazekeep.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import cn.rainafter.app.gazekeep.core.model.Attention
import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.ui.contract.UiNotice
import cn.rainafter.app.gazekeep.ui.testing.launchApp
import cn.rainafter.app.gazekeep.ui.testing.readyState
import cn.rainafter.app.gazekeep.ui.testing.text
import gazekeep.shared.generated.resources.Res
import gazekeep.shared.generated.resources.home_decision_looking
import gazekeep.shared.generated.resources.home_status_decision
import gazekeep.shared.generated.resources.home_status_stopped
import gazekeep.shared.generated.resources.home_status_waiting
import gazekeep.shared.generated.resources.m0_result_unknown
import gazekeep.shared.generated.resources.self_test_looking
import gazekeep.shared.generated.resources.settings_loading
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
class HomeUiTest {
  @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun showsLoadingUntilConfigurationIsRead() {
    rule.launchApp(readyState().copy(loaded = false)).assertShown(text(Res.string.settings_loading))
  }

  @Test
  fun tappingStoppedBannerEnablesTheService() {
    val app = rule.launchApp(readyState())
    app.click(text(Res.string.home_status_stopped))
    assertEquals(listOf("setEnabled:true"), app.controller.calls.filter { it.startsWith("setEnabled") })
    app.assertShown(text(Res.string.home_status_waiting))
  }

  @Test
  fun enablingWithoutSelfTestOpensSelfTest() {
    val app = rule.launchApp(readyState().copy(selfTestPassed = false, notice = UiNotice.NeedsSelfTest))
    app.click(text(Res.string.home_status_stopped))
    app.assertShown(text(Res.string.self_test_looking))
    assertEquals(listOf("setEnabled:true", "beginSelfTest"), app.controller.calls.filter { it != "cancelSelfTest" })
  }

  @Test
  fun workingStatusUsesProductResultLanguage() {
    val running = readyState().copy(serviceRunning = true, activeBackend = BackendId.Shizuku)
    val app = rule.launchApp(
      running.copy(config = running.config.copy(enabled = true), lastDecision = Attention.Looking),
    )
    app.assertShown(text(Res.string.home_status_decision, text(Res.string.home_decision_looking)))
    app.controller.state.value = app.controller.state.value.copy(lastDecision = Attention.Unknown)
    rule.waitForIdle()
    app.assertShown(text(Res.string.home_status_decision, text(Res.string.m0_result_unknown)))
  }
}
