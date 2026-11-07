package cn.rainafter.app.gazekeep.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import cn.rainafter.app.gazekeep.core.model.Attention
import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.ui.contract.UiNotice
import cn.rainafter.app.gazekeep.ui.testing.launchApp
import cn.rainafter.app.gazekeep.ui.testing.readyState
import cn.rainafter.app.gazekeep.ui.testing.text
import gazekeep.shared.generated.resources.Res
import gazekeep.shared.generated.resources.about_feedback
import gazekeep.shared.generated.resources.home_decision_looking
import gazekeep.shared.generated.resources.home_privacy_card_title
import gazekeep.shared.generated.resources.home_status_decision
import gazekeep.shared.generated.resources.home_status_stopped
import gazekeep.shared.generated.resources.home_status_waiting
import gazekeep.shared.generated.resources.home_status_waiting_camera
import gazekeep.shared.generated.resources.home_status_waiting_camera_hint
import gazekeep.shared.generated.resources.home_status_working
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
import kotlin.test.assertTrue

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

  @Test
  fun runningServiceWithoutActiveBackendIsNotShownAsWorking() {
    val running = readyState().copy(serviceRunning = true, activeBackend = null)
    val app = rule.launchApp(running.copy(config = running.config.copy(enabled = true)))
    app.assertShown(text(Res.string.home_status_waiting))
    app.assertNotShown(text(Res.string.home_status_working))
  }

  @Test
  fun feedbackEntryBelowPrivacyOpensIssuesLikeAboutPage() {
    val app = rule.launchApp(readyState())
    // 首页反馈入口紧跟隐私卡片，打开的地址与关于页的「问题反馈」相同。
    val privacy = rule.onNodeWithText(text(Res.string.home_privacy_card_title)).getUnclippedBoundsInRoot()
    val feedback = rule.onNodeWithText(text(Res.string.about_feedback)).getUnclippedBoundsInRoot()
    assertTrue(feedback.top > privacy.bottom)
    app.click(text(Res.string.about_feedback))
    assertEquals(listOf("openUrl:https://github.com/IceTeacher/GazeKeep/issues"), app.actions.calls)
  }

  @Test
  fun missingCameraPermissionExplainsTheRealReason() {
    val stopped = readyState().copy(cameraGranted = false, serviceRunning = false)
    val app = rule.launchApp(stopped.copy(config = stopped.config.copy(enabled = true)))
    app.assertShown(text(Res.string.home_status_waiting_camera))
    app.assertShown(text(Res.string.home_status_waiting_camera_hint))
    app.assertNotShown(text(Res.string.home_status_waiting))
  }
}
