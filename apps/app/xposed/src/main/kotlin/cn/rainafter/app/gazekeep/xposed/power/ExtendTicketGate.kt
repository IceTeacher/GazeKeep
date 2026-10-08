package cn.rainafter.app.gazekeep.xposed.power

import java.util.concurrent.atomic.AtomicReference

/** 不取 PMS 之外的锁；令牌消费与失效只能有一个胜者。 */
internal class ExtendTicketGate {
    private data class Ticket(val seq: Long, val deadline: Long)
    private val pending = AtomicReference<Ticket?>(null)

    fun offer(seq: Long, deadline: Long) {
        pending.set(Ticket(seq, deadline))
    }

    fun invalidate() {
        pending.set(null)
    }

    fun consume(seq: Long, now: Long): Boolean {
        val ticket = pending.get() ?: return false
        if (ticket.seq != seq || now < 0L || now >= ticket.deadline) return false
        return pending.compareAndSet(ticket, null)
    }
}
