package cn.rainafter.app.gazekeep.backend.lsposed

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HiddenApiExemptionTest {
  @Test
  fun concurrentCallersApplyExemptionOnlyOnce() {
    val calls = AtomicInteger()
    val inside = AtomicInteger()
    val overlapped = AtomicInteger()
    val exemption = HiddenApiExemption {
      if (inside.incrementAndGet() > 1) overlapped.incrementAndGet()
      calls.incrementAndGet()
      Thread.sleep(20)
      inside.decrementAndGet()
      true
    }
    val threads = 8
    val start = CountDownLatch(1)
    val pool = Executors.newFixedThreadPool(threads)
    val results = (1..threads).map {
      pool.submit<Boolean> {
        start.await()
        exemption.ensure()
      }
    }
    start.countDown()
    val values = results.map { it.get(5, TimeUnit.SECONDS) }
    pool.shutdown()

    assertTrue(values.all { it })
    assertEquals(1, calls.get())
    assertEquals(0, overlapped.get())
  }

  @Test
  fun failedOrThrowingExemptionIsRetriedOnNextCall() {
    val outcomes = ArrayDeque(listOf<() -> Boolean>({ false }, { error("bypass unavailable") }, { true }))
    var calls = 0
    val exemption = HiddenApiExemption {
      calls++
      outcomes.removeFirst()()
    }

    assertFalse(exemption.ensure())
    assertFalse(exemption.ensure())
    assertTrue(exemption.ensure())
    assertTrue(exemption.ensure())
    assertEquals(3, calls)
  }
}
