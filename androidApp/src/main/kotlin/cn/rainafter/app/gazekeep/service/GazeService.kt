package cn.rainafter.app.gazekeep.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import cn.rainafter.app.gazekeep.MainActivity
import cn.rainafter.app.gazekeep.R
import cn.rainafter.app.gazekeep.backend.a11y.GazeA11yService
import cn.rainafter.app.gazekeep.backend.shizuku.PowerDumpOracle
import cn.rainafter.app.gazekeep.backend.shizuku.ShizukuKeepAwakeBackend
import cn.rainafter.app.gazekeep.backend.lsposed.LsposedKeepAwakeBackend
import cn.rainafter.app.gazekeep.core.backend.ExtendResult
import cn.rainafter.app.gazekeep.core.backend.KeepAwakeBackend
import cn.rainafter.app.gazekeep.core.backend.TimeoutOracle
import cn.rainafter.app.gazekeep.core.engine.AbortReason
import cn.rainafter.app.gazekeep.core.engine.Alert
import cn.rainafter.app.gazekeep.core.engine.EngineEffect
import cn.rainafter.app.gazekeep.core.engine.EngineEvent
import cn.rainafter.app.gazekeep.core.engine.EngineSnapshot
import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.core.model.GazeConfig
import cn.rainafter.app.gazekeep.core.policy.GateContext
import cn.rainafter.app.gazekeep.core.policy.GatePolicy
import cn.rainafter.app.gazekeep.engine.EngineEffectRunner
import cn.rainafter.app.gazekeep.engine.GazeEngine
import cn.rainafter.app.gazekeep.engine.camera.CaptureOutcome
import cn.rainafter.app.gazekeep.engine.camera.CameraXFrameSource
import cn.rainafter.app.gazekeep.engine.classify.MlKitFrameClassifier
import cn.rainafter.app.gazekeep.engine.gate.AndroidGateProbe
import cn.rainafter.app.gazekeep.engine.gate.ForegroundAppSource
import cn.rainafter.app.gazekeep.engine.m0.M0CaptureSession
import cn.rainafter.app.gazekeep.engine.oracle.IdleClockOracle
import cn.rainafter.app.gazekeep.platform.BackendSelector
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import java.io.FileDescriptor
import java.io.PrintWriter
import cn.rainafter.app.gazekeep.core.engine.EngineState

class GazeService : LifecycleService() {
    private val handler = Handler(Looper.getMainLooper())
    private var wakeRunnable: Runnable? = null
    private var screenReceiver: BroadcastReceiver? = null
    private var captureJob: Job? = null
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
    private var activeConfig: GazeConfig? = null
    private var frameSource: CameraXFrameSource? = null
    private var classifier: MlKitFrameClassifier? = null
    private val lastAlerts = mutableMapOf<Alert, Long>()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        promoteForeground()

        val config = loadConfig()
        activeConfig = config
        val permission = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val a11yBackend = GazeA11yService.current?.backend
        shizukuBackend = if (config.backendLock == cn.rainafter.app.gazekeep.core.model.BackendLock.Accessibility) {
            null
        } else {
            ShizukuKeepAwakeBackend(applicationContext)
        }
        lsposedBackend = LsposedKeepAwakeBackend(applicationContext)
        val candidates = mutableMapOf<BackendId, KeepAwakeBackend>().apply {
            a11yBackend?.let { put(BackendId.Accessibility, it) }
            shizukuBackend?.let { put(BackendId.Shizuku, it) }
            lsposedBackend?.let { put(BackendId.Lsposed, it) }
        }
        val selector = BackendSelector(config.backendLock, candidates)
        backendSelector = selector
        backend = selector.select()
        GazeA11yService.onServiceUnavailable = {
            selector.unregister(BackendId.Accessibility)
            lifecycleScope.launch { switchBackend(selector, config) }
        }
        val gateProbe = AndroidGateProbe(
            this,
            ForegroundAppSource { GazeA11yService.current?.foregroundPackage },
        )
        frameSource = CameraXFrameSource(this)
        classifier = MlKitFrameClassifier()
        GazeA11yService.current?.onExternalActivity = {
            val uptime = SystemClock.uptimeMillis()
            // SharedFlow 可能立即恢复收集器，必须先取消旧轮次，避免新 deadline 被随后取消。
            engine?.submit(EngineEvent.UserActivity(uptime))
            idleOracle?.markActivity(uptime, GazeA11yService.current?.foregroundPackage)
            powerOracle?.requestRead()
        }

        val runner = EngineEffectRunner { effect, emit ->
            runEffect(effect, gateProbe, config, emit)
        }
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
        lifecycleScope.launch {
            engine?.snapshot?.collectLatest { snapshot ->
                if (config.debugLogEnabled) Log.d(TAG, "state=${snapshot.state::class.simpleName} backend=${snapshot.backend}")
            }
        }
        shizukuBackend?.let { shizuku ->
            backendSelectionJob = lifecycleScope.launch {
                shizuku.attach()
                shizuku.readiness.collectLatest {
                    switchBackend(selector, config)
                }
            }
        }
        lsposedBackend?.let { lsposed ->
            lsposedSelectionJob = lifecycleScope.launch {
                lsposed.attach()
                lsposed.readiness.collectLatest {
                    switchBackend(selector, config)
                }
            }
        }
        a11yBackend?.let { a11y ->
            lifecycleScope.launch {
                a11y.readiness.collectLatest {
                    switchBackend(selector, config)
                }
            }
        }
    }

    override fun dump(fd: FileDescriptor, writer: PrintWriter, args: Array<out String>) {
        val snapshot = engine?.snapshot?.value
        writer.println("enabled=${snapshot?.config?.enabled}")
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 页面刷新和无障碍连接会重复启动已创建的服务，每次都兑现前台启动要求。
        promoteForeground()
        super.onStartCommand(intent, flags, startId)
        if (intent?.getBooleanExtra(BACKEND_READY_EXTRA, false) == true) {
            GazeA11yService.current?.backend?.let { backendSelector?.register(it) }
            backendSelector?.let { selector ->
                lifecycleScope.launch {
                    switchBackend(selector, activeConfig ?: loadConfig())
                }
            }
        }
        return START_STICKY
    }

    private suspend fun switchBackend(selector: BackendSelector, config: GazeConfig) {
        val target = selector.select()
        if (target == null && config.backendLock == cn.rainafter.app.gazekeep.core.model.BackendLock.Auto && backend != null) {
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
            deadlineJob = lifecycleScope.launch {
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
                scope = lifecycleScope,
                leadMs = config.leadMillis,
            ).also { it.start() }.also { powerOracle = it }
            else -> IdleClockOracle(this).also { it.start() }.also { idleOracle = it }
        }
        val activeOracle = oracle ?: return
        deadlineJob = lifecycleScope.launch {
            activeOracle.deadlines.collectLatest { deadline ->
                if (config.debugLogEnabled) Log.d(TAG, "deadline")
                engine?.submit(EngineEvent.DeadlineUpdated(deadline))
            }
        }
    }

    override fun onDestroy() {
        screenReceiver?.let { runCatching { unregisterReceiver(it) } }
        screenReceiver = null
        wakeRunnable?.let(handler::removeCallbacks)
        captureJob?.cancel()
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
        if (getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_ENABLED, false)) {
            notifyAlert(Alert.BackendUnavailable)
        }
        val selectedBackend = backend
        lifecycleScope.launch {
            runCatching { selectedBackend?.detach() }
            if (shizukuBackend !== selectedBackend) runCatching { shizukuBackend?.detach() }
            if (lsposedBackend !== selectedBackend) runCatching { lsposedBackend?.detach() }
        }
        GazeA11yService.current?.onExternalActivity = null
        GazeA11yService.onServiceUnavailable = null
        backend = null
        shizukuBackend = null
        lsposedBackend = null
        backendSelector = null
        activeConfig = null
        super.onDestroy()
    }

    private fun runEffect(
        effect: EngineEffect,
        gateProbe: AndroidGateProbe,
        config: GazeConfig,
        emit: (EngineEvent) -> Unit,
    ) {
        if (config.debugLogEnabled) Log.d(TAG, "effect=${effect::class.simpleName}")
        when (effect) {
            is EngineEffect.ScheduleWake -> {
                wakeRunnable?.let(handler::removeCallbacks)
                val runnable = Runnable { emit(EngineEvent.WakeTimerFired) }
                wakeRunnable = runnable
                handler.postAtTime(runnable, effect.atUptime)
            }
            EngineEffect.CancelWake -> wakeRunnable?.let(handler::removeCallbacks)
            is EngineEffect.RunGates -> lifecycleScope.launch {
                val snapshot = engine?.snapshot?.value
                val input = gateProbe.collect(GateContext(snapshot?.context?.darkUnknownStreak ?: 0))
                val decision = GatePolicy.decide(input, GateContext(snapshot?.context?.darkUnknownStreak ?: 0), config)
                if (config.debugLogEnabled) {
                    Log.d(TAG, "gates interactive=${input.interactive} locked=${input.keyguardLocked} foreground=${input.foregroundPackage != null} decision=${decision::class.simpleName}")
                }
                emit(EngineEvent.GateResult(decision))
            }
            is EngineEffect.StartCapture -> {
                captureJob?.cancel()
                captureJob = lifecycleScope.launch {
                    val source = frameSource ?: return@launch emit(EngineEvent.CaptureAborted(AbortReason.Error))
                    val model = classifier ?: return@launch emit(EngineEvent.CaptureAborted(AbortReason.Error))
                    val result = runCatching {
                        M0CaptureSession(source, model).capture(
                            sensitivity = effect.plan.sensitivity,
                            maxFrames = effect.plan.maxFrames,
                            timeoutMs = (effect.plan.abortAtUptime - SystemClock.uptimeMillis()).coerceAtLeast(1L),
                        )
                    }.getOrNull()
                    if (result == null) {
                        emit(EngineEvent.CaptureAborted(AbortReason.Error))
                    } else {
                        val outcome = result.outcome
                        when (outcome) {
                            is CaptureOutcome.Completed -> {
                                if (config.debugLogEnabled) {
                                    Log.d(TAG, "capture attention=${result.attention} frames=${result.frames} elapsed=${result.elapsedMs}")
                                }
                                emit(EngineEvent.CaptureFinished(result.attention, result.frames, result.elapsedMs))
                            }
                            is CaptureOutcome.Aborted -> emit(EngineEvent.CaptureAborted(outcome.reason.toEngineReason()))
                        }
                    }
                }
            }
            EngineEffect.CancelCapture -> captureJob?.cancel()
            is EngineEffect.Extend -> lifecycleScope.launch {
                val result = backend?.extend(effect.token)
                    ?: ExtendResult.Failed(IllegalStateException("backend unavailable"))
                if (config.debugLogEnabled) Log.d(TAG, "extend result=${result::class.simpleName}")
                if (result === ExtendResult.Done) {
                    idleOracle?.markExtended()
                    powerOracle?.requestRead()
                }
                emit(EngineEvent.ExtendFinished(result))
            }
            EngineEffect.Release -> lifecycleScope.launch { runCatching { backend?.release() } }
            is EngineEffect.Record -> Unit
            is EngineEffect.Notify -> notifyAlert(effect.alert)
        }
    }

    private fun notifyAlert(alert: Alert) {
        val now = SystemClock.elapsedRealtime()
        val previous = lastAlerts[alert]
        if (previous != null && now - previous < ALERT_SUPPRESSION_MS) return
        lastAlerts[alert] = now
        val message = when (alert) {
            Alert.BackendUnavailable -> "保亮后端不可用，请打开设置检查。"
            Alert.CameraPermissionLost -> "相机权限不可用，本轮不会保亮。"
            Alert.CameraUnavailable -> "相机暂时不可用，本轮不会保亮。"
            Alert.BackendFailed -> "保亮后端未响应，请打开设置检查。"
        }
        getSystemService(NotificationManager::class.java).notify(
            ALERT_NOTIFICATION_ID + alert.ordinal,
            NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("注视保亮需要处理")
                .setContentText(message)
                .setContentIntent(
                    PendingIntent.getActivity(
                        this,
                        alert.ordinal + 1,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
                .setAutoCancel(true)
                .build(),
        )
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
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(receiver, filter)
        screenReceiver = receiver
    }

    private fun loadConfig(): GazeConfig {
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val enabled = preferences.getBoolean(KEY_ENABLED, false)
        // 白名单页尚未接入；空白名单不能把每轮检测静默判为不在允许范围。
        val testMode = preferences.getBoolean(KEY_M1_TEST, false)
        val backendLock = runCatching {
            cn.rainafter.app.gazekeep.core.model.BackendLock.valueOf(
                preferences.getString(
                    KEY_BACKEND_LOCK,
                    if (testMode) cn.rainafter.app.gazekeep.core.model.BackendLock.Accessibility.name
                    else cn.rainafter.app.gazekeep.core.model.BackendLock.Auto.name,
                ) ?: cn.rainafter.app.gazekeep.core.model.BackendLock.Auto.name,
            )
        }.getOrDefault(cn.rainafter.app.gazekeep.core.model.BackendLock.Auto)
        return GazeConfig(
            enabled = enabled,
            backendLock = backendLock,
            whitelistEnabled = false,
            debugLogEnabled = testMode,
        )
    }

    private fun promoteForeground() {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, serviceNotification(), foregroundType())
        } else {
            startForeground(NOTIFICATION_ID, serviceNotification())
        }
    }

    private fun foregroundType(): Int = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "注视保亮", NotificationManager.IMPORTANCE_LOW),
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(ALERT_CHANNEL_ID, "注视保亮提醒", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }

    private fun serviceNotification(): Notification {
        val intent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("注视保亮运行中")
            .setContentText("正在等待系统超时")
            .setContentIntent(intent)
            .setOngoing(true)
            .build()
    }

    private fun cn.rainafter.app.gazekeep.engine.camera.AbortReason.toEngineReason(): AbortReason = when (this) {
        cn.rainafter.app.gazekeep.engine.camera.AbortReason.Deadline -> AbortReason.Deadline
        cn.rainafter.app.gazekeep.engine.camera.AbortReason.CameraInUse -> AbortReason.CameraInUse
        cn.rainafter.app.gazekeep.engine.camera.AbortReason.CameraDisabled -> AbortReason.CameraDisabled
        cn.rainafter.app.gazekeep.engine.camera.AbortReason.Cancelled -> AbortReason.Cancelled
        cn.rainafter.app.gazekeep.engine.camera.AbortReason.Error -> AbortReason.Error
    }

    private companion object {
        const val TAG = "GazeKeepEngine"
        const val CHANNEL_ID = "service"
        const val ALERT_CHANNEL_ID = "alerts"
        const val NOTIFICATION_ID = 1001
        const val ALERT_NOTIFICATION_ID = 2001
        const val ALERT_SUPPRESSION_MS = 10 * 60 * 1000L
        const val PREFS_NAME = "gazekeep_config"
        const val KEY_ENABLED = "enabled"
        const val KEY_M1_TEST = "m1_test_mode"
        const val KEY_BACKEND_LOCK = "backend_lock"
        const val BACKEND_READY_EXTRA = "cn.rainafter.app.gazekeep.backend_ready"
    }
}
