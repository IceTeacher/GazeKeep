package cn.rainafter.app.gazekeep.backend.shizuku.client

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExtendSequenceTest {
  @Test fun olderSequenceIsAlwaysStale() {
    assertTrue(ExtendSequence.isStale(value = 9, last = 10, timeoutFallbackActive = false))
    assertTrue(ExtendSequence.isStale(value = 9, last = 10, timeoutFallbackActive = true))
  }

  @Test fun repeatedSequenceIsStaleOnlyWithoutAnActiveTimeoutFallback() {
    assertTrue(ExtendSequence.isStale(value = 10, last = 10, timeoutFallbackActive = false))
    // 退路不调用 userActivity，系统活动时间不变；持续注视必须能用同一序号再次延长。
    assertFalse(ExtendSequence.isStale(value = 10, last = 10, timeoutFallbackActive = true))
  }

  @Test fun newerSequenceIsAccepted() {
    assertFalse(ExtendSequence.isStale(value = 11, last = 10, timeoutFallbackActive = false))
  }
}
