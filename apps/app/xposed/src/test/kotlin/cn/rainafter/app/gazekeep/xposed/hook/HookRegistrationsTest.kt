package cn.rainafter.app.gazekeep.xposed.hook

import java.lang.reflect.Method
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HookRegistrationsTest {
  @Test
  fun optionalFailureDisablesThenUnhooksOnlyItsOwnGroup() {
    val facade = RegistrationFixture()
    facade.hookBefore(facade.method) { }
    assertFalse(
      installOptionalHooks(facade, { facade.events += "disable" }) {
        it.hookBefore(facade.method) { }
        it.hookAfter(facade.method) { _, _ -> }
        throw AssertionError("indicator install failed")
      },
    )
    assertEquals(setOf(1), facade.active)
    assertEquals(listOf("disable", "unhook3", "unhook2"), facade.events)
  }

  @Test
  fun optionalCleanupFailureStillAttemptsEveryUnhookAndKeepsMainHook() {
    val facade = RegistrationFixture()
    facade.hookBefore(facade.method) { }
    facade.failedUnhook = 3
    assertFalse(
      installOptionalHooks(facade, { throw AssertionError("disable failure") }) {
        it.hookBefore(facade.method) { }
        it.hookAfter(facade.method) { _, _ -> }
        throw AssertionError("indicator install failed")
      },
    )
    assertEquals(listOf("unhook3", "unhook2"), facade.events)
    assertTrue(1 in facade.active)
    assertFalse(2 in facade.active)
  }

  @Test
  fun successfulOptionalInstallationKeepsBothGroupsActive() {
    val facade = RegistrationFixture()
    facade.hookBefore(facade.method) { }
    assertTrue(installOptionalHooks(facade, { error("must not disable") }) { it.hookAfter(facade.method) { _, _ -> } })
    assertEquals(setOf(1, 2), facade.active)
    assertEquals(emptyList(), facade.events)
  }
}

private class RegistrationFixture : HookFacade {
  val method = HashMap::class.java.getMethod("get", Any::class.java)
  val active = mutableSetOf<Int>()
  val events = mutableListOf<String>()
  var failedUnhook = -1
  private var sequence = 0

  private fun register(): HookRegistration {
    val id = ++sequence
    active += id
    return HookRegistration {
      events += "unhook$id"
      if (failedUnhook == id) throw AssertionError("unhook failure")
      active -= id
    }
  }

  override fun hookBefore(method: Method, callback: (HookBeforeParam) -> Unit): HookRegistration = register()
  override fun hookAfter(method: Method, callback: (Array<Any?>, Any?) -> Unit): HookRegistration = register()
}
