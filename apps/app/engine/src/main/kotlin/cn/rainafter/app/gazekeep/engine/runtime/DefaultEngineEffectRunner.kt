package cn.rainafter.app.gazekeep.engine.runtime

import android.util.Log
import cn.rainafter.app.gazekeep.core.backend.ExtendResult
import cn.rainafter.app.gazekeep.core.backend.KeepAwakeBackend
import cn.rainafter.app.gazekeep.core.engine.AbortReason
import cn.rainafter.app.gazekeep.core.engine.Alert
import cn.rainafter.app.gazekeep.core.engine.CapturePlan
import cn.rainafter.app.gazekeep.core.engine.DecisionRecord
import cn.rainafter.app.gazekeep.core.engine.EngineEffect
import cn.rainafter.app.gazekeep.core.engine.EngineEvent
import cn.rainafter.app.gazekeep.core.engine.EngineSnapshot
import cn.rainafter.app.gazekeep.core.model.GazeConfig
import cn.rainafter.app.gazekeep.core.policy.GateContext
import cn.rainafter.app.gazekeep.core.policy.GatePolicy
import cn.rainafter.app.gazekeep.engine.camera.CaptureOutcome
import cn.rainafter.app.gazekeep.engine.capture.AttentionCaptureResult
import cn.rainafter.app.gazekeep.engine.gate.GateProbe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import cn.rainafter.app.gazekeep.engine.camera.AbortReason as CameraAbortReason

/** 所有异步副作用属于运行会话，关闭时先取消任务，再由会话释放硬件。 */
class DefaultEngineEffectRunner(
    parentScope: CoroutineScope,
    private val config: GazeConfig,
    private val gateProbe: GateProbe,
    private val scheduler: WakeScheduler,
    private val snapshot: () -> EngineSnapshot?,
    private val backend: () -> KeepAwakeBackend?,
    private val capture: suspend (CapturePlan) -> AttentionCaptureResult,
    private val onExtended: () -> Unit,
    private val onRecord: (DecisionRecord) -> Unit,
    private val onNotify: (Alert) -> Unit,
) : EngineEffectRunner,
    AutoCloseable {
    private val jobs = kotlinx.coroutines.SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + jobs)
    private var captureJob: Job? = null
    override suspend fun run(effect: EngineEffect, emit: (EngineEvent) -> Unit) {
        if (config.debugLogEnabled) Log.d(TAG, "effect=${effect::class.simpleName}")
        when (effect) {
            is EngineEffect.ScheduleWake -> {
                scheduler.cancel()
                scheduler.schedule(effect.atUptime) { emit(EngineEvent.WakeTimerFired) }
            }

            EngineEffect.CancelWake -> scheduler.cancel()

            is EngineEffect.RunGates -> scope.launch {
                val snapshot = snapshot()
                val input = gateProbe.collect(GateContext(snapshot?.context?.darkUnknownStreak ?: 0))
                val decision = GatePolicy.decide(input, GateContext(snapshot?.context?.darkUnknownStreak ?: 0), config)
                if (config.debugLogEnabled) {
                    Log.d(TAG, "gates interactive=${input.interactive} locked=${input.keyguardLocked} foreground=${input.foregroundPackage != null} decision=${decision::class.simpleName}")
                }
                emit(EngineEvent.GateResult(decision))
            }

            is EngineEffect.StartCapture -> {
                captureJob?.cancel()
                captureJob = scope.launch {
                    val result = try {
                        capture(effect.plan)
                    } catch (error: CancellationException) {
                        // 已取消的旧采集不能向下一轮回灌错误事件。
                        throw error
                    } catch (_: Exception) {
                        null
                    }
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

            is EngineEffect.Extend -> scope.launch {
                val result = backend()?.extend(effect.token)
                    ?: ExtendResult.Failed(IllegalStateException("backend unavailable"))
                if (config.debugLogEnabled) Log.d(TAG, "extend result=${result::class.simpleName}")
                if (result === ExtendResult.Done) {
                    onExtended()
                }
                emit(EngineEvent.ExtendFinished(result))
            }

            EngineEffect.Release -> scope.launch { runCatching { backend()?.release() } }

            is EngineEffect.Record -> onRecord(effect.record)

            is EngineEffect.Notify -> onNotify(effect.alert)
        }
    }

    private fun CameraAbortReason.toEngineReason(): AbortReason = when (this) {
        CameraAbortReason.Deadline -> AbortReason.Deadline
        CameraAbortReason.CameraInUse -> AbortReason.CameraInUse
        CameraAbortReason.CameraDisabled -> AbortReason.CameraDisabled
        CameraAbortReason.Cancelled -> AbortReason.Cancelled
        CameraAbortReason.Error -> AbortReason.Error
    }

    override fun close() {
        scheduler.cancel()
        jobs.cancel()
    }
    private companion object {
        const val TAG = "GazeKeepEngine"
    }
}
