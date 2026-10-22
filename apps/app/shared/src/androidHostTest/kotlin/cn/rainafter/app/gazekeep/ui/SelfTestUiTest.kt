package cn.rainafter.app.gazekeep.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cn.rainafter.app.gazekeep.core.model.Attention
import cn.rainafter.app.gazekeep.ui.contract.SelfTestState
import cn.rainafter.app.gazekeep.ui.contract.SelfTestStep
import cn.rainafter.app.gazekeep.ui.testing.FakePlatformActions
import cn.rainafter.app.gazekeep.ui.testing.UiTestSession
import cn.rainafter.app.gazekeep.ui.testing.launchApp
import cn.rainafter.app.gazekeep.ui.testing.readyState
import cn.rainafter.app.gazekeep.ui.testing.text
import gazekeep.shared.generated.resources.Res
import gazekeep.shared.generated.resources.app_title
import gazekeep.shared.generated.resources.m0_capturing
import gazekeep.shared.generated.resources.m0_result_unknown
import gazekeep.shared.generated.resources.nav_settings
import gazekeep.shared.generated.resources.self_test_camera_permission
import gazekeep.shared.generated.resources.self_test_capture_step_1
import gazekeep.shared.generated.resources.self_test_capture_step_2
import gazekeep.shared.generated.resources.self_test_complete
import gazekeep.shared.generated.resources.self_test_enable
import gazekeep.shared.generated.resources.self_test_last_result
import gazekeep.shared.generated.resources.self_test_retry_looking_hint
import gazekeep.shared.generated.resources.self_test_status_current
import gazekeep.shared.generated.resources.self_test_status_done
import gazekeep.shared.generated.resources.self_test_status_pending
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
class SelfTestUiTest {
  @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

  private fun UiTestSession.openSelfTest() {
    openTab(text(Res.string.nav_settings))
    click(text(Res.string.settings_self_test))
  }

  private fun button(label: String) = rule.onNode(hasText(label) and hasClickAction(), useUnmergedTree = false)

  @Test
  fun withoutCameraPermissionOffersGrantAndNoPreview() {
    val app = rule.launchApp(readyState().copy(cameraGranted = false, selfTestPassed = false))
    app.openSelfTest()
    rule.onNodeWithTag(FakePlatformActions.SELF_TEST_PREVIEW).assertDoesNotExist()
    app.click(text(Res.string.self_test_camera_permission))
    assertEquals(listOf("requestCameraPermission"), app.actions.calls)
  }

  @Test
  fun unknownResultKeepsFirstStepAndAsksToRetry() {
    val app = rule.launchApp(readyState().copy(selfTestPassed = false))
    app.controller.onCapture = { it.copy(attention = Attention.Unknown, lastStepFailed = true) }
    app.openSelfTest()
    app.click(text(Res.string.self_test_capture_step_1))
    assertTrue("captureSelfTest" in app.controller.calls)
    app.assertShown(text(Res.string.self_test_last_result, text(Res.string.m0_result_unknown)))
    app.assertShown(text(Res.string.self_test_retry_looking_hint))
    app.assertShown(text(Res.string.self_test_status_current))
    app.assertShown(text(Res.string.self_test_status_pending))
    app.assertShown(text(Res.string.self_test_capture_step_1))
  }

  @Test
  fun lookingThenAwayCompletesAndEnablingReturnsHome() {
    val app = rule.launchApp(readyState().copy(selfTestPassed = false))
    app.controller.onCapture = { state ->
      when (state.step) {
        SelfTestStep.Looking -> state.copy(step = SelfTestStep.Away, attention = Attention.Looking)
        else -> state.copy(step = SelfTestStep.Complete, attention = Attention.Away)
      }
    }
    app.openSelfTest()
    app.click(text(Res.string.self_test_capture_step_1))
    app.assertShown(text(Res.string.self_test_status_done))
    app.click(text(Res.string.self_test_capture_step_2))
    app.assertShown(text(Res.string.self_test_complete))
    // 完成后不再占用相机预览。
    rule.onNodeWithTag(FakePlatformActions.SELF_TEST_PREVIEW).assertDoesNotExist()
    app.click(text(Res.string.self_test_enable))
    assertTrue("setEnabled:true" in app.controller.calls)
    app.assertShown(text(Res.string.app_title))
  }

  @Test
  fun captureButtonIsDisabledWhileRunning() {
    val app = rule.launchApp(readyState(), SelfTestState(running = true))
    app.openSelfTest()
    app.assertShown(text(Res.string.m0_capturing))
    button(text(Res.string.m0_capturing)).assertIsNotEnabled()
  }
}
