package cn.rainafter.app.gazekeep.runtime

import cn.rainafter.app.gazekeep.core.engine.DecisionReason
import cn.rainafter.app.gazekeep.core.engine.LastOutcome
import cn.rainafter.app.gazekeep.core.model.Attention
import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.core.policy.SkipReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class RuntimeStatusStoreTest {
  @Test fun sessionClosePreservesLastDecisionAndOutcomeUntilExplicitlyCleared() {
    RuntimeStatusStore.resetForTest()
    RuntimeStatusStore.updateRunning(running = true, backend = BackendId.Shizuku)
    val outcome = LastOutcome(DecisionReason.GateSkipped, skipReason = SkipReason.PowerSaveMode)
    RuntimeStatusStore.recordOutcome(outcome = outcome, attention = Attention.Looking)
    assertEquals(Attention.Looking, RuntimeStatusStore.status.value.lastDecision)
    assertEquals(outcome, RuntimeStatusStore.status.value.lastOutcome)

    // 服务重配或会话关闭时保留最近一次判定与结果摘要
    RuntimeStatusStore.updateRunning(running = false, backend = null)
    assertFalse(RuntimeStatusStore.status.value.running)
    assertEquals(Attention.Looking, RuntimeStatusStore.status.value.lastDecision)
    assertEquals(outcome, RuntimeStatusStore.status.value.lastOutcome)

    // 用户显式关闭总开关时才清空
    RuntimeStatusStore.clearLastDecision()
    assertNull(RuntimeStatusStore.status.value.lastDecision)
    assertNull(RuntimeStatusStore.status.value.lastOutcome)
  }
}
