package cn.rainafter.app.gazekeep.service

import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import cn.rainafter.app.gazekeep.core.engine.Alert
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.io.FileDescriptor
import java.io.PrintWriter

import cn.rainafter.app.gazekeep.app.AppGraph
import cn.rainafter.app.gazekeep.notification.GazeNotifier
import cn.rainafter.app.gazekeep.runtime.BackendSession

class GazeService : LifecycleService() {
    private lateinit var graph: AppGraph
    private lateinit var notifier: GazeNotifier
    private var session: BackendSession? = null

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(applicationContext)
        notifier = graph.createNotifier()
        notifier.createChannels()
        promoteForeground()
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
            } catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (_: Exception) {
                notifier.notifyAlert(Alert.BackendUnavailable)
                stopSelf()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 重复启动也要兑现前台服务契约，不能只依赖首次 onCreate。
        promoteForeground()
        super.onStartCommand(intent, flags, startId)
        if (intent?.getBooleanExtra(BACKEND_READY_EXTRA, false) == true) session?.backendReady()
        return START_STICKY
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

    private companion object {
        const val BACKEND_READY_EXTRA = "cn.rainafter.app.gazekeep.backend_ready"
    }
}
