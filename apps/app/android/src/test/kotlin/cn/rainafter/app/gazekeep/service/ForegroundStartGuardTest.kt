package cn.rainafter.app.gazekeep.service

import cn.rainafter.app.gazekeep.core.engine.Alert
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ForegroundStartGuardTest {
  @Test fun lostPermissionNeverPromotesOrRestartsAndStillCleansUpWhenReportingFails() {
    val events = mutableListOf<String>()
    val guard = ForegroundStartGuard(
      cameraGranted = { false },
      promote = { error("must not start camera FGS") },
      release = {
        events += "release"
        throw IllegalStateException("cleanup failed")
      },
      notify = { alert ->
        assertEquals(Alert.CameraPermissionLost, alert)
        events += "notify"
        error("notifications denied")
      },
      stop = { events += "stop" },
    )
    assertFalse(guard.ensureForeground())
    assertFalse(guard.ensureForeground())
    assertEquals(listOf("release", "notify", "stop"), events)
  }

  @Test fun platformRejectingForegroundPromotionStopsInsteadOfCrashing() {
    val events = mutableListOf<String>()
    val guard = ForegroundStartGuard(
      cameraGranted = { true },
      promote = { throw SecurityException("not eligible for camera FGS") },
      release = { events += "release" },
      notify = { alert ->
        assertEquals(Alert.BackendUnavailable, alert)
        events += "notify"
      },
      stop = { events += "stop" },
    )
    assertFalse(guard.ensureForeground())
    assertEquals(listOf("release", "notify", "stop"), events)
  }

  @Test fun validRepeatedStartCommandsEachPromoteAndFreshInstanceCanRecover() {
    var granted = false
    var promotions = 0
    fun guard() = ForegroundStartGuard({ granted }, { promotions++ }, {}, {}, {})
    val failed = guard()
    assertFalse(failed.ensureForeground())
    granted = true
    assertFalse(failed.ensureForeground())
    val recovered = guard()
    assertTrue(recovered.ensureForeground())
    assertTrue(recovered.ensureForeground())
    assertEquals(2, promotions)
  }
}
