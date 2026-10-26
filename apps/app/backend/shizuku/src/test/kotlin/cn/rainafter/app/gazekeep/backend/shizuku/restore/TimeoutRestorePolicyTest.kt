package cn.rainafter.app.gazekeep.backend.shizuku.restore

import cn.rainafter.app.gazekeep.backend.shizuku.protocol.PowerSnapshot
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimeoutRestorePolicyTest {
  private fun snapshot(lastActivity: Long?, interactive: Boolean = true) =
    PowerSnapshot(lastActivity, 67_000, null, hasScreenWakeLock = false, interactive = interactive)

  @Test fun defersWhileScreenOnWhenRestoringWouldExpireImmediately() {
    assertTrue(TimeoutRestorePolicy.wouldExpireImmediately(snapshot(100_000), nowUptime = 160_000, original = 15_000))
    assertTrue(TimeoutRestorePolicy.wouldExpireImmediately(snapshot(100_000), nowUptime = 114_000, original = 15_000))
  }

  @Test fun restoresWhenRecentActivityLeavesTimeBeforeTheOriginalTimeout() {
    assertFalse(TimeoutRestorePolicy.wouldExpireImmediately(snapshot(100_000), nowUptime = 105_000, original = 15_000))
  }

  @Test fun restoresWhenScreenIsOffOrPowerStateIsUnknown() {
    assertFalse(
      TimeoutRestorePolicy.wouldExpireImmediately(snapshot(100_000, interactive = false), 160_000, 15_000),
    )
    assertFalse(TimeoutRestorePolicy.wouldExpireImmediately(snapshot(null), 160_000, 15_000))
    assertFalse(TimeoutRestorePolicy.wouldExpireImmediately(null, 160_000, 15_000))
  }
}
