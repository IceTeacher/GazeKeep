package cn.rainafter.app.gazekeep.backend.a11y

import kotlin.test.Test
import kotlin.test.assertEquals

class A11ySignalsTest {
  @Test
  fun closingOldSubscriptionDoesNotRemoveTheNewSession() {
    val signals = A11ySignals()
    val events = mutableListOf<String>()
    val old = signals.subscribe({ events.add("oldActivity") }, { events.add("oldUnavailable") })
    val current = signals.subscribe({ events.add("activity") }, { events.add("unavailable") })
    old.close()
    signals.activity()
    signals.unavailable()
    current.close()
    signals.activity()
    assertEquals(listOf("activity", "unavailable"), events)
  }

  @Test
  fun activityIsDeliveredBeforeTheProducerContinues() {
    val signals = A11ySignals()
    val order = mutableListOf<String>()
    val subscription = signals.subscribe({ order.add("userActivity") }, {})
    try {
      signals.activity()
      order.add("nextDeadline")
      assertEquals(listOf("userActivity", "nextDeadline"), order)
    } finally {
      subscription.close()
    }
  }
}
