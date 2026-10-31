package cn.rainafter.app.gazekeep.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** 设置在进程被杀后冷启动仍可回读。 */
@RunWith(AndroidJUnit4::class)
class ConfigPersistenceTest {
  private val session = DeviceSession()

  @Before fun setUp() = session.useChineseUi()

  @After fun tearDown() = session.close()

  /**
   * 选择后等待弹出菜单收起、且显示值变为目标值。显示值来自持久化后的配置流，
   * 据此确认写盘完成，避免紧接着强停时 DataStore 尚未落盘。
   */
  private fun chooseMaxHold(option: String, unrelated: String) {
    session.tap(session.label("settings_max_hold"))
    session.require(option).click()
    session.device.wait(Until.gone(By.text(unrelated)), 5_000L)
    assertNotNull(session.find(option), "未确认 $option 已保存")
  }

  @Test
  fun maxHoldSurvivesForceStopAndColdStart() {
    val presets = listOf("settings_hold_10", "settings_hold_30", "settings_hold_60").map(session::label)
    session.launch()
    session.openSettingsTab()
    session.scrollToText(session.label("settings_max_hold"))
    // 原值为自定义或无限制时无法经同一下拉无损恢复，此时不执行。
    val original = presets.firstOrNull { session.device.findObject(By.text(it)) != null }
    assumeTrue("测试前单次最长保亮不是预设值，无法无损恢复", original != null)
    val target = presets.first { it != original }
    session.onCleanup {
      session.launch()
      session.openSettingsTab()
      chooseMaxHold(original!!, presets.first { it != original && it != target })
    }

    chooseMaxHold(target, presets.first { it != original && it != target })
    session.forceStop()
    session.launch()
    session.openSettingsTab()
    session.scrollToText(session.label("settings_max_hold"))
    assertNotNull(session.find(target), "冷启动后未回读到 $target")
  }

  /** 选择生效范围，并以对应范围的说明出现确认配置已写入。 */
  private fun chooseAppScope(option: String, summary: String) {
    session.tap(session.label("settings_app_scope"))
    session.require(option).click()
    assertNotNull(session.find(summary), "未确认 $option 已保存")
  }

  /** 搜索框输入包名后自身也显示同一文本，只匹配列表行里的包名，避免点到搜索框。 */
  private fun selfRow() = checkNotNull(
    session.device.wait(Until.findObject(By.text(PACKAGE).clazz("android.widget.TextView")), 10_000L),
  ) { "未找到被测应用所在行" }

  /** 从设置页进入应用列表，按被测应用自身的包名搜索，返回当前名单的已选数量。 */
  private fun openAppsAndSearchSelf(): Int {
    session.tap(session.label("settings_apps"))
    session.require(session.label("settings_apps_blacklist_title"))
    checkNotNull(session.device.wait(Until.findObject(By.clazz("android.widget.EditText")), 10_000L)) {
      "未找到应用搜索框"
    }.text = PACKAGE
    selfRow()
    return selectedCount()
  }

  private fun countText(count: Int) = session.label("settings_apps_selected").replace("%1\$d", "$count")

  private fun selectedCount(): Int {
    val (prefix, suffix) = session.label("settings_apps_selected").split("%1\$d")
    val text = checkNotNull(session.device.wait(Until.findObject(By.textStartsWith(prefix)), 10_000L)) {
      "未找到已选数量"
    }.text
    return text.removePrefix(prefix).removeSuffix(suffix).trim().toInt()
  }

  /** 切换被测应用的勾选；它原本可能已在黑名单内，按数量变化方向确认切换结果。 */
  private fun toggleSelf(): Int {
    val before = selectedCount()
    selfRow().click()
    session.device.wait(Until.gone(By.text(countText(before))), 5_000L)
    val after = selectedCount()
    assertEquals(1, abs(after - before), "切换勾选后已选数量未更新")
    return after
  }

  @Test
  fun blacklistScopeAndSelectionSurviveForceStopAndColdStart() {
    val scopes = listOf("settings_app_scope_all", "settings_app_scope_whitelist", "settings_app_scope_blacklist")
    val summaries = scopes.associate { session.label(it) to session.label("${it}_summary") }
    val blacklist = session.label("settings_app_scope_blacklist")
    session.launch()
    session.openSettingsTab()
    session.scrollToText(session.label("settings_app_scope"))
    val original = summaries.keys.firstOrNull { session.device.findObject(By.text(it)) != null }
    assumeTrue("未读到当前生效范围", original != null)
    // 原本就是黑名单范围时，切换前后无法区分是否真正回读，此时不执行。
    assumeTrue("测试前已是排除黑名单应用范围", original != blacklist)

    chooseAppScope(blacklist, summaries.getValue(blacklist))
    val before = openAppsAndSearchSelf()
    // 先登记恢复：切回原勾选状态，再恢复原生效范围；黑名单内容与测试前一致。
    session.onCleanup {
      session.launch()
      session.openSettingsTab()
      session.scrollToText(session.label("settings_app_scope"))
      if (session.device.findObject(By.text(blacklist)) ==
        null
      ) {
        chooseAppScope(blacklist, summaries.getValue(blacklist))
      }
      if (openAppsAndSearchSelf() != before) toggleSelf()
      session.launch()
      session.openSettingsTab()
      chooseAppScope(original!!, summaries.getValue(original))
    }
    val after = toggleSelf()

    session.forceStop()
    session.launch()
    session.openSettingsTab()
    session.scrollToText(session.label("settings_app_scope"))
    assertNotNull(session.find(blacklist), "冷启动后未回读到排除黑名单应用范围")
    assertEquals(after, openAppsAndSearchSelf(), "冷启动后黑名单数量未回读")
  }
}
