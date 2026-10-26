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

  @Test fun defersWhenTheRemainingTimeIsTooShortToDetectAndExtend() {
    // 实机：重连时距上次活动 13.5 s，写回 15 s 只剩 1.5 s，引擎按 TooLate 放弃后立即熄屏。
    assertTrue(TimeoutRestorePolicy.wouldExpireImmediately(snapshot(100_000), nowUptime = 113_500, original = 15_000))
    assertTrue(TimeoutRestorePolicy.wouldExpireImmediately(snapshot(100_000), nowUptime = 105_500, original = 15_000))
  }

  @Test fun restoresWhenRecentActivityLeavesTimeBeforeTheOriginalTimeout() {
    assertFalse(TimeoutRestorePolicy.wouldExpireImmediately(snapshot(100_000), nowUptime = 104_000, original = 15_000))
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
