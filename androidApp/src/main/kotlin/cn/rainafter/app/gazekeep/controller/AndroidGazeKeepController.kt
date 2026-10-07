package cn.rainafter.app.gazekeep.controller

import cn.rainafter.app.gazekeep.apps.LaunchableAppsRepository
import cn.rainafter.app.gazekeep.selftest.SelfTestCoordinator
import cn.rainafter.app.gazekeep.runtime.RuntimeStatusStore
import cn.rainafter.app.gazekeep.runtime.ServiceActivation
import android.content.Context
import androidx.camera.core.Preview
import cn.rainafter.app.gazekeep.backend.a11y.GazeA11yService
import cn.rainafter.app.gazekeep.core.model.GazeConfig
import cn.rainafter.app.gazekeep.core.policy.EnableRequirement
import cn.rainafter.app.gazekeep.core.policy.enableRequirement
import cn.rainafter.app.gazekeep.engine.config.ConfigStore
import cn.rainafter.app.gazekeep.ui.contract.ControllerState
import cn.rainafter.app.gazekeep.ui.contract.GazeKeepController
import cn.rainafter.app.gazekeep.ui.contract.UiNotice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import rikka.shizuku.Shizuku

class AndroidGazeKeepController(context: Context, private val store: ConfigStore = ConfigStore(context)) : GazeKeepController, AutoCloseable {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(ControllerState())
    override val state = mutableState.asStateFlow()
    private val readinessProbe = BackendReadinessProbe(context)
    private val selfTestCoordinator = SelfTestCoordinator(context, scope, store)
    override val selfTest = selfTestCoordinator.state
    private val appsRepository = LaunchableAppsRepository(context, scope) { apps ->
        mutableState.update { it.copy(apps = apps) }
    }
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }
    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDead = Shizuku.OnBinderDeadListener { refresh() }

    init {
        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        scope.launch {
            try {
                store.settings.collect { settings ->
                    val firstLoad = !state.value.loaded
                    mutableState.update { it.copy(loaded = true, config = settings.config, selfTestPassed = settings.selfTestPassedAt != null) }
                    if (firstLoad && settings.config.enabled && settings.selfTestPassedAt != null && !RuntimeStatusStore.status.value.running) reconfigureEngine()
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                mutableState.update { it.copy(loaded = true, config = GazeConfig(), notice = UiNotice.SaveFailed) }
                stopEngine()
            }
        }
        scope.launch {
            RuntimeStatusStore.status.collect { runtime ->
                mutableState.update { it.copy(serviceRunning = runtime.running, activeBackend = runtime.backend, lastDecision = runtime.lastDecision) }
            }
        }
        scope.launch { GazeA11yService.connected.collect { refresh() } }
    }

    override fun refresh() {
        scope.launch {
            val readiness = readinessProbe.read()
            mutableState.update { it.copy(cameraGranted = readiness.cameraGranted, backends = readiness.backends) }
        }
    }

    override suspend fun updateConfig(transform: (GazeConfig) -> GazeConfig) = mutex.withLock {
        try {
            store.update(transform)
            mutableState.update { it.copy(notice = null) }
            reconfigureEngine()
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) {
            mutableState.update { it.copy(notice = UiNotice.SaveFailed) }
            stopEngine()
        }
    }

    override suspend fun setEnabled(enabled: Boolean) {
        if (!state.value.loaded) return
        if (enabled) {
            val settings = try { store.settings.first() }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutableState.update { it.copy(notice = UiNotice.SaveFailed) }; stopEngine(); return }
            when (enableRequirement(state.value.cameraGranted, settings.selfTestPassedAt != null)) {
                EnableRequirement.Camera -> { mutableState.update { it.copy(notice = UiNotice.NeedsCamera) }; return }
                EnableRequirement.SelfTest -> { mutableState.update { it.copy(notice = UiNotice.NeedsSelfTest) }; return }
                EnableRequirement.Ready -> Unit
            }
        }
        updateConfig { it.copy(enabled = enabled) }
        if (enabled && state.value.config.whitelistEnabled && state.value.config.packages.isEmpty()) {
            mutableState.update { it.copy(notice = UiNotice.EmptyScope) }
        }
    }

    private fun stopEngine() {
        ServiceActivation.stop(context)
    }

    private fun reconfigureEngine() = ServiceActivation.reconfigure(context)

    override suspend fun beginSelfTest() {
        cancelSelfTest()
        updateConfig { it.copy(enabled = false) }
        selfTestCoordinator.begin()
    }

    fun setPreviewSurface(provider: Preview.SurfaceProvider?) = selfTestCoordinator.setPreviewSurface(provider)
    override suspend fun captureSelfTest() {
        if (!state.value.cameraGranted) {
            mutableState.update { it.copy(notice = UiNotice.NeedsCamera) }
            return
        }
        selfTestCoordinator.capture(state.value.config.sensitivity)
    }
    fun pauseSelfTest() = selfTestCoordinator.pause()
    override fun cancelSelfTest() = selfTestCoordinator.cancel()

    override suspend fun loadApps() = appsRepository.load()

    override fun close() {
        cancelSelfTest()
        appsRepository.close()
        selfTestCoordinator.close()
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeBinderDeadListener(binderDead)
        scope.cancel()
    }
}
