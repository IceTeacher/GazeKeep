package cn.rainafter.app.gazekeep.runtime

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import cn.rainafter.app.gazekeep.engine.config.ConfigStore
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
        pending?.cancel()
        pending = null
        RuntimeStatusStore.intentionalStop = true
        context.stopService(Intent(context, GazeService::class.java))
    }
    fun reconfigure(context: Context) {
        stop(context)
        pending = scope.launch {
            try {
                delay(250L)
                val settings = ConfigStore(context).settings.first()
                if (!settings.config.enabled || settings.selfTestPassedAt == null) return@launch
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return@launch
                ContextCompat.startForegroundService(context, Intent(context, GazeService::class.java))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                RuntimeStatusStore.publish(RuntimeStatus())
            }
        }
    }
}
