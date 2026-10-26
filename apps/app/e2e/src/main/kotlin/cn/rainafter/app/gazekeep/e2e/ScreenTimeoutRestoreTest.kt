package cn.rainafter.app.gazekeep.e2e

import android.app.KeyguardManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/**
 * 启用、息屏/亮屏与关闭后，系统熄屏超时必须回到用户原值（产品承诺 8）。
 * 需要应用已完成自检且至少一个后端就绪，且设备未设置安全锁屏；不满足时跳过并按未执行记录。
 */
@RunWith(AndroidJUnit4::class)
class ScreenTimeoutRestoreTest {
  private val session = DeviceSession()

  @Before fun setUp() = session.useChineseUi()

  @After fun tearDown() = session.close()

  @Test
  fun enableSleepWakeDisableKeepsUserScreenOffTimeout() {
    // 息屏后 shell 无法解除安全锁屏，后续步骤会停在锁屏界面。
    val keyguard = InstrumentationRegistry.getInstrumentation().context.getSystemService(KeyguardManager::class.java)
    assumeTrue("设备设置了安全锁屏，息屏后无法回到应用", !keyguard.isDeviceSecure)
    val original = session.rememberSetting("system", "screen_off_timeout")
    session.launch()
    val stopped = session.find(session.label("home_status_stopped"))
    assumeTrue("测试前服务已开启，为避免改变用户状态不执行", stopped != null)
    stopped!!.click()
    session.device.waitForIdle()
    // 启用后若被引导至自检或停留在等待后端，说明前提不满足；先恢复关闭状态。
    val working = session.find(session.label("home_status_working"))
    if (working == null) {
      session.device.pressBack()
      session.find(session.label("home_status_waiting"))?.click()
      assumeTrue("未完成自检或没有就绪后端，无法运行实际保亮链路", false)
    }
    session.onCleanup {
      session.launch()
      session.find(session.label("home_status_working"))?.click()
    }

    session.shell("input keyevent KEYCODE_SLEEP")
    Thread.sleep(2_000L)
    session.shell("input keyevent KEYCODE_WAKEUP")
    session.launch()
    session.require(session.label("home_status_working")).click()
    session.device.waitForIdle()
    session.require(session.label("home_status_stopped"))
    Thread.sleep(1_000L)
    assertEquals(original, session.shell("settings get system screen_off_timeout"))
  }
}
