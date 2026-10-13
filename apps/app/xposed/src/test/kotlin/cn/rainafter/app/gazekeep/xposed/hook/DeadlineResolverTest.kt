package cn.rainafter.app.gazekeep.xposed.hook

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeadlineResolverTest {
  @Test
  fun rejectsWrongNameParametersAndReturnType() {
    val longType = Long::class.javaPrimitiveType!!
    val method = SignatureFixture::class.java.getDeclaredMethod("updateUserActivity", longType, longType)
    assertTrue(matchesHookSignature(method, "updateUserActivity", arrayOf(longType, longType), longType))
    assertFalse(matchesHookSignature(method, "wrongName", arrayOf(longType, longType), longType))
    assertFalse(matchesHookSignature(method, "updateUserActivity", arrayOf(longType), longType))
    assertFalse(matchesHookSignature(method, "updateUserActivity", arrayOf(longType, longType), Void.TYPE))
  }

  @Test
  fun fallbackReportsActualSupportedCandidate() {
    val loader = object : ClassLoader(javaClass.classLoader) {
      override fun loadClass(name: String): Class<*> {
        if (name == "com.android.server.power.AttentionDetector") throw ClassNotFoundException(name)
        return super.loadClass(name)
      }
    }
    assertEquals(HookCandidate.C_INACTIVITY, DeadlineResolver(loader).resolve().candidate)
  }

  @Test
  fun attentionTakesPriorityOverInactivity() {
    val resolution = DeadlineResolver(javaClass.classLoader!!).resolve()
    assertEquals(HookCandidate.A_ATTENTION, resolution.candidate)
    assertEquals("updateUserActivity", resolution.method?.name)
  }

  @Test
  fun summaryAloneIsNotASupportedCandidate() {
    val resolver = DeadlineResolver { name ->
      if (name == "com.android.server.power.PowerManagerService") {
        SummaryOnlyFixture::class.java
      } else {
        throw ClassNotFoundException(name)
      }
    }
    assertEquals(null, resolver.resolve().candidate)
  }

  @Test
  fun wrongAttentionSignatureFallsBackToInactivity() {
    val resolver = DeadlineResolver { name ->
      when (name) {
        "com.android.server.power.AttentionDetector" -> WrongSignatureFixture::class.java
        "com.android.server.power.PowerManagerService" -> com.android.server.power.PowerManagerService::class.java
        else -> throw ClassNotFoundException(name)
      }
    }
    assertEquals(HookCandidate.C_INACTIVITY, resolver.resolve().candidate)
  }

  @Test
  fun wrongSignaturesLeaveHooksUnavailable() {
    val resolution = DeadlineResolver { WrongSignatureFixture::class.java }.resolve()
    assertEquals(null, resolution.candidate)
    assertEquals(null, resolution.method)
  }

  @Test
  fun recordsAllCandidatesWhenFrameworkClassesAreAbsent() {
    val loader = object : ClassLoader(javaClass.classLoader) {
      override fun loadClass(name: String): Class<*> {
        if (name.startsWith("com.android.server.power.")) throw ClassNotFoundException(name)
        return super.loadClass(name)
      }
    }
    val resolution = DeadlineResolver(loader).resolve()
    assertEquals(null, resolution.candidate)
    assertEquals(2, resolution.tried.size)
  }
}

private class SignatureFixture {
  fun updateUserActivity(nextDimming: Long, duration: Long): Long = nextDimming + duration
}

private class SummaryOnlyFixture {
  fun updateUserActivitySummaryLocked(now: Long, groupId: Int) = Unit
}

private class WrongSignatureFixture {
  fun updateUserActivity(nextDimming: Long, duration: Long): Int = 0
  fun scheduleUserInactivityTimeout(timeout: Int) = Unit
}
