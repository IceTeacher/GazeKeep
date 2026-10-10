package cn.rainafter.app.gazekeep.xposed.power

import kotlin.test.Test
import kotlin.test.assertEquals

class PreTimeoutScheduleTest {
  @Test
  fun longTimeoutNotifiesBeforeDimming() {
    assertEquals(40_000L, preTimeoutAt(60_000L, 48_000L, 8_000L, 0L))
  }

  @Test
  fun unknownOrInvalidDimmingFallsBackToScreenOff() {
    for (dimAt in listOf(-1L, 0L, 70_000L)) {
      assertEquals(52_000L, preTimeoutAt(60_000L, dimAt, 8_000L, 0L))
    }
  }

  @Test
  fun attachingAfterCheckTimeNotifiesImmediately() {
    assertEquals(49_000L, preTimeoutAt(60_000L, 48_000L, 8_000L, 49_000L))
  }

  @Test
  fun zeroLeadUsesDimOrCapsAtMinimumCaptureWindowBeforeScreenOff() {
    assertEquals(48_000L, preTimeoutAt(60_000L, 48_000L, 0L, 0L))
    assertEquals(57_500L, preTimeoutAt(60_000L, -1L, 0L, 0L))
  }
}
