package cn.rainafter.app.gazekeep

import android.Manifest
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.camera.core.Preview
import androidx.core.content.ContextCompat
import cn.rainafter.app.gazekeep.backend.a11y.GazeA11yService
import cn.rainafter.app.gazekeep.backend.lsposed.LsposedKeepAwakeBackend
import cn.rainafter.app.gazekeep.core.model.*
import cn.rainafter.app.gazekeep.core.policy.*
import cn.rainafter.app.gazekeep.engine.EngineRuntime
import cn.rainafter.app.gazekeep.engine.camera.CameraXFrameSource
import cn.rainafter.app.gazekeep.engine.classify.MlKitFrameClassifier
import cn.rainafter.app.gazekeep.engine.config.ConfigStore
import cn.rainafter.app.gazekeep.engine.m0.M0CaptureSession
import cn.rainafter.app.gazekeep.service.GazeService
import cn.rainafter.app.gazekeep.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import rikka.shizuku.Shizuku

class AndroidGazeKeepController(context: Context) : GazeKeepController, AutoCloseable {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val store = ConfigStore(context)
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(ControllerState())
    override val state = mutableState.asStateFlow()
    private val mutableSelfTest = MutableStateFlow(SelfTestState())
    override val selfTest = mutableSelfTest.asStateFlow()
    private val lsposed = LsposedKeepAwakeBackend(context)
    private var testJob: Job? = null
    private var testActive = false
    private var progress = SelfTestProgress()
    private val frameSource = CameraXFrameSource(context)
    private val classifier = MlKitFrameClassifier()
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
                    if (firstLoad && settings.config.enabled && settings.selfTestPassedAt != null && !EngineRuntime.status.value.running) reconfigureEngine()
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                mutableState.update { it.copy(loaded = true, config = GazeConfig(), notice = UiNotice.SaveFailed) }
                stopEngine()
            }
        }
        scope.launch {
            EngineRuntime.status.collect { runtime ->
                mutableState.update { it.copy(serviceRunning = runtime.running, activeBackend = runtime.backend, lastDecision = runtime.lastDecision) }
            }
        }
        scope.launch { GazeA11yService.connected.collect { refresh() } }
    }

    override fun refresh() {
        scope.launch {
            val lsposedReadiness = withContext(Dispatchers.IO) { lsposed.checkReadiness() }
            val shizukuReadiness = runCatching {
                when {
                    !Shizuku.pingBinder() -> Readiness.NeedsSetup(SetupStep.StartShizuku)
                    Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> Readiness.NeedsSetup(SetupStep.GrantShizukuPermission)
                    else -> Readiness.Ready
                }
            }.getOrDefault(Readiness.NeedsSetup(SetupStep.StartShizuku))
            val expected = ComponentName(context, GazeA11yService::class.java).flattenToString()
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                .orEmpty().split(':').any { it.equals(expected, true) }
            mutableState.update { it.copy(
                cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
                backends = mapOf(
                    BackendId.Lsposed to lsposedReadiness,
                    BackendId.Shizuku to shizukuReadiness,
                    BackendId.Accessibility to if (enabled && GazeA11yService.connected.value) Readiness.Ready
                        else Readiness.NeedsSetup(SetupStep.EnableAccessibilityService),
                ),
            ) }
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
        EngineActivation.stop(context)
    }

    private fun reconfigureEngine() = EngineActivation.reconfigure(context)

    override suspend fun beginSelfTest() {
        cancelSelfTest()
        updateConfig { it.copy(enabled = false) }
        testActive = true
        progress = SelfTestProgress()
        mutableSelfTest.value = SelfTestState()
    }

    fun setPreviewSurface(provider: Preview.SurfaceProvider?) { frameSource.previewSurfaceProvider = provider }

    override suspend fun captureSelfTest() {
        if (!testActive || selfTest.value.running || progress.complete) return
        if (!state.value.cameraGranted) { mutableState.update { it.copy(notice = UiNotice.NeedsCamera) }; return }
        testJob = scope.launch {
            mutableSelfTest.update { it.copy(running = true, attention = null) }
            try {
                withTimeout(1_000L) { EngineRuntime.status.first { !it.running } }
                val result = M0CaptureSession(frameSource, classifier).capture(sensitivity = state.value.config.sensitivity)
                val attention = if (result.outcome is cn.rainafter.app.gazekeep.engine.camera.CaptureOutcome.Completed) result.attention else Attention.Unknown
                progress = advanceSelfTest(progress, attention)
                if (progress.complete) store.completeSelfTest()
                mutableSelfTest.value = SelfTestState(
                    step = when { progress.complete -> SelfTestStep.Complete; progress.lookingPassed -> SelfTestStep.Away; else -> SelfTestStep.Looking },
                    attention = attention,
                )
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutableSelfTest.update { it.copy(running = false, attention = Attention.Unknown) } }
            finally { mutableSelfTest.update { it.copy(running = false) } }
        }
    }

    fun pauseSelfTest() { testJob?.cancel(); testJob = null }

    override fun cancelSelfTest() {
        testJob?.cancel()
        testJob = null
        testActive = false
    }

    override suspend fun loadApps() {
        val apps = withContext(Dispatchers.IO) {
            context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { AppEntry(it.activityInfo.packageName, it.loadLabel(context.packageManager).toString()) }
                .distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
        }
        mutableState.update { it.copy(apps = apps) }
    }

    override fun close() {
        cancelSelfTest()
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeBinderDeadListener(binderDead)
        scope.cancel()
        frameSource.close()
        classifier.close()
    }
}

/** 启停任务属于应用进程，退出设置页不会取消已经保存的开启操作。 */
private object EngineActivation {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pending: Job? = null
    fun stop(context: Context) {
        pending?.cancel()
        pending = null
        EngineRuntime.intentionalStop = true
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
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { EngineRuntime.publish(cn.rainafter.app.gazekeep.engine.RuntimeStatus()) }
        }
    }
}
