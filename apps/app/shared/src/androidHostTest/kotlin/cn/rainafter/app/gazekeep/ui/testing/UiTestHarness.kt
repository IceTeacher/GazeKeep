package cn.rainafter.app.gazekeep.ui.testing

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.rules.ActivityScenarioRule
import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.core.model.Readiness
import cn.rainafter.app.gazekeep.core.model.SetupStep
import cn.rainafter.app.gazekeep.ui.App
import cn.rainafter.app.gazekeep.ui.contract.ControllerState
import cn.rainafter.app.gazekeep.ui.contract.SelfTestState
import cn.rainafter.app.gazekeep.ui.contract.SystemInfo
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import java.io.File

typealias ActivityComposeRule = AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>

/** 截图基线目录，随测试源码提交；Gradle 主机测试的工作目录是模块目录。 */
private val SCREENSHOTS = File("src/androidHostTest/screenshots")

/** 按当前测试语言读取 Compose 资源，断言不硬编码文案。 */
fun text(resource: StringResource, vararg args: Any): String = runBlocking { getString(resource, *args) }

/** 已加载、相机已授权且自检通过的典型状态；设备信息为虚构样本。 */
fun readyState(
  backends: Map<BackendId, Readiness> = mapOf(
    BackendId.Lsposed to Readiness.NeedsSetup(SetupStep.InstallFramework),
    BackendId.Shizuku to Readiness.Ready,
    BackendId.Accessibility to Readiness.NeedsSetup(SetupStep.EnableAccessibilityService),
  ),
) = ControllerState(
  loaded = true,
  cameraGranted = true,
  selfTestPassed = true,
  backends = backends,
  systemInfo = SystemInfo("v1.2.3", "Android 15 (API 35)", "Acme Phone 1", "Acme/demo/demo:15/TEST:user/release-keys"),
)

class UiTestSession(
  val rule: ActivityComposeRule,
  val controller: FakeGazeKeepController,
  val actions: FakePlatformActions,
) {
  fun pressBack() {
    rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
    rule.waitForIdle()
  }

  /**
   * 点击文本所在的可点击节点。滚动容器内的节点先滚入视口，再触发语义点击：
   * 底部导航栏覆盖在列表上方，坐标触摸可能落到导航栏而不是目标。
   */
  fun click(label: String) {
    val node = rule.onNodeWithText(label)
    val scrollable = node.fetchSemanticsNode().let { target ->
      generateSequence(target.parent) { it.parent }.any { SemanticsActions.ScrollBy in it.config }
    }
    if (scrollable) node.performScrollTo().performSemanticsAction(SemanticsActions.OnClick) else node.performClick()
    rule.waitForIdle()
  }

  /** 底部导航项不在滚动容器内，直接点击。 */
  fun openTab(label: String) {
    rule.onNodeWithText(label, useUnmergedTree = true).performClick()
    rule.waitForIdle()
  }

  /** miuix 顶栏同时绘制大标题与折叠标题，按“至少一个”判断页面。 */
  fun assertShown(label: String) {
    check(rule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()) { "未找到文本：$label" }
  }

  fun assertNotShown(label: String) {
    check(rule.onAllNodesWithText(label).fetchSemanticsNodes().isEmpty()) { "不应出现文本：$label" }
  }

  fun capture(name: String) {
    rule.waitForIdle()
    rule.onRoot().captureRoboImage(File(SCREENSHOTS, "$name.png").path)
  }
}

fun ActivityComposeRule.launchApp(state: ControllerState, selfTest: SelfTestState = SelfTestState()): UiTestSession {
  val controller = FakeGazeKeepController(state, selfTest)
  val actions = FakePlatformActions()
  setContent { App(controller, actions) }
  waitForIdle()
  return UiTestSession(this, controller, actions)
}
