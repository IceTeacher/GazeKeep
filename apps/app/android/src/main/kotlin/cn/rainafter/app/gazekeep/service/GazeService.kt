package cn.rainafter.app.gazekeep.service

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import cn.rainafter.app.gazekeep.app.AppGraph
import cn.rainafter.app.gazekeep.core.engine.Alert
import cn.rainafter.app.gazekeep.notification.GazeNotifier
import cn.rainafter.app.gazekeep.runtime.BackendSession
import cn.rainafter.app.gazekeep.runtime.RuntimeStatusStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.FileDescriptor
import java.io.PrintWriter

class GazeService : LifecycleService() {
  private lateinit var graph: AppGraph
  private lateinit var notifier: GazeNotifier
  private var session: BackendSession? = null
  private lateinit var foreground: ForegroundStartGuard

  override fun onCreate() {
    super.onCreate()
    graph = AppGraph(applicationContext)
    notifier = graph.createNotifier()
    notifier.createChannels()
    foreground = ForegroundStartGuard(
      cameraGranted = { checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED },
      promote = ::promoteForeground,
      release = {
        val oldSession = session
        session = null
        RuntimeStatusStore.updateRunning(running = false, backend = null)
        oldSession?.close()
      },
      notify = notifier::notifyAlert,
      stop = ::stopSelf,
    )
    if (!foreground.ensureForeground()) return
    lifecycleScope.launch {
      try {
        val settings = graph.settings.settings.first()
        if (!settings.config.enabled || settings.selfTestPassedAt == null) {
          stopSelf()
          return@launch
        }
        val running = graph.createSession(lifecycleScope, settings.config, notifier)
        session = running
        running.start()
      } catch (error: kotlinx.coroutines.CancellationException) {
        throw error
      } catch (_: Exception) {
        notifier.notifyAlert(Alert.BackendUnavailable)
        stopSelf()
      }
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    // 重复启动也要兑现前台服务契约，不能只依赖首次 onCreate。
    val ready = foreground.ensureForeground()
    super.onStartCommand(intent, flags, startId)
    return if (ready) START_STICKY else START_NOT_STICKY
  }

  override fun dump(fd: FileDescriptor, writer: PrintWriter, args: Array<out String>) {
    session?.dump(writer)
  }

  override fun onDestroy() {
    session?.close()
    session = null
    super.onDestroy()
  }

  private fun promoteForeground() {
    if (Build.VERSION.SDK_INT >= 29) {
      val type = if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0
      startForeground(GazeNotifier.NOTIFICATION_ID, notifier.serviceNotification(), type)
    } else {
      startForeground(GazeNotifier.NOTIFICATION_ID, notifier.serviceNotification())
    }
  }
}
