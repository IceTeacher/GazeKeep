package cn.rainafter.app.gazekeep

import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import cn.rainafter.app.gazekeep.backend.shizuku.ShizukuKeepAwakeBackend
import cn.rainafter.app.gazekeep.backend.lsposed.LsposedKeepAwakeBackend
import cn.rainafter.app.gazekeep.backend.a11y.GazeA11yService
import cn.rainafter.app.gazekeep.core.model.BackendLock
import cn.rainafter.app.gazekeep.core.model.Readiness
import cn.rainafter.app.gazekeep.service.GazeService
import cn.rainafter.app.gazekeep.ui.M2ShizukuController
import cn.rainafter.app.gazekeep.ui.M2ShizukuStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class AndroidM2ShizukuController(context: Context) : M2ShizukuController {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val backend = ShizukuKeepAwakeBackend(context.applicationContext)
    private val lsposedBackend = LsposedKeepAwakeBackend(context.applicationContext)
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _status = MutableStateFlow(M2ShizukuStatus.Unknown)
    override val status: StateFlow<M2ShizukuStatus> = _status.asStateFlow()
    private val _backendLock = MutableStateFlow(readBackendLock())
    override val backendLock: StateFlow<BackendLock> = _backendLock.asStateFlow()
    private val _accessibilityEnabled = MutableStateFlow(isAccessibilityEnabled())
    override val accessibilityEnabled: StateFlow<Boolean> = _accessibilityEnabled.asStateFlow()
    private val _lsposedReady = MutableStateFlow(false)
    override val lsposedReady: StateFlow<Boolean> = _lsposedReady.asStateFlow()

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    init {
        Shizuku.addRequestPermissionResultListener(permissionListener)
        scope.launch {
            GazeA11yService.connected.collect {
                _accessibilityEnabled.value = isAccessibilityEnabled()
                if (_backendLock.value == BackendLock.Accessibility && preferences.getBoolean(KEY_ENABLED, false)) {
                    activateSelectedBackend()
                }
            }
        }
        scope.launch {
            backend.readiness.collect { readiness ->
                _status.value = when (readiness) {
                    Readiness.Ready -> M2ShizukuStatus.Ready
                    is Readiness.NeedsSetup -> when (readiness.step) {
                        cn.rainafter.app.gazekeep.core.model.SetupStep.StartShizuku -> M2ShizukuStatus.NeedsStart
                        cn.rainafter.app.gazekeep.core.model.SetupStep.GrantShizukuPermission -> M2ShizukuStatus.NeedsPermission
                        else -> M2ShizukuStatus.Unavailable
                    }
                    is Readiness.Unavailable -> M2ShizukuStatus.Unavailable
                }
                if (_backendLock.value == BackendLock.Shizuku && preferences.getBoolean(KEY_ENABLED, false)) {
                    activateSelectedBackend()
                }
            }
        }
        scope.launch {
            lsposedBackend.readiness.collect { readiness ->
                _lsposedReady.value = readiness == Readiness.Ready
                if ((_backendLock.value == BackendLock.Lsposed || _backendLock.value == BackendLock.Auto) &&
                    preferences.getBoolean(KEY_ENABLED, false)
                ) {
                    activateSelectedBackend()
                }
            }
        }
        refresh()
    }

    override fun refresh() {
        _accessibilityEnabled.value = isAccessibilityEnabled()
        scope.launch { lsposedBackend.checkReadiness() }
        if (!Shizuku.pingBinder()) {
            _status.value = M2ShizukuStatus.NeedsStart
            scope.launch { backend.attach() }
            return
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            _status.value = M2ShizukuStatus.NeedsPermission
            return
        }
        scope.launch { backend.attach() }
        if (preferences.getBoolean(KEY_ENABLED, false)) activateSelectedBackend()
    }

    override fun requestPermission() {
        if (!Shizuku.pingBinder()) {
            _status.value = M2ShizukuStatus.NeedsStart
            return
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            _status.value = M2ShizukuStatus.NeedsPermission
            runCatching { Shizuku.requestPermission(REQUEST_CODE) }
                .onFailure { _status.value = M2ShizukuStatus.Unavailable }
        } else {
            refresh()
        }
    }

    override fun setBackendLock(lock: BackendLock) {
        preferences.edit().putString(KEY_BACKEND_LOCK, lock.name).apply()
        _backendLock.value = lock
        context.stopService(Intent(context, GazeService::class.java))
        preferences.edit().putBoolean(KEY_ENABLED, true).putBoolean(KEY_M1_TEST, false).apply()
        activateSelectedBackend()
    }

    override fun openAccessibilitySettings() {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    override fun close() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        scope.launch { backend.detach() }
        scope.cancel()
    }

    private companion object {
        const val PREFS_NAME = "gazekeep_config"
        const val KEY_ENABLED = "enabled"
        const val KEY_M1_TEST = "m1_test_mode"
        const val KEY_BACKEND_LOCK = "backend_lock"
        const val REQUEST_CODE = 2301
    }

    private fun readBackendLock(): BackendLock = runCatching {
        BackendLock.valueOf(preferences.getString(KEY_BACKEND_LOCK, null) ?: BackendLock.Auto.name)
    }.getOrDefault(BackendLock.Auto)

    private fun isAccessibilityEnabled(): Boolean {
        val expected = ComponentName(context, GazeA11yService::class.java).flattenToString()
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty().split(':').any { it.equals(expected, ignoreCase = true) }
    }

    private fun activateSelectedBackend() {
        val lock = _backendLock.value
        val ready = when (lock) {
            BackendLock.Accessibility -> isAccessibilityEnabled() && GazeA11yService.connected.value
            BackendLock.Shizuku -> Shizuku.pingBinder() &&
                runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
            BackendLock.Auto -> isAccessibilityEnabled() || (
                _lsposedReady.value || (Shizuku.pingBinder() &&
                    runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
                ))
            BackendLock.Lsposed -> _lsposedReady.value
        }
        if (!ready) return
        scope.launch {
            delay(200L)
            ContextCompat.startForegroundService(context, Intent(context, GazeService::class.java))
        }
    }
}
