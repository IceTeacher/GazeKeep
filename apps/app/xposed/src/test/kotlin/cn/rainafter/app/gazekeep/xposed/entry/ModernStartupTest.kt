package cn.rainafter.app.gazekeep.xposed.entry

import kotlin.test.Test
import kotlin.test.assertEquals

class ModernStartupTest {
  @Test
  fun rejectsUnsupportedApiCapabilityAndProcessWithoutResolvingContext() {
    for ((api, properties, system, expected) in listOf(
      StartupFixture(100, 1L, true, ModernStartupResult.UnsupportedApi),
      StartupFixture(101, 1L, true, ModernStartupResult.UnsupportedApi),
      StartupFixture(102, 2L, true, ModernStartupResult.MissingSystemCapability),
      StartupFixture(102, 1L, false, ModernStartupResult.WrongProcess),
    )) {
      val startup = ModernStartup()
      assertEquals(expected, startup.load(api, properties, system))
      assertEquals(ModernStartupResult.NotAuthorized, startup.start({ error("must not resolve") }, { true }))
    }
  }

  @Test
  fun installsOnlyOnceAndDuplicateLoadCannotResetAttempt() {
    val startup = ModernStartup()
    assertEquals(ModernStartupResult.Accepted, startup.load(102, 3L, true))
    var installs = 0
    assertEquals(
      ModernStartupResult.Installed,
      startup.start({ "context" }, {
        installs++
        true
      }),
    )
    assertEquals(ModernStartupResult.AlreadyAttempted, startup.load(102, 1L, true))
    assertEquals(ModernStartupResult.AlreadyAttempted, startup.start({ error("must not resolve") }, { true }))
    assertEquals(1, installs)
  }

  @Test
  fun contextMissingOrThrowingDoesNotInstallOrRetry() {
    for (resolve in listOf<() -> String?>({ null }, { throw AssertionError("context failure") })) {
      val startup = ModernStartup()
      startup.load(102, 1L, true)
      assertEquals(ModernStartupResult.ContextUnavailable, startup.start(resolve) { error("must not install") })
      assertEquals(ModernStartupResult.AlreadyAttempted, startup.start({ "context" }, { true }))
    }
  }

  @Test
  fun installRejectionAndExceptionRemainUnavailable() {
    for (install in listOf<(String) -> Boolean>({ false }, { throw AssertionError("install failure") })) {
      val startup = ModernStartup()
      startup.load(102, 1L, true)
      assertEquals(ModernStartupResult.InstallationFailed, startup.start({ "context" }, install))
      assertEquals(ModernStartupResult.AlreadyAttempted, startup.start({ "context" }, { true }))
    }
  }

  private data class StartupFixture(
    val api: Int,
    val properties: Long,
    val system: Boolean,
    val expected: ModernStartupResult,
  )
}
