package cn.rainafter.app.gazekeep.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
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
}
