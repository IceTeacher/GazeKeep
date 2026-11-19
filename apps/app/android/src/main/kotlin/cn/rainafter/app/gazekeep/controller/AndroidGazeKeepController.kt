package cn.rainafter.app.gazekeep.controller

import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.camera.core.Preview
import cn.rainafter.app.gazekeep.applist.LaunchableAppsRepository
import cn.rainafter.app.gazekeep.backend.a11y.GazeA11yService
import cn.rainafter.app.gazekeep.core.model.AppFilterMode
import cn.rainafter.app.gazekeep.core.model.GazeConfig
import cn.rainafter.app.gazekeep.core.model.Readiness
import cn.rainafter.app.gazekeep.core.policy.EnableRequirement
import cn.rainafter.app.gazekeep.core.policy.enableRequirement
import cn.rainafter.app.gazekeep.core.policy.shouldSyncHideCameraIndicator
import cn.rainafter.app.gazekeep.engine.camera.CaptureOwnershipCoordinator
import cn.rainafter.app.gazekeep.engine.config.ConfigStore
import cn.rainafter.app.gazekeep.notification.DecisionLogs
import cn.rainafter.app.gazekeep.runtime.RuntimeStatusStore
import cn.rainafter.app.gazekeep.runtime.ServiceActivation
import cn.rainafter.app.gazekeep.selftest.SelfTestCoordinator
import cn.rainafter.app.gazekeep.service.GazeQuickTileService
import cn.rainafter.app.gazekeep.ui.contract.ControllerState
import cn.rainafter.app.gazekeep.ui.contract.GazeKeepController
import cn.rainafter.app.gazekeep.ui.contract.UiNotice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import rikka.shizuku.Shizuku

class AndroidGazeKeepController(
  context: Context,
  private val store: ConfigStore,
  ownership: CaptureOwnershipCoordinator,
) : GazeKeepController,
  AutoCloseable {
  private val context = context.applicationContext
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val mutex = Mutex()
  private val indicatorSyncMutex = Mutex()
  private var indicatorSyncJob: Job? = null
  private val mutableState = MutableStateFlow(ControllerState(systemInfo = readSystemInfo(this.context)))
  override val state = mutableState.asStateFlow()
  private val readinessProbe = BackendReadinessProbe(context)

  @Volatile private var lastCameraGranted: Boolean? = null
  private val selfTestCoordinator = SelfTestCoordinator(context, scope, store, ownership)
  override val selfTest = selfTestCoordinator.state
  private val decisionLogs = DecisionLogs.get(this.context)
  private val appsRepository = LaunchableAppsRepository(context, scope) { loadState, apps ->
    mutableState.update { it.copy(apps = apps, appsLoadState = loadState) }
  }
  private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }
  private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }
  private val binderDead = Shizuku.OnBinderDeadListener { refresh() }
  private val accessibilityManager = this.context.getSystemService(AccessibilityManager::class.java)
  private val accessibilityStateListener = AccessibilityManager.AccessibilityStateChangeListener { refresh() }
  private val accessibilityServicesListener = if (Build.VERSION.SDK_INT >= 33) {
    AccessibilityManager.AccessibilityServicesStateChangeListener { refresh() }
  } else {
    null
  }
  private val accessibilitySettingsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
    override fun onChange(selfChange: Boolean) {
      refresh()
    }
  }

  init {
    Shizuku.addRequestPermissionResultListener(permissionListener)
    Shizuku.addBinderReceivedListenerSticky(binderReceived)
    Shizuku.addBinderDeadListener(binderDead)
    accessibilityManager?.addAccessibilityStateChangeListener(accessibilityStateListener)
    if (Build.VERSION.SDK_INT >= 33 && accessibilityServicesListener != null) {
      accessibilityManager?.addAccessibilityServicesStateChangeListener(accessibilityServicesListener)
    }
    runCatching {
      val resolver = this.context.contentResolver
      Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?.let { uri ->
        resolver.registerContentObserver(uri, false, accessibilitySettingsObserver)
      }
      Settings.Secure.getUriFor(Settings.Secure.ACCESSIBILITY_ENABLED)?.let { uri ->
        resolver.registerContentObserver(uri, false, accessibilitySettingsObserver)
      }
    }
    scope.launch(Dispatchers.IO) {
      val notices = runCatching {
        this@AndroidGazeKeepController.context.assets.open("THIRD_PARTY_NOTICES.txt")
          .bufferedReader()
          .use { it.readText() }
      }.getOrDefault("")
      mutableState.update { it.copy(thirdPartyNotices = notices) }
    }
    scope.launch {
      try {
        store.settings.collect { settings ->
          val firstLoad = !state.value.loaded
          mutableState.update {
            it.copy(
              loaded = true,
              config = settings.config,
              selfTestPassed =
              settings.selfTestPassedAt != null,
            )
          }
          syncIndicatorState()
          if (firstLoad && settings.config.enabled && settings.selfTestPassedAt != null &&
            !RuntimeStatusStore.status.value.running
          ) {
            reconfigureEngine()
          }
        }
      } catch (error: CancellationException) {
        throw error
      } catch (_: Exception) {
        mutableState.update { it.copy(loaded = true, config = GazeConfig(), notice = UiNotice.SaveFailed) }
        stopEngine()
      }
    }
    scope.launch {
      var lastShizukuReadiness: Readiness? = RuntimeStatusStore.status.value.shizukuReadiness
      RuntimeStatusStore.status.collect { runtime ->
        mutableState.update {
          it.copy(
            serviceRunning = runtime.running,
            activeBackend = runtime.backend,
            lastDecision = runtime.lastDecision,
            lastOutcome = runtime.lastOutcome,
            notice = if (it.notice == UiNotice.NeedsBackend && runtime.backend != null) null else it.notice,
          )
        }
        syncIndicatorState()
        GazeQuickTileService.requestRefresh(context)
        if (runtime.shizukuReadiness != lastShizukuReadiness) {
          lastShizukuReadiness = runtime.shizukuReadiness
          refresh()
        }
      }
    }
    scope.launch {
      decisionLogs.entries.collect { entries ->
        mutableState.update { it.copy(logs = entries) }
      }
    }
    scope.launch { GazeA11yService.connected.collect { refresh() } }
  }

  private fun applyReadiness(readiness: BackendReadinessSnapshot) {
    mutableState.update {
      val backendReady = readiness.backends.values.any { value -> value == Readiness.Ready }
      it.copy(
        cameraGranted = readiness.cameraGranted,
        backends = readiness.backends,
        hideCameraIndicatorReady = readiness.hideCameraIndicatorReady,
        notice = if (it.notice == UiNotice.NeedsBackend && backendReady) null else it.notice,
      )
    }
  }

  override fun refresh() {
    scope.launch {
      val readiness = readinessProbe.read()
      applyReadiness(readiness)
      GazeQuickTileService.requestRefresh(context)
      syncIndicatorState()
      // 首次读取只记录基线，避免每次打开应用都把默认未授权当作权限恢复。
      val wasGranted = lastCameraGranted
      lastCameraGranted = readiness.cameraGranted
      val current = state.value
      if (wasGranted != null && current.loaded &&
        shouldResumeAfterCameraGrant(
          wasGranted,
          readiness.cameraGranted,
          current.config.enabled,
          current.selfTestPassed,
        )
      ) {
        reconfigureEngine()
      }
    }
  }

  override suspend fun updateConfig(transform: (GazeConfig) -> GazeConfig) = mutex.withLock {
    try {
      store.update(transform)
      mutableState.update { it.copy(notice = null) }
      reconfigureEngine()
    } catch (error: CancellationException) {
      throw error
    } catch (_: Exception) {
      mutableState.update { it.copy(notice = UiNotice.SaveFailed) }
      stopEngine()
    }
  }

  override suspend fun importConfig(config: GazeConfig): Boolean = mutex.withLock {
    try {
      val saved = store.importConfig(config)
      mutableState.update {
        it.copy(
          config = saved.config,
          selfTestPassed = saved.selfTestPassedAt != null,
          notice = null,
        )
      }
      syncIndicatorState()
      if (saved.selfTestPassedAt != null && (saved.config.enabled || RuntimeStatusStore.status.value.running)) {
        reconfigureEngine()
      }
      GazeQuickTileService.requestRefresh(context)
      true
    } catch (error: CancellationException) {
      throw error
    } catch (_: Exception) {
      mutableState.update { it.copy(notice = UiNotice.SaveFailed) }
      stopEngine()
      false
    }
  }

  private fun syncIndicatorState() {
    val current = state.value
    if (!current.loaded) return
    val shouldHide = shouldSyncHideCameraIndicator(
      config = current.config,
      activeBackend = current.activeBackend,
      backends = current.backends,
    ) && current.hideCameraIndicatorReady
    indicatorSyncJob?.cancel()
    indicatorSyncJob = scope.launch {
      indicatorSyncMutex.withLock {
        readinessProbe.syncHideCameraIndicator(shouldHide)
      }
    }
  }

  override suspend fun setEnabled(enabled: Boolean) {
    // 从磁贴冷启动时，配置流可能尚未完成首帧加载，先等待就绪再执行开关变更。
    state.first { it.loaded }
    if (enabled) {
      val settings = try {
        store.settings.first()
      } catch (error: CancellationException) {
        throw error
      } catch (_: Exception) {
        mutableState.update { it.copy(notice = UiNotice.SaveFailed) }
        stopEngine()
        return
      }
      if (state.value.backends.isEmpty()) {
        val readiness = readinessProbe.read()
        applyReadiness(readiness)
        if (lastCameraGranted == null) {
          lastCameraGranted = readiness.cameraGranted
        }
      }
      when (enableRequirement(state.value.cameraGranted, settings.selfTestPassedAt != null)) {
        EnableRequirement.Camera -> {
          mutableState.update { it.copy(notice = UiNotice.NeedsCamera) }
          return
        }

        EnableRequirement.SelfTest -> {
          mutableState.update { it.copy(notice = UiNotice.NeedsSelfTest) }
          return
        }

        EnableRequirement.Ready -> Unit
      }
    } else {
      RuntimeStatusStore.clearLastDecision()
    }
    updateConfig { it.copy(enabled = enabled) }
    val current = state.value
    val config = current.config
    val backendMissing = current.backends.isNotEmpty() && current.backends.values.none { it == Readiness.Ready }
    if (enabled && backendMissing) {
      mutableState.update { it.copy(notice = UiNotice.NeedsBackend) }
    } else if (enabled && config.appFilterMode == AppFilterMode.Whitelist && config.whitelistPackages.isEmpty()) {
      mutableState.update { it.copy(notice = UiNotice.EmptyScope) }
    }
    GazeQuickTileService.requestRefresh(context)
  }

  private fun stopEngine() {
    ServiceActivation.stop(context)
  }

  private fun reconfigureEngine() = ServiceActivation.reconfigure(context)

  override suspend fun beginSelfTest() {
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

  fun resumeSelfTest() = selfTestCoordinator.resume()
  fun pauseSelfTest() = selfTestCoordinator.pause()
  override fun cancelSelfTest() = selfTestCoordinator.cancel()

  override fun clearLogs() = decisionLogs.clear()
  override fun refreshLogs() = decisionLogs.refresh()

  override suspend fun loadApps() = appsRepository.load()

  override fun close() {
    appsRepository.close()
    selfTestCoordinator.close()
    accessibilityManager?.removeAccessibilityStateChangeListener(accessibilityStateListener)
    if (Build.VERSION.SDK_INT >= 33 && accessibilityServicesListener != null) {
      accessibilityManager?.removeAccessibilityServicesStateChangeListener(accessibilityServicesListener)
    }
    runCatching { context.contentResolver.unregisterContentObserver(accessibilitySettingsObserver) }
    Shizuku.removeRequestPermissionResultListener(permissionListener)
    Shizuku.removeBinderReceivedListener(binderReceived)
    Shizuku.removeBinderDeadListener(binderDead)
    scope.cancel()
  }
}
