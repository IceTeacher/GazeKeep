package cn.rainafter.app.gazekeep.engine.runtime

import cn.rainafter.app.gazekeep.core.backend.TimeoutDeadline
import cn.rainafter.app.gazekeep.core.engine.EngineEffect
import cn.rainafter.app.gazekeep.core.engine.EngineEvent
import cn.rainafter.app.gazekeep.core.engine.EngineSnapshot
import cn.rainafter.app.gazekeep.core.engine.EngineState
import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.core.model.GazeConfig
import cn.rainafter.app.gazekeep.core.policy.GateDecision
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertIs

class GazeEngineTest {
  @Test fun cancelledEffectStopsWorkerWithoutPublishingFailure() {
    val deadline = TimeoutDeadline(20_000L)
    val runner = object : EngineEffectRunner {
      override suspend fun run(effect: EngineEffect, emit: (EngineEvent) -> Unit) {
        if (effect is EngineEffect.StartCapture) throw CancellationException("cancelled")
      }
    }
    val engine = GazeEngine(
      EngineSnapshot(
        state = EngineState.Gating(deadline),
        config = GazeConfig(enabled = true),
        backend = BackendId.Accessibility,
      ),
      runner,
      { 10_000L },
      Dispatchers.Unconfined,
    )
    try {
      engine.submit(EngineEvent.GateResult(GateDecision.Proceed, deadline = deadline))
      engine.submit(EngineEvent.UserActivity(11_000L))
      assertIs<EngineState.Capturing>(engine.snapshot.value.state)
    } finally {
      engine.close()
    }
  }
}
