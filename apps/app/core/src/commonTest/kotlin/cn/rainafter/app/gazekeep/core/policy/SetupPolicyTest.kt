package cn.rainafter.app.gazekeep.core.policy

import cn.rainafter.app.gazekeep.core.model.Attention
import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.core.model.BackendLock
import cn.rainafter.app.gazekeep.core.model.GazeConfig
import cn.rainafter.app.gazekeep.core.model.Readiness
import cn.rainafter.app.gazekeep.core.model.SetupStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SetupPolicyTest {
  @Test fun unknownNeverCompletesEitherStep() {
    assertEquals(SelfTestProgress(), advanceSelfTest(SelfTestProgress(), Attention.Unknown))
    val looking = SelfTestProgress(lookingPassed = true)
    assertEquals(looking, advanceSelfTest(looking, Attention.Unknown))
  }

  @Test fun awayBeforeLookingDoesNotPass() {
    assertFalse(advanceSelfTest(SelfTestProgress(), Attention.Away).lookingPassed)
  }

  @Test fun requiresLookingThenAway() {
    val looking = advanceSelfTest(SelfTestProgress(), Attention.Looking)
    assertFalse(looking.complete)
    assertTrue(advanceSelfTest(looking, Attention.Away).complete)
  }

  @Test fun cannotEnableWithoutCameraAndSelfTest() {
    assertEquals(EnableRequirement.Camera, enableRequirement(false, true))
    assertEquals(EnableRequirement.SelfTest, enableRequirement(true, false))
    assertEquals(EnableRequirement.Ready, enableRequirement(true, true))
  }

  @Test fun hideCameraIndicatorDefaultsToFalseAndRequiresLsposedReadyOrActive() {
    val defaultConfig = GazeConfig()
    assertFalse(defaultConfig.hideCameraIndicator)

    val readyBackends = mapOf(
      BackendId.Lsposed to Readiness.Ready,
      BackendId.Shizuku to Readiness.Ready,
      BackendId.Accessibility to Readiness.Ready,
    )
    val unreadyBackends = mapOf(
      BackendId.Lsposed to Readiness.NeedsSetup(SetupStep.InstallFramework),
      BackendId.Shizuku to Readiness.Ready,
    )

    assertTrue(canConfigureHideCameraIndicator(BackendId.Lsposed, BackendLock.Auto, readyBackends))
    assertTrue(canConfigureHideCameraIndicator(null, BackendLock.Auto, readyBackends))
    assertTrue(canConfigureHideCameraIndicator(null, BackendLock.Lsposed, readyBackends))
    assertFalse(canConfigureHideCameraIndicator(BackendId.Shizuku, BackendLock.Auto, readyBackends))
    assertFalse(canConfigureHideCameraIndicator(null, BackendLock.Shizuku, readyBackends))
    assertFalse(canConfigureHideCameraIndicator(null, BackendLock.Accessibility, readyBackends))
    assertFalse(canConfigureHideCameraIndicator(null, BackendLock.Auto, unreadyBackends))

    val enabledConfig = defaultConfig.copy(hideCameraIndicator = true, backendLock = BackendLock.Auto)
    assertTrue(shouldSyncHideCameraIndicator(enabledConfig, BackendId.Lsposed, readyBackends))
    assertTrue(shouldSyncHideCameraIndicator(enabledConfig, null, readyBackends))
    assertFalse(shouldSyncHideCameraIndicator(enabledConfig, BackendId.Shizuku, readyBackends))
    assertFalse(
      shouldSyncHideCameraIndicator(
        enabledConfig.copy(backendLock = BackendLock.Shizuku),
        null,
        readyBackends,
      ),
    )
  }
}
