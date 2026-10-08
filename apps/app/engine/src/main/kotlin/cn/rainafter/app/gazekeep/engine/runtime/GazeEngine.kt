package cn.rainafter.app.gazekeep.engine.runtime

import android.os.SystemClock
import cn.rainafter.app.gazekeep.core.backend.ExtendResult
import cn.rainafter.app.gazekeep.core.engine.AbortReason
import cn.rainafter.app.gazekeep.core.engine.EngineEffect
import cn.rainafter.app.gazekeep.core.engine.EngineEvent
import cn.rainafter.app.gazekeep.core.engine.EngineReducer
import cn.rainafter.app.gazekeep.core.engine.EngineSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GazeEngine(
  initial: EngineSnapshot,
  private val effectRunner: EngineEffectRunner,
  private val clock: () -> Long = { SystemClock.uptimeMillis() },
) : AutoCloseable {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
  private val events = Channel<EngineEvent>(Channel.UNLIMITED)
  private val _snapshot = MutableStateFlow(initial)
  val snapshot: StateFlow<EngineSnapshot> = _snapshot.asStateFlow()
  private val worker: Job = scope.launch {
    for (event in events) {
      val transition = EngineReducer.reduce(_snapshot.value, event, clock())
      _snapshot.value = transition.snapshot
      for (effect in transition.effects) {
        runCatching {
          effectRunner.run(effect) { nextEvent -> events.trySend(nextEvent) }
        }.onFailure { error ->
          when (effect) {
            is EngineEffect.StartCapture -> events.trySend(EngineEvent.CaptureAborted(AbortReason.Error))
            is EngineEffect.Extend -> events.trySend(EngineEvent.ExtendFinished(ExtendResult.Failed(error)))
            else -> Unit
          }
        }
      }
    }
  }

  fun submit(event: EngineEvent) {
    events.trySend(event)
  }

  override fun close() {
    events.close()
    worker.cancel()
    scope.cancel()
  }
}
