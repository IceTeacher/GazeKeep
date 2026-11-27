package cn.rainafter.app.gazekeep.platform

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import cn.rainafter.app.gazekeep.core.policy.RomBackgroundPolicy
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class RomBackgroundPolicyActionTest {
  @Test
  fun xiaomiLaunchesPowerkeeperAndFallsBackToAppDetailsOnFailure() {
    val launched = mutableListOf<Intent>()
    var fallbackCalls = 0
    launchRomBackgroundPolicy(
      policy = RomBackgroundPolicy.Xiaomi,
      packageName = "cn.rainafter.app.gazekeep",
      appLabel = "GazeKeep",
      startIntent = { launched += it },
      fallbackAppDetails = { fallbackCalls++ },
    )
    assertEquals(1, launched.size)
    assertEquals(
      ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"),
      launched.single().component,
    )
    assertEquals("cn.rainafter.app.gazekeep", launched.single().getStringExtra("package_name"))
    assertEquals("GazeKeep", launched.single().getStringExtra("package_label"))
    assertEquals(0, fallbackCalls)

    launchRomBackgroundPolicy(
      policy = RomBackgroundPolicy.Xiaomi,
      packageName = "cn.rainafter.app.gazekeep",
      appLabel = "GazeKeep",
      startIntent = { throw ActivityNotFoundException("missing") },
      fallbackAppDetails = { fallbackCalls++ },
    )
    assertEquals(1, fallbackCalls)
  }

  @Test
  fun nonXiaomiFamiliesAndNullOpenAppDetailsDirectlyWithoutPrivateIntents() {
    val policies = listOf(
      RomBackgroundPolicy.Oplus,
      RomBackgroundPolicy.Vivo,
      RomBackgroundPolicy.Samsung,
      RomBackgroundPolicy.HuaweiHonor,
      null,
    )
    for (policy in policies) {
      val launched = mutableListOf<Intent>()
      var fallbackCalls = 0
      launchRomBackgroundPolicy(
        policy = policy,
        packageName = "cn.rainafter.app.gazekeep",
        appLabel = "GazeKeep",
        startIntent = { launched += it },
        fallbackAppDetails = { fallbackCalls++ },
      )
      assertTrue(launched.isEmpty(), "expected no private intent for $policy")
      assertEquals(1, fallbackCalls, "expected direct app details fallback for $policy")
    }
  }
}
