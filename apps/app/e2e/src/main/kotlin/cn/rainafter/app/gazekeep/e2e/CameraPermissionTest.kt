package cn.rainafter.app.gazekeep.e2e

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 撤销相机权限后自检只提供授权入口，权限状态不被应用绕过。 */
@RunWith(AndroidJUnit4::class)
class CameraPermissionTest {
  private val session = DeviceSession()

  @Before fun setUp() = session.useChineseUi()

  @After fun tearDown() = session.close()

  private fun granted() = session.shell("dumpsys package $PACKAGE")
    .lineSequence()
    .any { it.contains("${Manifest.permission.CAMERA}: granted=true") }

  @Test
  fun revokedCameraKeepsSelfTestBehindPermissionRequest() {
    val wasGranted = granted()
    session.onCleanup { if (wasGranted) session.shell("pm grant $PACKAGE ${Manifest.permission.CAMERA}") }
    session.shell("pm revoke $PACKAGE ${Manifest.permission.CAMERA}")

    session.launch()
    session.openSettingsTab()
    session.tap(session.label("settings_self_test"))
    assertNotNull(session.find(session.label("self_test_camera_permission")))
    assertTrue(session.device.findObject(By.text(session.label("self_test_capture_step_1"))) == null)

    // 拒绝系统授权框后仍停留在授权入口。系统授权框由各 ROM 提供，返回键等价于拒绝。
    session.tap(session.label("self_test_camera_permission"))
    session.device.pressBack()
    session.device.waitForIdle()
    assertTrue(!granted(), "拒绝后相机权限不应被授予")
    assertNotNull(session.find(session.label("self_test_camera_permission")))
  }
}
