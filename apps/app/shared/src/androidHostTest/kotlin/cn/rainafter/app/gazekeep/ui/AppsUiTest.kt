package cn.rainafter.app.gazekeep.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import cn.rainafter.app.gazekeep.core.model.AppFilterMode
import cn.rainafter.app.gazekeep.ui.contract.AppEntry
import cn.rainafter.app.gazekeep.ui.contract.AppListLoadState
import cn.rainafter.app.gazekeep.ui.testing.UiTestSession
import cn.rainafter.app.gazekeep.ui.testing.launchApp
import cn.rainafter.app.gazekeep.ui.testing.readyState
import cn.rainafter.app.gazekeep.ui.testing.text
import gazekeep.shared.generated.resources.Res
import gazekeep.shared.generated.resources.nav_settings
import gazekeep.shared.generated.resources.settings_apps
import gazekeep.shared.generated.resources.settings_apps_blacklist_title
import gazekeep.shared.generated.resources.settings_apps_clear
import gazekeep.shared.generated.resources.settings_apps_clear_confirm
import gazekeep.shared.generated.resources.settings_apps_clear_whitelist_message
import gazekeep.shared.generated.resources.settings_apps_hidden_system
import gazekeep.shared.generated.resources.settings_apps_load_failed
import gazekeep.shared.generated.resources.settings_apps_more
import gazekeep.shared.generated.resources.settings_apps_select_matches
import gazekeep.shared.generated.resources.settings_apps_selected
import gazekeep.shared.generated.resources.settings_apps_show_system
import gazekeep.shared.generated.resources.settings_apps_whitelist_title
import gazekeep.shared.generated.resources.settings_dialog_cancel
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

/** 带一个虚构系统应用的列表，用于系统应用显示开关。 */
private val APPS_WITH_SYSTEM = SAMPLE_APPS + AppEntry("com.example.gallery", "Gallery", isSystem = true)

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

  private fun openMenu() {
    rule.onNodeWithContentDescription(text(Res.string.settings_apps_more)).performClick()
    rule.waitForIdle()
  }

  private fun loaded(apps: List<AppEntry>, whitelist: Set<String> = emptySet()) = readyState().let {
    it.copy(
      apps = apps,
      appsLoadState = AppListLoadState.Loaded,
      config = it.config.copy(whitelistPackages = whitelist),
    )
  }

  @Test
  fun systemAppsAreHiddenByDefaultAndToggleIsPersisted() {
    val app = rule.launchApp(loaded(APPS_WITH_SYSTEM))
    app.openApps()
    app.assertShown("Reader")
    app.assertNotShown("Gallery")
    openMenu()
    app.click(text(Res.string.settings_apps_show_system))
    app.assertShown("Gallery")
    assertEquals(listOf("setShowSystemApps:true"), app.actions.calls.filter { it.startsWith("setShowSystemApps") })
  }

  @Test
  fun persistedPreferenceShowsSystemApps() {
    val app = rule.launchApp(loaded(APPS_WITH_SYSTEM), showSystemApps = true)
    app.openApps()
    app.assertShown("Gallery")
  }

  @Test
  fun selectedSystemAppStaysVisibleAfterUnchecking() {
    val app = rule.launchApp(loaded(APPS_WITH_SYSTEM, whitelist = setOf("com.example.gallery")))
    app.openApps()
    app.assertShown("Gallery")
    app.click("Gallery")
    assertEquals(emptySet(), app.controller.state.value.config.whitelistPackages)
    app.assertShown("Gallery")
  }

  @Test
  fun searchHitOnHiddenSystemAppOffersToShowIt() {
    val app = rule.launchApp(loaded(APPS_WITH_SYSTEM))
    app.openApps()
    search("gallery")
    app.assertShown(text(Res.string.settings_apps_hidden_system, 1))
    app.click(text(Res.string.settings_apps_show_system))
    app.assertShown("Gallery")
    assertTrue("setShowSystemApps:true" in app.actions.calls)
  }

  @Test
  fun selectSearchResultsAddsOnlyVisibleMatches() {
    val app = rule.launchApp(loaded(APPS_WITH_SYSTEM, whitelist = setOf("com.example.docs")))
    app.openApps()
    openMenu()
    app.assertNotShown(text(Res.string.settings_apps_select_matches))
    rule.onRoot().performClick()
    rule.waitForIdle()
    // "r" 命中 Reader、Browser 与隐藏的 Gallery，只选中可见的两个。
    search("r")
    openMenu()
    app.click(text(Res.string.settings_apps_select_matches))
    assertEquals(
      setOf("com.example.docs", "com.example.reader", "com.example.browser"),
      app.controller.state.value.config.whitelistPackages,
    )
  }

  @Test
  fun clearingTheScopeRequiresConfirmation() {
    val app = rule.launchApp(loaded(SAMPLE_APPS, whitelist = setOf("com.example.reader", "com.example.docs")))
    app.openApps()
    openMenu()
    app.click(text(Res.string.settings_apps_clear))
    app.assertShown(text(Res.string.settings_apps_clear_whitelist_message, 2))
    app.click(text(Res.string.settings_dialog_cancel))
    assertEquals(2, app.controller.state.value.config.whitelistPackages.size)
    openMenu()
    app.click(text(Res.string.settings_apps_clear))
    app.click(text(Res.string.settings_apps_clear_confirm))
    assertEquals(emptySet(), app.controller.state.value.config.whitelistPackages)
    app.assertShown(text(Res.string.settings_apps_selected, 0))
    openMenu()
    app.assertNotShown(text(Res.string.settings_apps_clear))
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
    app.assertShown(text(Res.string.settings_apps_whitelist_title))
    app.click("Reader")
    assertEquals(setOf("com.example.reader"), app.controller.state.value.config.whitelistPackages)
    app.assertShown(text(Res.string.settings_apps_selected, 1))
  }

  @Test
  fun blacklistScopeEditsOnlyTheBlacklist() {
    val state = readyState().copy(apps = SAMPLE_APPS, appsLoadState = AppListLoadState.Loaded)
    val app = rule.launchApp(
      state.copy(
        config = state.config.copy(
          appFilterMode = AppFilterMode.Blacklist,
          whitelistPackages = setOf("com.example.reader"),
        ),
      ),
    )
    app.openApps()
    app.assertShown(text(Res.string.settings_apps_blacklist_title))
    // 白名单中的 Reader 不显示为已排除，计数只来自黑名单。
    app.assertShown(text(Res.string.settings_apps_selected, 0))
    app.click("Browser")
    val config = app.controller.state.value.config
    assertEquals(setOf("com.example.browser"), config.blacklistPackages)
    assertEquals(setOf("com.example.reader"), config.whitelistPackages)
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
