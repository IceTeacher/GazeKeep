package cn.rainafter.app.gazekeep.backend.shizuku.oracle

import cn.rainafter.app.gazekeep.backend.shizuku.protocol.PowerSnapshot
import cn.rainafter.app.gazekeep.core.backend.MIN_CAPTURE_WINDOW_MS
import cn.rainafter.app.gazekeep.core.backend.TimeoutDeadline
import cn.rainafter.app.gazekeep.core.backend.captureWakeAtUptime
import cn.rainafter.app.gazekeep.core.model.BackendId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PowerDumpOracleTest {
  private fun TestScope.oracle(
    read: suspend () -> PowerSnapshot?,
    clock: Long,
    leadMs: Long,
    retryMs: Long = 1_000L,
    wakeLockRetryMs: Long = 5_000L,
  ) = PowerDumpOracle(
    readPower = read,
    scope = backgroundScope,
    clock = { clock },
    leadMs = leadMs,
    retryMs = retryMs,
    wakeLockRetryMs = wakeLockRetryMs,
    pollDispatcher = StandardTestDispatcher(testScheduler),
  )

  @Test
  fun pausesPollingWhileScreenOffUntilReadIsRequested() = runTest {
    var reads = 0
    val off = PowerSnapshot(1_000L, 60_000, null, hasScreenWakeLock = false, interactive = false)
    val oracle = oracle({ off.also { reads++ } }, clock = 1_000L, leadMs = 8_000L)

    oracle.start()
    advanceTimeBy(10_000L)
    runCurrent()
    // 息屏期间不再每秒拉起 dumpsys；亮屏广播通过 requestRead 恢复。
    assertEquals(1, reads)

    oracle.requestRead()
    runCurrent()
    assertEquals(2, reads)
    oracle.close()
  }

  @Test
  fun backsOffWhileScreenWakeLockIsHeld() = runTest {
    var reads = 0
    val held = PowerSnapshot(1_000L, 60_000, null, hasScreenWakeLock = true, interactive = true)
    val oracle = oracle({ held.also { reads++ } }, clock = 1_000L, leadMs = 8_000L)

    oracle.start()
    advanceTimeBy(10_000L)
    runCurrent()
    oracle.close()

    // 视频等应用自行保亮时按 5 s 退避：0、5、10 s 各读一次。
    assertEquals(3, reads)
  }

  @Test
  fun wakeLockReleasedInsideCheckWindowIsSeenBeforeCaptureBudgetRunsOut() = runTest {
    // 截止 10 s、降亮 7 s、提前量 2 s：检测点 5 s。别的应用在 6 s 释放亮屏锁，释放不会触发 requestRead。
    val releaseAt = 6_000L
    val oracle = PowerDumpOracle(
      readPower = {
        PowerSnapshot(0L, 10_000, 3_000, hasScreenWakeLock = testScheduler.currentTime < releaseAt, interactive = true)
      },
      scope = backgroundScope,
      clock = { testScheduler.currentTime },
      leadMs = 2_000L,
      pollDispatcher = StandardTestDispatcher(testScheduler),
    )
    var emittedAt: Long? = null
    backgroundScope.launch(StandardTestDispatcher(testScheduler)) {
      oracle.deadlines.first()
      emittedAt = testScheduler.currentTime
    }

    oracle.start()
    advanceTimeBy(12_000L)
    runCurrent()
    oracle.close()

    // 必须在熄屏前留出完整采集窗口内发出 deadline，否则 reducer 直接 TooLate。
    val at = assertNotNull(emittedAt)
    assertTrue(10_000L - at > MIN_CAPTURE_WINDOW_MS, "deadline emitted at $at")
  }

  @Test
  fun wakeLockHeldPastScreenOffDeadlineKeepsBackoff() = runTest {
    var reads = 0
    // 锁持有已超过原截止时间：释放后系统按自身规则处理，轮询频率无法挽回，保持退避。
    val held = PowerSnapshot(1_000L, 10_000, null, hasScreenWakeLock = true, interactive = true)
    val oracle = oracle({ held.also { reads++ } }, clock = 20_000L, leadMs = 8_000L)

    oracle.start()
    advanceTimeBy(10_000L)
    runCurrent()
    oracle.close()

    assertEquals(3, reads)
  }

  @Test
  fun publishesDimmingTimeAndPreservesRealScreenOffDeadline() = runTest {
    for (dimMs in listOf(18_000, null, -1, 60_001)) {
      val oracle = oracle({ PowerSnapshot(1_000L, 60_000, dimMs, false, true) }, clock = 1_000L, leadMs = 8_000L)
      oracle.start()
      runCurrent()
      val deadline = oracle.deadlines.first()
      assertEquals(61_000L, deadline.screenOffAtUptime)
      assertEquals(if (dimMs == 18_000) 43_000L else null, deadline.screenDimAtUptime)
      assertEquals(if (dimMs == 18_000) 35_000L else 53_000L, deadline.captureWakeAtUptime(8_000L))
      oracle.close()
    }
  }

  @Test
  fun doesNotEmitTheSameDeadlineDuringPollingWindow() = runTest {
    var reads = 0
    val snapshot = PowerSnapshot(
      lastUserActivityUptime = 1_000L,
      screenOffTimeoutMs = 10_000,
      dimDurationMs = 3_000,
      hasScreenWakeLock = false,
      interactive = true,
    )
    // 时钟已进入采集窗口，每次 retryMs 都会重新读取到同一个活动 token。
    val oracle = oracle({ snapshot.also { reads++ } }, clock = 9_000L, leadMs = 2_000L, retryMs = 50L)
    val events = mutableListOf<TimeoutDeadline>()
    backgroundScope.launch(StandardTestDispatcher(testScheduler)) { oracle.deadlines.collect { events += it } }

    oracle.start()
    advanceTimeBy(500L)
    runCurrent()
    oracle.close()

    assertEquals(11, reads)
    assertEquals(1, events.size)
    assertEquals(BackendId.Shizuku, events.single().backend)
  }
}
