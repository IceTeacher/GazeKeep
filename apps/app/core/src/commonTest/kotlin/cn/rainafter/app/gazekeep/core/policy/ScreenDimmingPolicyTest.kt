package cn.rainafter.app.gazekeep.core.policy

import cn.rainafter.app.gazekeep.core.backend.TimeoutDeadline
import cn.rainafter.app.gazekeep.core.backend.captureWakeAtUptime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScreenDimmingPolicyTest {
  @Test
  fun sixtySecondTimeoutChecksBeforeEighteenSecondDimPhase() {
    val duration = ScreenDimmingPolicy.estimateDuration(60_000L, 18_000L, 0.3f)
    val dimAt = ScreenDimmingPolicy.screenDimAt(1_000L, 60_000L, duration)
    val deadline = TimeoutDeadline(61_000L, screenDimAtUptime = dimAt)
    assertEquals(43_000L, dimAt)
    assertEquals(35_000L, deadline.captureWakeAtUptime(8_000L))
    assertEquals(61_000L, deadline.screenOffAtUptime)
  }

  @Test
  fun shortTimeoutReducesDimDurationAndLongTimeoutCapsIt() {
    assertEquals(4_500L, ScreenDimmingPolicy.estimateDuration(15_000L, 18_000L, 0.3f))
    assertEquals(18_000L, ScreenDimmingPolicy.estimateDuration(600_000L, 18_000L, 0.3f))
  }

  @Test
  fun noDimmingUsesOriginalScreenOffCheck() {
    assertEquals(0L, ScreenDimmingPolicy.estimateDuration(60_000L, 0L, 0.3f))
    val dimAt = ScreenDimmingPolicy.screenDimAt(1_000L, 60_000L, 0L)
    assertEquals(53_000L, TimeoutDeadline(61_000L, screenDimAtUptime = dimAt).captureWakeAtUptime(8_000L))
  }

  @Test
  fun unreadableOrInvalidResourcesRemainUnknown() {
    assertNull(ScreenDimmingPolicy.estimateDuration(60_000L, null, 0.3f))
    assertNull(ScreenDimmingPolicy.estimateDuration(60_000L, 18_000L, null))
    assertNull(ScreenDimmingPolicy.estimateDuration(60_000L, -1L, 0.3f))
    for (ratio in listOf(-0.1f, 1.1f, Float.NaN, Float.POSITIVE_INFINITY)) {
      assertNull(ScreenDimmingPolicy.estimateDuration(60_000L, 18_000L, ratio))
    }
    assertNull(ScreenDimmingPolicy.estimateDuration(0L, 18_000L, 0.3f))
  }

  @Test
  fun invalidReportedDimDurationFallsBackWithoutChangingScreenOff() {
    for (duration in listOf(null, -1L, 60_001L)) {
      val dimAt = ScreenDimmingPolicy.screenDimAt(1_000L, 60_000L, duration)
      assertNull(dimAt)
      assertEquals(53_000L, TimeoutDeadline(61_000L, screenDimAtUptime = dimAt).captureWakeAtUptime(8_000L))
    }
  }
}
