package cn.rainafter.app.gazekeep.runtime

import cn.rainafter.app.gazekeep.core.model.BackendLock
import cn.rainafter.app.gazekeep.engine.capture.AttentionCaptureSession
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
import cn.rainafter.app.gazekeep.backend.shizuku.PowerDumpOracle
import cn.rainafter.app.gazekeep.backend.shizuku.ShizukuKeepAwakeBackend
import cn.rainafter.app.gazekeep.backend.lsposed.LsposedKeepAwakeBackend
import cn.rainafter.app.gazekeep.core.backend.KeepAwakeBackend
import cn.rainafter.app.gazekeep.core.backend.TimeoutOracle
import cn.rainafter.app.gazekeep.core.engine.Alert
import cn.rainafter.app.gazekeep.core.engine.EngineEvent
import cn.rainafter.app.gazekeep.core.engine.EngineSnapshot
import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.core.model.GazeConfig
import cn.rainafter.app.gazekeep.engine.runtime.GazeEngine
import cn.rainafter.app.gazekeep.engine.camera.CameraXFrameSource
import cn.rainafter.app.gazekeep.engine.classify.MlKitFrameClassifier
import cn.rainafter.app.gazekeep.engine.gate.AndroidGateProbe
import cn.rainafter.app.gazekeep.engine.gate.ForegroundAppSource
import cn.rainafter.app.gazekeep.engine.oracle.IdleClockOracle
import cn.rainafter.app.gazekeep.platform.BackendSelector
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import java.io.PrintWriter
import cn.rainafter.app.gazekeep.core.engine.EngineState

import cn.rainafter.app.gazekeep.notification.GazeNotifier
import cn.rainafter.app.gazekeep.engine.runtime.DefaultEngineEffectRunner
import cn.rainafter.app.gazekeep.engine.runtime.HandlerWakeScheduler

/** 服务会话拥有后端、oracle、订阅与采集硬件；关闭后不再向引擎投递事件。 */
class BackendSession(
    private val context: Context,
    parentScope: CoroutineScope,
    private val config: GazeConfig,
    private val notifier: GazeNotifier,
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
    private var backendSelectionJob: Job? = null
    private var lsposedSelectionJob: Job? = null
    private var backend: KeepAwakeBackend? = null
    private var shizukuBackend: ShizukuKeepAwakeBackend? = null
    private var lsposedBackend: LsposedKeepAwakeBackend? = null
    private var backendSelector: BackendSelector? = null
    private var frameSource: CameraXFrameSource? = null
    private var classifier: MlKitFrameClassifier? = null
    fun start() {
        RuntimeStatusStore.intentionalStop = false
        val permission = context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val a11yBackend = GazeA11yService.current?.backend
        shizukuBackend = if (config.backendLock == BackendLock.Accessibility) {
            null
        } else {
            ShizukuKeepAwakeBackend(context)
        }
        lsposedBackend = LsposedKeepAwakeBackend(context, config.leadMillis)
        val candidates = mutableMapOf<BackendId, KeepAwakeBackend>().apply {
            a11yBackend?.let { put(BackendId.Accessibility, it) }
            shizukuBackend?.let { put(BackendId.Shizuku, it) }
            lsposedBackend?.let { put(BackendId.Lsposed, it) }
        }
        val selector = BackendSelector(config.backendLock, candidates)
        backendSelector = selector
        backend = selector.select()
        val gateProbe = AndroidGateProbe(
            context,
            ForegroundAppSource { GazeA11yService.current?.foregroundPackage },
        )
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
            onUnavailable = {
                selector.unregister(BackendId.Accessibility)
                scope.launch { switchBackend(selector, config) }
            },
        )
        val runner = DefaultEngineEffectRunner(
            parentScope = scope,
            config = config,
            gateProbe = gateProbe,
            scheduler = HandlerWakeScheduler(),
            snapshot = { engine?.snapshot?.value },
            backend = { backend },
            capture = { plan ->
                AttentionCaptureSession(
                    checkNotNull(frameSource), checkNotNull(classifier),
                ).capture(
                    sensitivity = plan.sensitivity,
                    maxFrames = plan.maxFrames,
                    timeoutMs = (plan.abortAtUptime - SystemClock.uptimeMillis()).coerceAtLeast(1L),
                )
            },
            onExtended = { idleOracle?.markExtended(); powerOracle?.requestRead() },
            onRecord = { record -> record.attention?.let { attention ->
                RuntimeStatusStore.publish(RuntimeStatusStore.status.value.copy(lastDecision = attention))
            } },
            onNotify = notifier::notifyAlert,
        )
        effects = runner
        engine = GazeEngine(
            EngineSnapshot(
                config = config,
                backend = backend?.id,
                cameraPermissionGranted = permission,
            ),
            runner,
        )
        registerScreenReceiver()
        engine?.submit(EngineEvent.BackendSelected(backend?.id))
        // 先让 reducer 进入可运行状态，再启动会立即发出首个 deadline 的 oracle，
        // 避免首个事件在 Disabled 状态被丢弃后一直等不到下一轮检测。
        configureOracle(backend, config)
        scope.launch {
            engine?.snapshot?.collectLatest { snapshot ->
                RuntimeStatusStore.publish(RuntimeStatusStore.status.value.copy(running = true, backend = snapshot.backend))
                if (config.debugLogEnabled) Log.d(TAG, "state=${snapshot.state::class.simpleName} backend=${snapshot.backend}")
            }
        }
        shizukuBackend?.let { shizuku ->
            backendSelectionJob = scope.launch {
                shizuku.attach()
                shizuku.readiness.collectLatest {
                    switchBackend(selector, config)
                }
            }
        }
        lsposedBackend?.let { lsposed ->
            lsposedSelectionJob = scope.launch {
                lsposed.attach()
                lsposed.readiness.collectLatest {
                    switchBackend(selector, config)
                }
            }
        }
        a11yBackend?.let { a11y ->
            scope.launch {
                a11y.attach()
                a11y.readiness.collectLatest {
                    switchBackend(selector, config)
                }
            }
        }
    }

    fun dump(writer: PrintWriter) {
        val snapshot = engine?.snapshot?.value
        writer.println("configSource=DataStore")
        writer.println("enabled=${snapshot?.config?.enabled}")
        writer.println("leadMillis=${snapshot?.config?.leadMillis}")
        writer.println("sensitivity=${snapshot?.config?.sensitivity}")
        writer.println("whitelistEnabled=${snapshot?.config?.whitelistEnabled}")
        writer.println("maxHoldMinutes=${snapshot?.config?.maxHoldMinutes}")
        writer.println("backend=${snapshot?.backend}")
        writer.println("state=${snapshot?.state?.let { it::class.simpleName }}")
        writer.println("backendReadiness=${backend?.readiness?.value?.let { it::class.simpleName }}")
        when (val state = snapshot?.state) {
            is EngineState.Armed -> {
                writer.println("wakeAt=${state.wakeAt}")
                writer.println("screenDimAt=${state.deadline.screenDimAtUptime}")
                writer.println("screenOffAt=${state.deadline.screenOffAtUptime}")
            }
            is EngineState.Yielded -> writer.println("yieldReason=${state.reason::class.simpleName}")
            else -> Unit
        }
    }

    fun backendReady() {
        GazeA11yService.current?.backend?.let { backendSelector?.register(it) }
        backendSelector?.let { selector -> scope.launch { switchBackend(selector, config) } }
    }

    private suspend fun switchBackend(selector: BackendSelector, config: GazeConfig) {
        val target = selector.select()
        if (target == null && config.backendLock == BackendLock.Auto && backend != null) {
            return
        }
        if (target?.id == backend?.id) return

        engine?.submit(EngineEvent.BackendSelected(null))
        val previous = backend
        if (previous != null) {
            runCatching { previous.release() }
        }
        backend = target
        engine?.submit(EngineEvent.BackendSelected(target?.id))
        // Oracle 启动会立即发出首个 deadline；等 reducer 先确认后端，避免首个事件落在 Disabled 状态。
        if (target != null) {
            withTimeoutOrNull(1_000L) {
                engine?.snapshot?.first { it.backend == target.id }
            }
        }
        configureOracle(target, config)
    }

    private fun configureOracle(selected: KeepAwakeBackend?, config: GazeConfig) {
        deadlineJob?.cancel()
        deadlineJob = null
        idleOracle?.close()
        idleOracle = null
        powerOracle?.close()
        powerOracle = null

        if (selected is LsposedKeepAwakeBackend) {
            deadlineJob = scope.launch {
                selected.deadlines.collectLatest { deadline ->
                    if (config.debugLogEnabled) Log.d(TAG, "deadline")
                    engine?.submit(EngineEvent.DeadlineUpdated(deadline))
                }
            }
            oracle = null
            return
        }

        oracle = when (selected) {
            is ShizukuKeepAwakeBackend -> PowerDumpOracle(
                readPower = { selected.readPower() },
                scope = scope,
                leadMs = config.leadMillis,
            ).also { it.start() }.also { powerOracle = it }
            else -> IdleClockOracle(context).also { it.start() }.also { idleOracle = it }
        }
        val activeOracle = oracle ?: return
        deadlineJob = scope.launch {
            activeOracle.deadlines.collectLatest { deadline ->
                if (config.debugLogEnabled) Log.d(TAG, "deadline")
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
        backendSelectionJob?.cancel()
        backendSelectionJob = null
        lsposedSelectionJob?.cancel()
        lsposedSelectionJob = null
        engine?.close()
        engine = null
        oracle = null
        idleOracle?.close()
        idleOracle = null
        powerOracle?.close()
        powerOracle = null
        classifier?.close()
        classifier = null
        frameSource?.close()
        frameSource = null
        if (config.enabled && !RuntimeStatusStore.intentionalStop) {
            notifier.notifyAlert(Alert.BackendUnavailable)
        }
        lsposedBackend?.detachImmediately()
        GazeA11yService.current?.backend?.detachImmediately()
        val shellBackend = shizukuBackend
        // 销毁回调返回后 scope 会取消；超时恢复必须拥有独立的清理任务。
        if (shellBackend != null) CoroutineScope(Dispatchers.IO).launch {
            try {
                shellBackend.release()
            } finally {
                runCatching { shellBackend.detach() }
            }
        }
        a11ySubscription?.close()
        a11ySubscription = null
        backend = null
        shizukuBackend = null
        lsposedBackend = null
        backendSelector = null
        RuntimeStatusStore.publish(RuntimeStatus())
    }

    private fun registerScreenReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        engine?.submit(EngineEvent.ScreenOff)
                        idleOracle?.markActivity()
                        powerOracle?.requestRead()
                    }
                    Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                        engine?.submit(EngineEvent.ScreenOn)
                        idleOracle?.markActivity()
                        powerOracle?.requestRead()
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") context.registerReceiver(receiver, filter)
        screenReceiver = receiver
    }

    private companion object { const val TAG = "GazeKeepEngine" }
}
