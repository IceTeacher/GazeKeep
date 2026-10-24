package cn.rainafter.app.gazekeep.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import cn.rainafter.app.gazekeep.MainActivity
import cn.rainafter.app.gazekeep.R
import cn.rainafter.app.gazekeep.core.engine.Alert

/** 文案每次按当前应用语言解析：运行中切换语言时，服务不会重建本对象。 */
class GazeNotifier(private val localized: () -> Context) {
  private val context get() = localized()
  private val lastAlerts = mutableMapOf<Alert, Long>()
  fun notifyAlert(alert: Alert) {
    val now = SystemClock.elapsedRealtime()
    val previous = lastAlerts[alert]
    if (previous != null && now - previous < ALERT_SUPPRESSION_MS) return
    lastAlerts[alert] = now
    val message = when (alert) {
      Alert.BackendUnavailable -> context.getString(R.string.alert_backend_unavailable)
      Alert.CameraPermissionLost -> context.getString(R.string.alert_camera_permission_lost)
      Alert.CameraUnavailable -> context.getString(R.string.alert_camera_unavailable)
      Alert.BackendFailed -> context.getString(R.string.alert_backend_failed)
    }
    context.getSystemService(NotificationManager::class.java).notify(
      ALERT_NOTIFICATION_ID + alert.ordinal,
      NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(context.getString(R.string.alert_title))
        .setContentText(message)
        .setContentIntent(
          PendingIntent.getActivity(
            context,
            alert.ordinal + 1,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
          ),
        )
        .setAutoCancel(true)
        .build(),
    )
  }

  fun createChannels() {
    if (Build.VERSION.SDK_INT >= 26) {
      context.getSystemService(NotificationManager::class.java).createNotificationChannel(
        NotificationChannel(
          CHANNEL_ID,
          context.getString(R.string.notification_service_channel),
          NotificationManager.IMPORTANCE_LOW,
        ),
      )
      context.getSystemService(NotificationManager::class.java).createNotificationChannel(
        NotificationChannel(
          ALERT_CHANNEL_ID,
          context.getString(R.string.notification_alert_channel),
          NotificationManager.IMPORTANCE_DEFAULT,
        ),
      )
    }
  }

  /** 语言变化后更新渠道名与仍在展示的常驻通知；用户已移除的常驻通知不重新弹出。 */
  fun refreshLocale() {
    createChannels()
    val manager = context.getSystemService(NotificationManager::class.java)
    if (manager.activeNotifications.any { it.id == NOTIFICATION_ID }) {
      manager.notify(NOTIFICATION_ID, serviceNotification())
    }
  }

  fun serviceNotification(): Notification {
    val intent = PendingIntent.getActivity(
      context,
      0,
      Intent(context, MainActivity::class.java),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    return NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle(context.getString(R.string.notification_service_title))
      .setContentText(context.getString(R.string.notification_service_waiting))
      .setContentIntent(intent)
      .setOngoing(true)
      .build()
  }

  companion object {
    const val NOTIFICATION_ID = 1001
    private const val CHANNEL_ID = "service"
    private const val ALERT_CHANNEL_ID = "alerts"
    private const val ALERT_NOTIFICATION_ID = 2001
    private const val ALERT_SUPPRESSION_MS = 10 * 60 * 1000L
  }
}
