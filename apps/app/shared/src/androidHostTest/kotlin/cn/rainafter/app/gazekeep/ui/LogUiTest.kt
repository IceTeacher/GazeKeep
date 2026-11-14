package cn.rainafter.app.gazekeep.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import cn.rainafter.app.gazekeep.ui.contract.LogEntry
import cn.rainafter.app.gazekeep.ui.contract.LogLevel
import cn.rainafter.app.gazekeep.ui.testing.UiTestSession
import cn.rainafter.app.gazekeep.ui.testing.launchApp
import cn.rainafter.app.gazekeep.ui.testing.readyState
import cn.rainafter.app.gazekeep.ui.testing.text
import gazekeep.shared.generated.resources.Res
import gazekeep.shared.generated.resources.logs_action_export
import gazekeep.shared.generated.resources.logs_action_filter
import gazekeep.shared.generated.resources.logs_action_more
import gazekeep.shared.generated.resources.logs_clear_dialog_confirm
import gazekeep.shared.generated.resources.logs_clear_dialog_title
import gazekeep.shared.generated.resources.logs_copied
import gazekeep.shared.generated.resources.logs_empty
import gazekeep.shared.generated.resources.logs_level_error
import gazekeep.shared.generated.resources.logs_menu_clear
import gazekeep.shared.generated.resources.logs_menu_live
import gazekeep.shared.generated.resources.logs_new_entries
import gazekeep.shared.generated.resources.logs_search_placeholder
import gazekeep.shared.generated.resources.nav_settings
import gazekeep.shared.generated.resources.settings_dialog_cancel
import gazekeep.shared.generated.resources.settings_view_logs
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
class LogUiTest {
  @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

  /** 虚构样本，最新在前；条数超过一屏，才能区分“跟随到顶部”与“停在原处”。 */
  private fun entries(from: Int, to: Int) = (to downTo from).map { n ->
    LogEntry(
      id = "entry-$n",
      timestampMs = 1_700_000_000_000L + n * 1_000L,
      level = LogLevel.Debug,
      tag = "Test",
      source = "cn.example.source",
      target = "system",
      message = "sample-message-$n",
    )
  }

  private fun openLogs(): UiTestSession {
    val ready = readyState()
    val app = rule.launchApp(ready.copy(config = ready.config.copy(debugLogEnabled = true), logs = entries(1, 30)))
    app.openTab(text(Res.string.nav_settings))
    app.click(text(Res.string.settings_view_logs))
    return app
  }

  private fun UiTestSession.append(from: Int, to: Int) {
    controller.state.value = controller.state.value.let { it.copy(logs = entries(from, to) + it.logs) }
    rule.waitForIdle()
  }

  private fun toggleLive() {
    rule.onNodeWithContentDescription(text(Res.string.logs_action_more)).performClick()
    rule.waitForIdle()
    rule.onNodeWithText(text(Res.string.logs_menu_live)).performClick()
    rule.waitForIdle()
  }

  private fun displayed(message: String) = rule.onNodeWithText(message).assertIsDisplayed()

  @Test
  fun liveLogsFollowNewestByDefault() {
    val app = openLogs()
    displayed("sample-message-30")
    app.append(31, 32)
    displayed("sample-message-32")
    app.assertNotShown(text(Res.string.logs_new_entries, 2))
  }

  @Test
  fun browsingHistoryIsNotInterruptedAndPillReturnsToNewest() {
    val app = openLogs()
    rule.onNode(hasScrollToIndexAction()).performScrollToIndex(15)
    rule.waitForIdle()
    displayed("sample-message-15")

    app.append(31, 33)
    // 新日志不把正在阅读的条目挤走，只浮出未读胶囊。
    displayed("sample-message-15")
    app.assertNotShown("sample-message-33")
    app.click(text(Res.string.logs_new_entries, 3))

    displayed("sample-message-33")
    app.assertNotShown(text(Res.string.logs_new_entries, 3))
  }

  @Test
  fun pausingLiveLogsFreezesListUntilResumed() {
    val app = openLogs()
    toggleLive()
    app.append(31, 31)
    app.assertNotShown("sample-message-31")
    app.assertShown(text(Res.string.logs_new_entries, 1))

    toggleLive()
    displayed("sample-message-31")
    app.assertNotShown(text(Res.string.logs_new_entries, 1))
  }

  @Test
  fun exportWhilePausedUsesLatestLogs() {
    val app = openLogs()
    toggleLive()
    app.append(31, 31)
    rule.onNodeWithContentDescription(text(Res.string.logs_action_export)).performClick()
    rule.waitForIdle()
    val exported = checkNotNull(app.actions.exportedText)
    assertTrue("sample-message-31" in exported)
    assertEquals(31, exported.lines().size)
  }

  @Test
  fun clearLogsRequiresConfirmationAndRespectsCancel() {
    val app = openLogs()
    rule.onNodeWithContentDescription(text(Res.string.logs_action_more)).performClick()
    rule.waitForIdle()
    app.click(text(Res.string.logs_menu_clear))
    app.assertShown(text(Res.string.logs_clear_dialog_title))
    app.click(text(Res.string.settings_dialog_cancel))
    assertTrue("clearLogs" !in app.controller.calls)
    displayed("sample-message-30")

    rule.onNodeWithContentDescription(text(Res.string.logs_action_more)).performClick()
    rule.waitForIdle()
    app.click(text(Res.string.logs_menu_clear))
    app.click(text(Res.string.logs_clear_dialog_confirm))
    assertTrue("clearLogs" in app.controller.calls)
    app.assertShown(text(Res.string.logs_empty))
  }

  @Test
  fun tappingLogEntryCopiesSingleFormattedLine() {
    val app = openLogs()
    app.click("sample-message-30")
    val copied = checkNotNull(app.actions.copiedText)
    assertTrue("sample-message-30" in copied)
    app.assertShown(text(Res.string.logs_copied))
  }

  @Test
  fun openingFilterAndMorePopupsFromSearchWorksCleanly() {
    val app = openLogs()
    rule.onNodeWithText(text(Res.string.logs_search_placeholder)).performTextInput("sample")
    rule.onNodeWithContentDescription(text(Res.string.logs_action_filter)).performClick()
    rule.waitForIdle()
    rule.onNodeWithText(text(Res.string.logs_level_error)).assertIsDisplayed()
    rule.onNodeWithText(text(Res.string.logs_level_error)).performClick()
    rule.waitForIdle()
    rule.onNodeWithContentDescription(text(Res.string.logs_action_more)).performClick()
    rule.waitForIdle()
    rule.onNodeWithText(text(Res.string.logs_menu_clear)).assertIsDisplayed()
  }
}
