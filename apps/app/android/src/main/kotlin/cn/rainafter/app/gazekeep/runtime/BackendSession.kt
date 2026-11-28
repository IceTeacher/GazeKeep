package cn.rainafter.app.gazekeep.runtime

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import cn.rainafter.app.gazekeep.backend.a11y.GazeA11yService
import cn.rainafter.app.gazekeep.backend.lsposed.LsposedKeepAwakeBackend
import cn.rainafter.app.gazekeep.backend.shizuku.client.ShizukuKeepAwakeBackend
import cn.rainafter.app.gazekeep.backend.shizuku.oracle.PowerDumpOracle
import cn.rainafter.app.gazekeep.core.backend.KeepAwakeBackend
import cn.rainafter.app.gazekeep.core.backend.TimeoutOracle
import cn.rainafter.app.gazekeep.core.engine.Alert
import cn.rainafter.app.gazekeep.core.engine.DecisionRecord
import cn.rainafter.app.gazekeep.core.engine.EngineContext
import cn.rainafter.app.gazekeep.core.engine.EngineEvent
import cn.rainafter.app.gazekeep.core.engine.EngineSnapshot
import cn.rainafter.app.gazekeep.core.engine.EngineState
import cn.rainafter.app.gazekeep.core.engine.toLastOutcome
import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.core.model.GazeConfig
import cn.rainafter.app.gazekeep.engine.camera.CameraXFrameSource
import cn.rainafter.app.gazekeep.engine.camera.CaptureOwnershipCoordinator
import cn.rainafter.app.gazekeep.engine.capture.AttentionCaptureSession
import cn.rainafter.app.gazekeep.engine.classify.MlKitFrameClassifier
import cn.rainafter.app.gazekeep.engine.gate.AndroidGateProbe
import cn.rainafter.app.gazekeep.engine.gate.ForegroundAppSource
import cn.rainafter.app.gazekeep.engine.oracle.IdleClockOracle
import cn.rainafter.app.gazekeep.engine.runtime.DefaultEngineEffectRunner
import cn.rainafter.app.gazekeep.engine.runtime.GazeEngine
import cn.rainafter.app.gazekeep.engine.runtime.HandlerWakeScheduler
import cn.rainafter.app.gazekeep.engine.runtime.logName
import cn.rainafter.app.gazekeep.notification.GazeNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.PrintWriter

/** 服务会话拥有后端、oracle、订阅与采集硬件；关闭后不再向引擎投递事件。 */
class BackendSession(
  private val context: Context,
  parentScope: CoroutineScope,
  private val config: GazeConfig,
  private val notifier: GazeNotifier,
  private val recordDecision: (DecisionRecord) -> Unit = {},
  private val ownership: CaptureOwnershipCoordinator,
) : AutoCloseable {
  private val jobs = kotlinx.coroutines.SupervisorJob(parentScope.coroutineContext[Job])
  private val scope = CoroutineScope(parentScope.coroutineContext + jobs)
  private var closed = false
  private var screenReceiver: BroadcastReceiver? = null
  private var effects: DefaultEngineEffectRunner? = null
  private var a11ySubscription: AutoCloseable? = null
  private var engine: GazeEngine? = null
  private var oracle: TimeoutOracle? = null
  private var idleOracle: IdleClockOracle? = null
  private var powerOracle: PowerDumpOracle? = null
  private var deadlineJob: Job? = null
  private var orchestrator: SessionBackendOrchestrator? = null

  @Volatile private var backend: KeepAwakeBackend? = null
  private var frameSource: CameraXFrameSource? = null
  private var classifier: MlKitFrameClassifier? = null
  private var gateProbe: AndroidGateProbe? = null
  fun start() {
    RuntimeStatusStore.intentionalStop = false
    val permission = context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val gateProbe = AndroidGateProbe(
      context,
      ForegroundAppSource {
        when (val selected = backend) {
          is ShizukuKeepAwakeBackend -> selected.foregroundPackage()
          is LsposedKeepAwakeBackend -> engine?.snapshot?.value?.context?.foregroundPackage
          else -> GazeA11yService.current?.foregroundPackage
        }
      },
    )
    this.gateProbe = gateProbe
    frameSource = CameraXFrameSource(context)
    classifier = MlKitFrameClassifier()
    a11ySubscription = GazeA11yService.signals.subscribe(
      onActivity = {
        val uptime = SystemClock.uptimeMillis()
        // 先取消旧采集，再发新 deadline，避免新等待被旧活动事件取消。
        engine?.submit(EngineEvent.UserActivity(uptime))
        idleOracle?.markActivity(uptime, GazeA11yService.current?.foregroundPackage)
        powerOracle?.requestRead()
      },
      onUnavailable = {},
    )
    val runner = DefaultEngineEffectRunner(
      parentScope = scope,
      config = config,
      gateProbe = gateProbe,
      scheduler = HandlerWakeScheduler(),
      snapshot = { engine?.snapshot?.value },
      backend = { backend },
      capture = { plan ->
        ownership.capture {
          AttentionCaptureSession(checkNotNull(frameSource), checkNotNull(classifier)).capture(plan)
        } ?: cn.rainafter.app.gazekeep.engine.capture.AttentionCaptureResult(
          attention = cn.rainafter.app.gazekeep.core.model.Attention.Unknown,
          frames = emptyList(),
          outcome = cn.rainafter.app.gazekeep.engine.camera.CaptureOutcome.Aborted(
            cn.rainafter.app.gazekeep.core.engine.AbortReason.Cancelled,
          ),
          elapsedMs = 0L,
        )
      },
      onExtended = {
        idleOracle?.markExtended()
        powerOracle?.requestRead()
      },
      onRecord = { record ->
        recordDecision(record)
        RuntimeStatusStore.recordOutcome(record.toLastOutcome(), record.attention)
      },
      onNotify = notifier::notifyAlert,
      onClearAlert = notifier::cancelAlert,
      // Shizuku 退路写回原超时后，按真实熄屏点重新安排检测。
      onReleased = { powerOracle?.requestRead() },
    )
    effects = runner
    engine = GazeEngine(
      EngineSnapshot(
        context = EngineContext(isCharging = AndroidGateProbe.currentChargingState(context)),
        config = config,
        backend = null,
        cameraPermissionGranted = permission,
      ),
      runner,
    )
    registerScreenReceiver()
    scope.launch {
      engine?.snapshot?.collectLatest { snapshot ->
        RuntimeStatusStore.updateRunning(running = true, backend = snapshot.backend)
        if (config.debugLogEnabled) {
          val stateName = logName(snapshot.state)
          val target = snapshot.context.foregroundPackage ?: snapshot.backend?.name ?: "system"
          Log.d(
            TAG,
            "state=$stateName backend=${snapshot.backend}",
          )
          cn.rainafter.app.gazekeep.notification.DecisionLogs.get(context).log(
            level = cn.rainafter.app.gazekeep.ui.contract.LogLevel.Debug,
            tag = TAG,
            target = target,
            message = "GazeEngine [main] state=$stateName backend=${snapshot.backend ?: "-"}",
          )
        }
      }
    }
    orchestrator = SessionBackendOrchestrator(
      lock = config.backendLock,
      scope = scope,
      createShizuku = { ShizukuKeepAwakeBackend(context) },
      hasPendingShizukuBind = { (it as? ShizukuKeepAwakeBackend)?.hasPendingBind() == true },
      createLsposed = {
        LsposedKeepAwakeBackend(
          leadMillis = config.leadMillis,
          // 隐私指示器状态只在后端真正选中后同步，避免 attach 顺序覆盖非 LSPosed 后端的恢复状态。
          hideCameraIndicator = false,
        )
      },
      currentAccessibility = { GazeA11yService.current?.backend },
      accessibilityAvailability = GazeA11yService.availableBackend,
      onShizukuReadiness = RuntimeStatusStore::updateShizukuReadiness,
      onRetainSameBackend = { retained ->
        syncIndicator(retained, config)
      },
      onBackendChanged = { previous, target ->
        applyBackendChange(previous, target, config)
      },
    ).also { it.start() }
  }

  fun dump(writer: PrintWriter) {
    val snapshot = engine?.snapshot?.value
    writer.println("configSource=DataStore")
    writer.println("enabled=${snapshot?.config?.enabled}")
    writer.println("leadMillis=${snapshot?.config?.leadMillis}")
    writer.println("sensitivity=${snapshot?.config?.sensitivity}")
    writer.println("appFilterMode=${snapshot?.config?.appFilterMode}")
    writer.println("whitelistCount=${snapshot?.config?.whitelistPackages?.size}")
    writer.println("blacklistCount=${snapshot?.config?.blacklistPackages?.size}")
    writer.println("maxHoldMinutes=${snapshot?.config?.maxHoldMinutes}")
    writer.println("unlimitedHoldWhileCharging=${snapshot?.config?.unlimitedHoldWhileCharging}")
    writer.println("faceDownGateEnabled=${snapshot?.config?.faceDownGateEnabled}")
    writer.println("isCharging=${snapshot?.context?.isCharging}")
    writer.println("hideCameraIndicator=${snapshot?.config?.hideCameraIndicator}")
    writer.println("backend=${snapshot?.backend}")
    writer.println("state=${snapshot?.state?.let(::logName)}")
    writer.println("backendReadiness=${backend?.readiness?.value?.let(::logName)}")
    when (val state = snapshot?.state) {
      is EngineState.Armed -> {
        writer.println("wakeAt=${state.wakeAt}")
        writer.println("screenDimAt=${state.deadline.screenDimAtUptime}")
        writer.println("screenOffAt=${state.deadline.screenOffAtUptime}")
      }

      is EngineState.Yielded -> writer.println("yieldReason=${logName(state.reason)}")

      else -> Unit
    }
  }

  private fun syncIndicator(selected: KeepAwakeBackend?, config: GazeConfig) {
    val lsposed = orchestrator?.lsposedBackend as? LsposedKeepAwakeBackend ?: return
    runCatching {
      lsposed.syncHideCameraIndicator(config.hideCameraIndicator && selected is LsposedKeepAwakeBackend)
    }
  }

  private suspend fun applyBackendChange(previous: KeepAwakeBackend?, target: KeepAwakeBackend?, config: GazeConfig) {
    switchBackendTransition(
      previous = previous,
      target = target,
      stopOracles = ::stopOracles,
      submitEvent = { event -> engine?.submit(event) },
      awaitDisabled = {
        withTimeoutOrNull(1_000L) {
          engine?.snapshot?.first { it.state == EngineState.Disabled }
        }
      },
      quiesceEffects = {
        withTimeoutOrNull(2_000L) {
          effects?.quiesce()
        }
      },
      assignBackend = { next ->
        backend = next
        syncIndicator(next, config)
      },
      awaitSelected = { id ->
        withTimeoutOrNull(1_000L) {
          engine?.snapshot?.first { it.backend == id }
        }
      },
      startOracle = { next -> configureOracle(next, config) },
    )
  }

  private fun stopOracles() {
    deadlineJob?.cancel()
    deadlineJob = null
    idleOracle?.close()
    idleOracle = null
    powerOracle?.close()
    powerOracle = null
  }

  private fun configureOracle(selected: KeepAwakeBackend?, config: GazeConfig) {
    stopOracles()

    if (selected is LsposedKeepAwakeBackend) {
      deadlineJob = scope.launch {
        selected.deadlines.collectLatest { deadline ->
          if (config.debugLogEnabled) {
            Log.d(TAG, "deadline")
            cn.rainafter.app.gazekeep.notification.DecisionLogs.get(context).log(
              level = cn.rainafter.app.gazekeep.ui.contract.LogLevel.Verbose,
              tag = TAG,
              target = deadline.foregroundPackage ?: "Lsposed",
              message = "TimeoutOracle [main] deadline updated screenOffAt=${deadline.screenOffAtUptime}",
            )
          }
          engine?.submit(EngineEvent.DeadlineUpdated(deadline))
        }
      }
      oracle = null
      return
    }

    oracle = when (selected) {
      is ShizukuKeepAwakeBackend -> PowerDumpOracle(
        readPower = { selected.readPower() },
        isExternalActivity = selected::isExternalActivity,
        scope = scope,
        leadMs = config.leadMillis,
      ).also { it.start() }.also { powerOracle = it }

      else -> IdleClockOracle(context).also { it.start() }.also { idleOracle = it }
    }
    val activeOracle = oracle ?: return
    deadlineJob = scope.launch {
      activeOracle.deadlines.collectLatest { deadline ->
        if (config.debugLogEnabled) {
          Log.d(TAG, "deadline")
          cn.rainafter.app.gazekeep.notification.DecisionLogs.get(context).log(
            level = cn.rainafter.app.gazekeep.ui.contract.LogLevel.Verbose,
            tag = TAG,
            target = deadline.foregroundPackage ?: selected?.id?.name ?: "system",
            message = "TimeoutOracle [main] deadline updated screenOffAt=${deadline.screenOffAtUptime}",
          )
        }
        engine?.submit(EngineEvent.DeadlineUpdated(deadline))
      }
    }
  }

  override fun close() {
    if (closed) return
    closed = true
    jobs.cancel()
    screenReceiver?.let { runCatching { context.unregisterReceiver(it) } }
    screenReceiver = null
    effects?.close()
    effects = null
    deadlineJob?.cancel()
    deadlineJob = null
    orchestrator?.cancel()
    val shizuku = orchestrator?.shizukuBackend as? ShizukuKeepAwakeBackend
    val lsposed = orchestrator?.lsposedBackend as? LsposedKeepAwakeBackend
    orchestrator = null
    engine?.close()
    engine = null
    oracle = null
    idleOracle?.close()
    idleOracle = null
    powerOracle?.close()
    powerOracle = null
    gateProbe?.close()
    gateProbe = null
    classifier?.close()
    classifier = null
    frameSource?.close()
    frameSource = null
    if (config.enabled && !RuntimeStatusStore.intentionalStop) {
      notifier.notifyAlert(Alert.BackendUnavailable)
    }
    lsposed?.detachImmediately()
    GazeA11yService.current?.backend?.detachImmediately()
    // 销毁回调返回后 scope 会取消；超时恢复与 UserService 解绑必须在独立清理作用域中完成并由新会话等待。
    shizuku?.detachInBackground()
    a11ySubscription?.close()
    a11ySubscription = null
    backend = null
    RuntimeStatusStore.updateRunning(running = false, backend = null)
  }

  private fun registerScreenReceiver() {
    val receiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
          Intent.ACTION_SCREEN_OFF -> {
            gateProbe?.setInteractive(false)
            engine?.submit(EngineEvent.ScreenOff)
            idleOracle?.markActivity()
            powerOracle?.requestRead()
          }

          Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
            gateProbe?.setInteractive(true)
            engine?.submit(EngineEvent.ScreenOn)
            idleOracle?.markActivity()
            powerOracle?.requestRead()
          }

          Intent.ACTION_POWER_CONNECTED -> engine?.submit(EngineEvent.ChargingChanged(true))

          Intent.ACTION_POWER_DISCONNECTED -> engine?.submit(EngineEvent.ChargingChanged(false))
        }
      }
    }
    val filter = IntentFilter().apply {
      addAction(Intent.ACTION_SCREEN_OFF)
      addAction(Intent.ACTION_SCREEN_ON)
      addAction(Intent.ACTION_USER_PRESENT)
      addAction(Intent.ACTION_POWER_CONNECTED)
      addAction(Intent.ACTION_POWER_DISCONNECTED)
    }
    if (Build.VERSION.SDK_INT >= 33) {
      context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
      @Suppress("DEPRECATION")
      context.registerReceiver(receiver, filter)
    }
    screenReceiver = receiver
  }

  internal companion object {
    const val TAG = "GazeKeepEngine"

    /** 切换后端时先停旧 oracle 并取消等待在途采集/延长结束，再强制释放与替换引用。 */
    suspend fun switchBackendTransition(
      previous: KeepAwakeBackend?,
      target: KeepAwakeBackend?,
      stopOracles: () -> Unit,
      submitEvent: (EngineEvent) -> Unit,
      awaitDisabled: suspend () -> Unit,
      quiesceEffects: suspend () -> Unit,
      assignBackend: (KeepAwakeBackend?) -> Unit,
      awaitSelected: suspend (BackendId) -> Unit,
      startOracle: (KeepAwakeBackend?) -> Unit,
    ) {
      stopOracles()
      if (previous != null) {
        submitEvent(EngineEvent.BackendSwitching)
        awaitDisabled()
        quiesceEffects()
        // 被切走的后端之后收不到熄屏释放，须在旧轮完全静止后立即恢复。
        runCatching { previous.forceRelease() }
      }
      assignBackend(target)
      submitEvent(EngineEvent.BackendSelected(target?.id))
      // Oracle 启动会立即发出首个 deadline；等 reducer 先确认后端，避免首个事件落在 Disabled 状态。
      if (target != null) {
        awaitSelected(target.id)
      }
      startOracle(target)
    }
  }
}
