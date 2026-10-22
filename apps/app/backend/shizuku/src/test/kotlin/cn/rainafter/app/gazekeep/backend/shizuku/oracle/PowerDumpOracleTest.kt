package cn.rainafter.app.gazekeep.backend.shizuku.oracle

import cn.rainafter.app.gazekeep.backend.shizuku.protocol.PowerSnapshot
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

class PowerDumpOracleTest {
  private fun TestScope.oracle(read: suspend () -> PowerSnapshot?, clock: Long, leadMs: Long, retryMs: Long = 1_000L) =
    PowerDumpOracle(
      readPower = read,
      scope = backgroundScope,
      clock = { clock },
      leadMs = leadMs,
      retryMs = retryMs,
      pollDispatcher = StandardTestDispatcher(testScheduler),
    )

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
