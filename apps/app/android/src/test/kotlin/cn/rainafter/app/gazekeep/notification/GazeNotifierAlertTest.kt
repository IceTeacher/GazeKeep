package cn.rainafter.app.gazekeep.notification

import android.app.NotificationManager
import android.content.Context
import cn.rainafter.app.gazekeep.core.engine.Alert
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
  private val notifier = GazeNotifier { app }

  @Test fun cancelAlertRemovesNotificationAndResetsSuppressionForFutureFailure() {
    notifier.createChannels()
    notifier.notifyAlert(Alert.BackendUnavailable)
    assertEquals(1, shadowOf(manager).allNotifications.size)

    // 10 分钟内重复故障应继续抑制。
    notifier.notifyAlert(Alert.BackendUnavailable)
    assertEquals(1, shadowOf(manager).allNotifications.size)

    // 后端恢复清除后，通知栏移除该提醒，且后续再次真实失效时应重新提醒。
    notifier.cancelAlert(Alert.BackendUnavailable)
    assertTrue(shadowOf(manager).allNotifications.isEmpty())

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
}
