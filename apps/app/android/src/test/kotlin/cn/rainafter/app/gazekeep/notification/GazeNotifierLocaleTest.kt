package cn.rainafter.app.gazekeep.notification

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import cn.rainafter.app.gazekeep.R
import cn.rainafter.app.gazekeep.core.engine.Alert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class GazeNotifierLocaleTest {
  private val app: Context = RuntimeEnvironment.getApplication()
  private val manager = app.getSystemService(NotificationManager::class.java)
  private val zh = localized("zh-CN")
  private val en = localized("en")
  private var current = zh
  private val notifier = GazeNotifier { current }

  @Test fun refreshRenamesChannelsAndReplacesShownServiceNotification() {
    notifier.createChannels()
    manager.notify(GazeNotifier.NOTIFICATION_ID, notifier.serviceNotification())
    assertNotEquals(
      zh.getString(R.string.notification_service_title),
      en.getString(R.string.notification_service_title),
    )

    current = en
    notifier.refreshLocale()

    assertEquals(
      setOf(en.getString(R.string.notification_service_channel), en.getString(R.string.notification_alert_channel)),
      manager.notificationChannels.map { it.name.toString() }.toSet(),
    )
    val service = shadowOf(manager).getNotification(GazeNotifier.NOTIFICATION_ID)
    assertEquals(en.getString(R.string.notification_service_title), service.title())
    assertEquals(en.getString(R.string.notification_service_waiting), service.text())
  }

  @Test fun refreshDoesNotRepostServiceNotificationThatIsNotShown() {
    notifier.createChannels()
    current = en
    notifier.refreshLocale()

    assertNull(shadowOf(manager).getNotification(GazeNotifier.NOTIFICATION_ID))
  }

  @Test fun alertsAfterSwitchUseCurrentLanguage() {
    notifier.createChannels()
    current = en
    notifier.notifyAlert(Alert.CameraUnavailable)

    val alert = shadowOf(manager).allNotifications.single()
    assertEquals(en.getString(R.string.alert_title), alert.title())
    assertEquals(en.getString(R.string.alert_camera_unavailable), alert.text())
  }

  private fun localized(tag: String): Context = app.createConfigurationContext(
    Configuration(app.resources.configuration).apply { setLocales(LocaleList.forLanguageTags(tag)) },
  )

  private fun Notification.title() = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
  private fun Notification.text() = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
}
