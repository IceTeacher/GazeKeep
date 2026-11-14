package cn.rainafter.app.gazekeep.service

import cn.rainafter.app.gazekeep.core.model.BackendId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuickTileStateMapperTest {
  @Test
  fun missingCameraRedirectsToMainWithoutToggling() {
    val decision = QuickTileStateMapper.resolve(
      cameraGranted = false,
      selfTestPassed = true,
      enabled = false,
      serviceRunning = false,
      activeBackend = null,
    )
    assertEquals(QuickTileVisualState.Inactive, decision.visualState)
    assertEquals(QuickTileAction.OpenMainActivity, decision.action)
    assertEquals(QuickTileSubtitle.NeedsCamera, decision.subtitle)
  }

  @Test
  fun missingSelfTestRedirectsToMainWithoutToggling() {
    val decision = QuickTileStateMapper.resolve(
      cameraGranted = true,
      selfTestPassed = false,
      enabled = false,
      serviceRunning = false,
      activeBackend = null,
    )
    assertEquals(QuickTileVisualState.Inactive, decision.visualState)
    assertEquals(QuickTileAction.OpenMainActivity, decision.action)
    assertEquals(QuickTileSubtitle.NeedsSelfTest, decision.subtitle)
  }

  @Test
  fun readyAndDisabledAllowsDirectEnable() {
    val decision = QuickTileStateMapper.resolve(
      cameraGranted = true,
      selfTestPassed = true,
      enabled = false,
      serviceRunning = false,
      activeBackend = null,
    )
    assertEquals(QuickTileVisualState.Inactive, decision.visualState)
    assertEquals(QuickTileAction.Enable, decision.action)
    assertEquals(QuickTileSubtitle.Off, decision.subtitle)
  }

  @Test
  fun enabledWithActiveBackendShowsRunningAndDisablesOnTap() {
    val decision = QuickTileStateMapper.resolve(
      cameraGranted = true,
      selfTestPassed = true,
      enabled = true,
      serviceRunning = true,
      activeBackend = BackendId.Shizuku,
    )
    assertEquals(QuickTileVisualState.Active, decision.visualState)
    assertEquals(QuickTileAction.Disable, decision.action)
    assertEquals(QuickTileSubtitle.Running(BackendId.Shizuku), decision.subtitle)
  }

  @Test
  fun enabledWithoutActiveBackendShowsWaitingAndAllowsDisable() {
    val decision = QuickTileStateMapper.resolve(
      cameraGranted = true,
      selfTestPassed = true,
      enabled = true,
      serviceRunning = true,
      activeBackend = null,
    )
    assertEquals(QuickTileVisualState.Active, decision.visualState)
    assertEquals(QuickTileAction.Disable, decision.action)
    assertEquals(QuickTileSubtitle.WaitingBackend, decision.subtitle)
  }

  @Test
  fun android14AndAboveRequireForegroundActivityToEnableCameraService() {
    assertFalse(QuickTileStateMapper.requiresForegroundActivityToEnable(33))
    assertTrue(QuickTileStateMapper.requiresForegroundActivityToEnable(34))
    assertTrue(QuickTileStateMapper.requiresForegroundActivityToEnable(35))
  }
}
