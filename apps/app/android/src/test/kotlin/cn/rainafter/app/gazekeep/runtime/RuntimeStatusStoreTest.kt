package cn.rainafter.app.gazekeep.runtime

import cn.rainafter.app.gazekeep.core.model.Attention
import cn.rainafter.app.gazekeep.core.model.BackendId
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class RuntimeStatusStoreTest {
  @Test fun sessionClosePreservesLastDecisionUntilExplicitlyCleared() {
    RuntimeStatusStore.resetForTest()
    RuntimeStatusStore.updateRunning(running = true, backend = BackendId.Shizuku)
    RuntimeStatusStore.recordDecision(Attention.Looking)
    assertEquals(Attention.Looking, RuntimeStatusStore.status.value.lastDecision)

    // 服务重配或会话关闭时保留最近一次判定状态
    RuntimeStatusStore.updateRunning(running = false, backend = null)
    assertFalse(RuntimeStatusStore.status.value.running)
    assertEquals(Attention.Looking, RuntimeStatusStore.status.value.lastDecision)

    // 用户显式关闭总开关时才清空最近判定
    RuntimeStatusStore.clearLastDecision()
    assertNull(RuntimeStatusStore.status.value.lastDecision)
  }
}
