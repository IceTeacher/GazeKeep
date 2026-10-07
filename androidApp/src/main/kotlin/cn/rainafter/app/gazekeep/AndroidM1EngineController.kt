package cn.rainafter.app.gazekeep

import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import androidx.core.content.ContextCompat
import cn.rainafter.app.gazekeep.service.GazeService
import cn.rainafter.app.gazekeep.backend.a11y.GazeA11yService
import cn.rainafter.app.gazekeep.core.model.BackendLock
import cn.rainafter.app.gazekeep.ui.M1EngineController
import cn.rainafter.app.gazekeep.ui.M1EngineStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class AndroidM1EngineController(
    private val context: Context,
) : M1EngineController, AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mutableEnabled = MutableStateFlow(preferences.getBoolean(KEY_ENABLED, false))
    override val enabled: StateFlow<Boolean> = mutableEnabled.asStateFlow()
    private val mutableStatus = MutableStateFlow(statusFor(mutableEnabled.value))
    override val status: StateFlow<M1EngineStatus> = mutableStatus.asStateFlow()

    init {
        scope.launch {
            GazeA11yService.connected.collect { refreshStatus() }
        }
    }

    override fun setEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putBoolean(KEY_M1_TEST, enabled)
            .apply()
        mutableEnabled.value = enabled
        refreshStatus()
        if (enabled) {
            ContextCompat.startForegroundService(context, Intent(context, GazeService::class.java))
        } else {
            context.stopService(Intent(context, GazeService::class.java))
        }
    }

    private fun refreshStatus() {
        mutableStatus.value = statusFor(mutableEnabled.value)
    }

    override fun close() {
        scope.cancel()
    }

    private fun statusFor(enabled: Boolean): M1EngineStatus = when {
        !enabled -> M1EngineStatus.Disabled
        backendReady() -> M1EngineStatus.Ready
        else -> M1EngineStatus.WaitingBackend
    }

    private fun backendReady(): Boolean {
        val lock = runCatching {
            BackendLock.valueOf(preferences.getString(KEY_BACKEND_LOCK, null) ?: BackendLock.Auto.name)
        }.getOrDefault(BackendLock.Auto)
        val shizukuReady = runCatching {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        return when (lock) {
            BackendLock.Accessibility -> GazeA11yService.connected.value
            BackendLock.Shizuku -> shizukuReady
            BackendLock.Auto -> GazeA11yService.connected.value || shizukuReady
            BackendLock.Lsposed -> false
        }
    }

    private companion object {
        const val PREFS_NAME = "gazekeep_config"
        const val KEY_ENABLED = "enabled"
        const val KEY_M1_TEST = "m1_test_mode"
        const val KEY_BACKEND_LOCK = "backend_lock"
    }
}
