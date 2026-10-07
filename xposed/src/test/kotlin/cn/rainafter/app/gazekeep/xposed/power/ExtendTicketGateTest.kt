package cn.rainafter.app.gazekeep.xposed.power

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class ExtendTicketGateTest {
    @Test
    fun concurrentRequestsHaveOneWinner() {
        val gate = ExtendTicketGate()
        gate.offer(1L, 100L)
        val start = CountDownLatch(1)
        val winners = AtomicInteger(0)
        val threads = List(8) {
            Thread {
                start.await()
                if (gate.consume(1L, 90L)) winners.incrementAndGet()
            }.apply { start() }
        }
        start.countDown()
        threads.forEach { it.join() }
        assertEquals(1, winners.get())
    }

    @Test
    fun acceptsCurrentTicketOnlyOnce() {
        val gate = ExtendTicketGate()
        gate.offer(1L, 100L)
        assertFalse(gate.consume(0L, 90L))
        assertTrue(gate.consume(1L, 90L))
        assertFalse(gate.consume(1L, 91L))
    }

    @Test
    fun rejectsAtAndAfterScreenOffDeadline() {
        val gate = ExtendTicketGate()
        gate.offer(1L, 100L)
        assertFalse(gate.consume(1L, 100L))
        assertFalse(gate.consume(1L, 101L))
    }

    @Test
    fun replacementAndInvalidationRejectOldTicket() {
        val gate = ExtendTicketGate()
        gate.offer(1L, 100L)
        gate.offer(2L, 200L)
        assertFalse(gate.consume(1L, 90L))
        gate.invalidate()
        assertFalse(gate.consume(2L, 190L))
    }
}
