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
import cn.rainafter.app.gazekeep.core.engine.EngineState
import cn.rainafter.app.gazekeep.core.model.GazeConfig
import cn.rainafter.app.gazekeep.core.policy.GateContext
import cn.rainafter.app.gazekeep.core.policy.GatePolicy
import cn.rainafter.app.gazekeep.engine.camera.CaptureOutcome
import cn.rainafter.app.gazekeep.engine.capture.AttentionCaptureResult
import cn.rainafter.app.gazekeep.engine.gate.GateProbe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

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
  private var gateJob: Job? = null
  private var extendJob: Job? = null
  private var holdJob: Job? = null
  private val backendLock = kotlinx.coroutines.sync.Mutex()
  override suspend fun run(effect: EngineEffect, emit: (EngineEvent) -> Unit) {
    if (config.debugLogEnabled) Log.d(TAG, "effect=${logName(effect)}")
    when (effect) {
      is EngineEffect.ScheduleWake -> {
        scheduler.cancel()
        scheduler.schedule(effect.atUptime) { emit(EngineEvent.ScheduledWakeFired(effect.atUptime)) }
      }

      EngineEffect.CancelWake -> scheduler.cancel()

      is EngineEffect.ScheduleHoldLimit -> {
        holdJob?.cancel()
        holdJob = scope.launch {
          kotlinx.coroutines.delay((effect.atUptime - android.os.SystemClock.uptimeMillis()).coerceAtLeast(0L))
          emit(EngineEvent.HoldLimitReached(effect.startUptime))
        }
      }

      EngineEffect.CancelHoldLimit -> holdJob?.cancel()

      is EngineEffect.RunGates -> {
        gateJob?.cancel()
        gateJob = scope.launch {
          val snapshot = snapshot()
          val input = gateProbe.collect()
          currentCoroutineContext().ensureActive()
          val decision = GatePolicy.decide(input, GateContext(snapshot?.context?.darkUnknownStreak ?: 0), config)
          if (config.debugLogEnabled) {
            Log.d(
              TAG,
              "gates interactive=${input.interactive} locked=${input.keyguardLocked} " +
                "foreground=${input.foregroundPackage != null} decision=${logName(decision)}",
            )
          }
          emit(EngineEvent.GateResult(decision, input.foregroundPackage, effect.deadline))
        }
      }

      is EngineEffect.StartCapture -> {
        captureJob?.cancel()
        val token = (snapshot()?.state as? EngineState.Capturing)?.deadline?.token
        captureJob = scope.launch {
          val result = try {
            capture(effect.plan)
          } catch (error: CancellationException) {
            // 已取消的旧采集不能向下一轮回灌错误事件。
            throw error
          } catch (_: Exception) {
            null
          }
          currentCoroutineContext().ensureActive()
          if (result == null) {
            emit(EngineEvent.CaptureAborted(AbortReason.Error, token))
          } else {
            val outcome = result.outcome
            when (outcome) {
              is CaptureOutcome.Completed -> {
                if (config.debugLogEnabled) {
                  Log.d(
                    TAG,
                    "capture attention=${result.attention} frames=${result.frames} elapsed=${result.elapsedMs}",
                  )
                }
                emit(EngineEvent.CaptureFinished(result.attention, result.frames, result.elapsedMs, token))
              }

              is CaptureOutcome.Aborted -> emit(
                EngineEvent.CaptureAborted(outcome.reason, token, result.elapsedMs),
              )
            }
          }
        }
      }

      EngineEffect.CancelCapture -> {
        captureJob?.cancel()
        gateJob?.cancel()
        extendJob?.cancel()
      }

      is EngineEffect.Extend -> {
        val selected = backend()
        extendJob = scope.launch {
          val result =
            try {
              backendLock.withLock { selected?.extend(effect.token) }
            } catch (
              error: CancellationException,
            ) {
              throw error
            } catch (error: Exception) {
              ExtendResult.Failed(error)
            }
              ?: ExtendResult.Failed(IllegalStateException("backend unavailable"))
          if (config.debugLogEnabled) Log.d(TAG, "extend result=${logName(result)}")
          currentCoroutineContext().ensureActive()
          emit(EngineEvent.ExtendFinished(result, effect.token))
          if (result === ExtendResult.Done) {
            onExtended()
          }
        }
      }

      EngineEffect.Release -> {
        val selected = backend()
        backendLock.withLock { selected?.release() }
      }

      is EngineEffect.Record -> onRecord(effect.record)

      is EngineEffect.Notify -> onNotify(effect.alert)
    }
  }

  override fun close() {
    scheduler.cancel()
    jobs.cancel()
  }
  private companion object {
    const val TAG = "GazeKeepEngine"
  }
}
