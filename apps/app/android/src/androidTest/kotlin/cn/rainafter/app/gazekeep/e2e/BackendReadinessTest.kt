package cn.rainafter.app.gazekeep.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/** 后端页的无障碍就绪状态与系统实际设置一致；本测试只读系统设置，不改变无障碍开关。 */
@RunWith(AndroidJUnit4::class)
class BackendReadinessTest {
  private val session = DeviceSession()

  @Before fun setUp() = session.useChineseUi()

  @After fun tearDown() = session.close()

  @Test
  fun accessibilityReadinessMatchesSystemSetting() {
    val enabled = session.shell("settings get secure enabled_accessibility_services")
      .contains("$PACKAGE/cn.rainafter.app.gazekeep.backend.a11y.GazeA11yService")
    session.launch()
    session.openSettingsTab()
    session.tap(session.label("settings_backend"))
    session.require(session.label("settings_backend_order"))
    val unavailable = session.device.findObject(By.text(session.label("m2_accessibility_unavailable"))) != null
    assertEquals(!enabled, unavailable, "系统无障碍启用=$enabled，页面显示未启用=$unavailable")
  }
}
