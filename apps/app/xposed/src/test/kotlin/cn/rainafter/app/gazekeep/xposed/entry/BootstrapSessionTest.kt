package cn.rainafter.app.gazekeep.xposed.entry

import cn.rainafter.app.gazekeep.xposed.hook.HookRegistration
import cn.rainafter.app.gazekeep.xposed.hook.HookRegistrations
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class BootstrapSessionTest {
  @Test
  fun successfulInstallIsSharedWithoutCreatingAnotherCore() {
    val session = BootstrapSession<Any>()
    val core = Any()
    assertSame(core, session.start({ core }, { true }, { error("must not close") }, { error("must not rollback") }))
    assertSame(core, session.start({ error("must not create") }, { false }, {}, {}))
  }

  @Test
  fun failedInstallClosesCoreBeforeReverseRollbackEvenWhenCleanupThrows() {
    val events = mutableListOf<String>()
    val registrations = HookRegistrations()
    val session = BootstrapSession<Any>()
    assertNull(
      session.start(
        create = { Any() },
        install = {
          registrations.add(HookRegistration { events += "first" })
          registrations.add(
            HookRegistration {
              events += "second"
              throw AssertionError("unhook failure")
            },
          )
          throw AssertionError("partial install")
        },
        close = {
          events += "close"
          throw AssertionError("close failure")
        },
        rollback = registrations::rollback,
      ),
    )
    assertEquals(listOf("close", "second", "first"), events)
    assertNull(session.start({ error("must not retry") }, { true }, {}, {}))
    registrations.rollback()
    assertEquals(listOf("close", "second", "first"), events)
  }

  @Test
  fun falseInstallAlsoClosesAndRollsBack() {
    val events = mutableListOf<String>()
    assertNull(BootstrapSession<Any>().start({ Any() }, { false }, { events += "close" }, { events += "rollback" }))
    assertEquals(listOf("close", "rollback"), events)
  }

  @Test
  fun creationFailureStillRunsRollbackWithoutClosingAbsentCore() {
    var rollbacks = 0
    assertNull(
      BootstrapSession<Any>().start(
        { throw AssertionError("creation failure") },
        { error("must not install") },
        { error("must not close") },
        { rollbacks++ },
      ),
    )
    assertEquals(1, rollbacks)
  }
}
