package cn.rainafter.app.gazekeep.core.policy

import cn.rainafter.app.gazekeep.core.model.Attention
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
}
