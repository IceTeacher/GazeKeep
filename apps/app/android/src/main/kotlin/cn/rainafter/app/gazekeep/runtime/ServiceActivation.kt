package cn.rainafter.app.gazekeep.runtime

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import cn.rainafter.app.gazekeep.engine.config.ConfigStore
import cn.rainafter.app.gazekeep.notification.GazeNotifier
import cn.rainafter.app.gazekeep.platform.AppLocales
import cn.rainafter.app.gazekeep.service.GazeQuickTileService
import cn.rainafter.app.gazekeep.service.GazeService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 启停任务属于应用进程，退出设置页不会取消已经保存的开启操作。 */
object ServiceActivation {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var pending: Job? = null
  fun stop(context: Context) {
    stopServiceOnly(context)
    GazeNotifier { AppLocales.wrap(context) }.clearAllAlerts()
  }

  private fun stopServiceOnly(context: Context) {
    pending?.cancel()
    pending = null
    RuntimeStatusStore.intentionalStop = true
    context.stopService(Intent(context, GazeService::class.java))
    GazeQuickTileService.requestRefresh(context)
  }

  fun reconfigure(context: Context) {
    stopServiceOnly(context)
    pending = scope.launch {
      try {
        delay(250L)
        val settings = ConfigStore(context).settings.first()
        if (!settings.config.enabled || settings.selfTestPassedAt == null) {
          if (!settings.config.enabled) {
            GazeNotifier { AppLocales.wrap(context) }.clearAllAlerts()
          }
          return@launch
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
          PackageManager.PERMISSION_GRANTED
        ) {
          return@launch
        }
        ContextCompat.startForegroundService(context, Intent(context, GazeService::class.java))
      } catch (error: CancellationException) {
        throw error
      } catch (_: Exception) {
        RuntimeStatusStore.updateRunning(running = false, backend = null)
      }
    }
  }
}
