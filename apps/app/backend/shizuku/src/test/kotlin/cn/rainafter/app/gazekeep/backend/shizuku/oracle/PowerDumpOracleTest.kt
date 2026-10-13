package cn.rainafter.app.gazekeep.backend.shizuku.oracle

import cn.rainafter.app.gazekeep.backend.shizuku.protocol.PowerSnapshot
import cn.rainafter.app.gazekeep.core.backend.TimeoutDeadline
import cn.rainafter.app.gazekeep.core.backend.captureWakeAtUptime
import cn.rainafter.app.gazekeep.core.model.BackendId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals

class PowerDumpOracleTest {
  @Test
  fun publishesDimmingTimeAndPreservesRealScreenOffDeadline() = runBlocking {
    for (dimMs in listOf(18_000, null, -1, 60_001)) {
      val scope = CoroutineScope(Dispatchers.Default)
      val oracle = PowerDumpOracle(
        readPower = { PowerSnapshot(1_000L, 60_000, dimMs, false, true) },
        scope = scope,
        clock = { 1_000L },
        leadMs = 8_000L,
      )
      val first = async(start = CoroutineStart.UNDISPATCHED) { oracle.deadlines.first() }
      try {
        oracle.start()
        val deadline = withTimeout(2_000L) { first.await() }
        assertEquals(61_000L, deadline.screenOffAtUptime)
        assertEquals(if (dimMs == 18_000) 43_000L else null, deadline.screenDimAtUptime)
        assertEquals(if (dimMs == 18_000) 35_000L else 53_000L, deadline.captureWakeAtUptime(8_000L))
      } finally {
        oracle.close()
        first.cancel()
        scope.cancel()
      }
    }
  }

  @Test
  fun doesNotEmitTheSameDeadlineDuringPollingWindow() = runBlocking {
    val scope = CoroutineScope(Dispatchers.Default)
    val snapshot = PowerSnapshot(
      lastUserActivityUptime = 1_000L,
      screenOffTimeoutMs = 10_000,
      dimDurationMs = 3_000,
      hasScreenWakeLock = false,
      interactive = true,
    )
    val events = mutableListOf<TimeoutDeadline>()
    val oracle = PowerDumpOracle(
      readPower = { snapshot },
      scope = scope,
      clock = { 9_000L },
      leadMs = 2_000L,
      retryMs = 50L,
    )
    val collector = launch {
      oracle.deadlines.collect { events += it }
    }

    oracle.start()
    delay(180L)
    oracle.close()
    collector.cancel()
    scope.cancel()

    assertEquals(1, events.size)
    assertEquals(BackendId.Shizuku, events.single().backend)
  }
}
