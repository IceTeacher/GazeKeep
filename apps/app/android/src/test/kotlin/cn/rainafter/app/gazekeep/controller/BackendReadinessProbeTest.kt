package cn.rainafter.app.gazekeep.controller

import android.content.ComponentName
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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
}
