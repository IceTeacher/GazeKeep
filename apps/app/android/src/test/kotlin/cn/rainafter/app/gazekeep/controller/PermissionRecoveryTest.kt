package cn.rainafter.app.gazekeep.controller

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PermissionRecoveryTest {
  @Test
  fun resumesWhenCameraBecomesGrantedForAnEnabledConfiguration() {
    assertTrue(shouldResumeAfterCameraGrant(wasGranted = false, granted = true, enabled = true, selfTestPassed = true))
  }

  @Test
  fun ignoresUnchangedOrRevokedPermissionAndIneligibleConfiguration() {
    assertFalse(shouldResumeAfterCameraGrant(wasGranted = true, granted = true, enabled = true, selfTestPassed = true))
    assertFalse(shouldResumeAfterCameraGrant(wasGranted = true, granted = false, enabled = true, selfTestPassed = true))
    assertFalse(
      shouldResumeAfterCameraGrant(wasGranted = false, granted = true, enabled = false, selfTestPassed = true),
    )
    assertFalse(
      shouldResumeAfterCameraGrant(wasGranted = false, granted = true, enabled = true, selfTestPassed = false),
    )
  }
}
