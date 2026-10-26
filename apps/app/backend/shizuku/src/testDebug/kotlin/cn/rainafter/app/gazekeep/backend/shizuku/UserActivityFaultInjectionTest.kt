package cn.rainafter.app.gazekeep.backend.shizuku

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UserActivityFaultInjectionTest {
  @Test
  fun enabledPropertyDeniesUserActivityWithSecurityException() {
    var property = ""
    assertFailsWith<SecurityException> {
      UserActivityFaultInjection.check { name ->
        property = name
        " 1\n"
      }
    }
    assertEquals(UserActivityFaultInjection.PROPERTY, property)
  }

  @Test
  fun unsetOtherValuesOrUnreadablePropertyLeaveUserActivityUntouched() {
    for (value in listOf("", "0", "true", "11")) UserActivityFaultInjection.check { value }
    UserActivityFaultInjection.check { error("getprop failed") }
  }
}
