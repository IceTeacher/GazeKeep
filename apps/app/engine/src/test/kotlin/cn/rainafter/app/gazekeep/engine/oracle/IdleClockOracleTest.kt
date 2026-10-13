package cn.rainafter.app.gazekeep.engine.oracle

import cn.rainafter.app.gazekeep.core.engine.EngineContext
import cn.rainafter.app.gazekeep.core.engine.EngineEvent
import cn.rainafter.app.gazekeep.core.engine.EngineReducer
import cn.rainafter.app.gazekeep.core.engine.EngineSnapshot
import cn.rainafter.app.gazekeep.core.engine.EngineState
import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.core.model.GazeConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IdleClockOracleTest {
  @Test fun ownExtensionPreservesHoldAndExternalActivityUntilRealTouch() = runBlocking {
    var now = 1_000L
    val oracle = IdleClockOracle({ now }, { 30_000L }, { 5_000L })
    oracle.start()
    val initial = oracle.deadlines.first()
    val held = EngineSnapshot(
      config = GazeConfig(enabled = true),
      backend = BackendId.Accessibility,
      cameraPermissionGranted = true,
      state = EngineState.Yielded(cn.rainafter.app.gazekeep.core.engine.YieldReason.WaitingDeadline),
      context = EngineContext(holdStartUptime = 2_000L, lookingStreak = 2, lastExternalActivityUptime = 1_000L),
    )
    now = 20_000L
    oracle.markExtended()
    val extended = oracle.deadlines.first()
    assertEquals(50_000L, extended.screenOffAtUptime)
    assertEquals(initial.lastExternalActivityUptime, extended.lastExternalActivityUptime)
    val next = EngineReducer.reduce(held, EngineEvent.DeadlineUpdated(extended), now).snapshot
    assertEquals(2_000L, next.context.holdStartUptime)
    assertEquals(2, next.context.lookingStreak)

    now = 22_000L
    oracle.markActivity()
    val touched = EngineReducer.reduce(next, EngineEvent.DeadlineUpdated(oracle.deadlines.first()), now).snapshot
    assertNull(touched.context.holdStartUptime)
    assertEquals(0, touched.context.lookingStreak)
    oracle.close()
  }
}
