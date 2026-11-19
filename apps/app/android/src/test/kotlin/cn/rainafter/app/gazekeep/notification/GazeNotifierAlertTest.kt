package cn.rainafter.app.gazekeep.notification

import android.app.NotificationManager
import android.content.Context
import cn.rainafter.app.gazekeep.core.engine.Alert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class GazeNotifierAlertTest {
  private val app: Context = RuntimeEnvironment.getApplication()
  private val manager = app.getSystemService(NotificationManager::class.java)
  private var nowMs = 1_000L
  private val notifier = GazeNotifier({ app }, { nowMs })

  @Before fun setUp() {
    GazeNotifier.resetForTest()
  }

  @Test fun cancelAlertRemovesNotificationWhileKeepingRateLimitWithinTenMinutes() {
    notifier.createChannels()
    notifier.notifyAlert(Alert.BackendUnavailable)
    assertEquals(1, shadowOf(manager).allNotifications.size)

    // 后端恢复清除后，通知栏应立即撤销；但 10 分钟内同类故障仍受限频约束，避免抖动刷屏。
    notifier.cancelAlert(Alert.BackendUnavailable)
    assertTrue(shadowOf(manager).allNotifications.isEmpty())

    nowMs += 5_000L
    notifier.notifyAlert(Alert.BackendUnavailable)
    assertTrue(shadowOf(manager).allNotifications.isEmpty())

    // 不同告警类型相互独立，不受前一种告警限频影响。
    notifier.notifyAlert(Alert.CameraUnavailable)
    assertEquals(1, shadowOf(manager).allNotifications.size)
    notifier.cancelAlert(Alert.CameraUnavailable)

    // 超过 10 分钟冷却窗口后，再次失效允许重新通知。
    nowMs += 10 * 60 * 1000L
    notifier.notifyAlert(Alert.BackendUnavailable)
    assertEquals(1, shadowOf(manager).allNotifications.size)
  }

  @Test fun clearAllAlertsRemovesAllPendingAlerts() {
    notifier.createChannels()
    Alert.entries.forEach(notifier::notifyAlert)
    assertEquals(Alert.entries.size, shadowOf(manager).allNotifications.size)

    notifier.clearAllAlerts()
    assertTrue(shadowOf(manager).allNotifications.isEmpty())
  }

  @Test fun rateLimitPersistsAcrossRecreatedNotifierInstancesAfterServiceReconfigure() {
    val firstNotifier = GazeNotifier({ app }, { nowMs })
    firstNotifier.createChannels()
    firstNotifier.notifyAlert(Alert.BackendUnavailable)
    assertEquals(1, shadowOf(manager).allNotifications.size)

    // 模拟切换至就绪后端触发 ServiceActivation.reconfigure 重建 GazeService 与 GazeNotifier。
    val secondNotifier = GazeNotifier({ app }, { nowMs })
    secondNotifier.cancelAlert(Alert.BackendUnavailable)
    assertTrue(shadowOf(manager).allNotifications.isEmpty())

    // 8 秒后再次切回不可用后端并重建服务，10 分钟内不应重新弹出同类告警。
    nowMs += 8_000L
    val thirdNotifier = GazeNotifier({ app }, { nowMs })
    thirdNotifier.notifyAlert(Alert.BackendUnavailable)
    assertTrue(shadowOf(manager).allNotifications.isEmpty())

    // 超过 10 分钟冷却窗口后，新服务实例允许再次提醒。
    nowMs += 10 * 60 * 1000L
    val fourthNotifier = GazeNotifier({ app }, { nowMs })
    fourthNotifier.notifyAlert(Alert.BackendUnavailable)
    assertEquals(1, shadowOf(manager).allNotifications.size)
  }
}
