package cn.rainafter.app.gazekeep.controller

import android.content.ComponentName
import cn.rainafter.app.gazekeep.core.model.Readiness
import cn.rainafter.app.gazekeep.core.model.SetupStep
import cn.rainafter.app.gazekeep.runtime.RuntimeStatus
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class BackendReadinessProbeTest {
  private val expected = ComponentName(
    "cn.rainafter.app.gazekeep",
    "cn.rainafter.app.gazekeep.backend.a11y.GazeA11yService",
  )

  @Test
  fun matchesFullAndShortAccessibilityComponentNames() {
    assertTrue(
      BackendReadinessProbe.isAccessibilityServiceListed(
        "com.other/com.other.Service:${expected.flattenToString()}",
        expected,
      ),
    )
    assertTrue(
      BackendReadinessProbe.isAccessibilityServiceListed(
        expected.flattenToShortString(),
        expected,
      ),
    )
    assertFalse(
      BackendReadinessProbe.isAccessibilityServiceListed(
        "com.other/.OtherService",
        expected,
      ),
    )
    assertFalse(BackendReadinessProbe.isAccessibilityServiceListed(null, expected))
  }

  @Test
  fun shizukuReadinessReflectsRuntimeFailureInsteadOfFalseReady() {
    val failed = Readiness.Unavailable("bind user service timed out")
    assertEquals(
      failed,
      BackendReadinessProbe.resolveShizukuReadiness(
        binderAlive = true,
        permissionGranted = true,
        runtimeStatus = RuntimeStatus(running = true, shizukuReadiness = failed),
      ),
    )
    assertEquals(
      Readiness.Ready,
      BackendReadinessProbe.resolveShizukuReadiness(
        binderAlive = true,
        permissionGranted = true,
        runtimeStatus = RuntimeStatus(running = true, shizukuReadiness = Readiness.NeedsSetup(SetupStep.StartShizuku)),
      ),
    )
    assertEquals(
      Readiness.NeedsSetup(SetupStep.StartShizuku),
      BackendReadinessProbe.resolveShizukuReadiness(
        binderAlive = false,
        permissionGranted = true,
        runtimeStatus = RuntimeStatus(running = true, shizukuReadiness = failed),
      ),
    )
  }
}
