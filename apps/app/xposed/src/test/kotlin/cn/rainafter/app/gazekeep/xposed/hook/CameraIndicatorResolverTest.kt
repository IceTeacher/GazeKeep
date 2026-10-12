package cn.rainafter.app.gazekeep.xposed.hook

import android.util.ArraySet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
class CameraIndicatorResolverTest {
  @Test
  fun resolvesAndroid12To15ActiveCallbackAndOperationActiveMethods() {
    val plan = CameraIndicatorResolver.resolveFromClass(AppOpsFixtureV34::class.java)
    val activeChanged = assertNotNull(plan.activeChanged)
    assertEquals(IndicatorCandidate.SCHEDULE_ACTIVE_V34, activeChanged.candidate)
    assertEquals(5, activeChanged.activeArgIndex)
    assertNotNull(plan.operationActiveMethod)
  }

  @Test
  fun fallsBackToNotifyOpActiveChangedWhenScheduleIsAbsent() {
    val plan = CameraIndicatorResolver.resolveFromClass(AppOpsFixtureV31NotifyOnly::class.java)
    val activeChanged = assertNotNull(plan.activeChanged)
    assertEquals(IndicatorCandidate.NOTIFY_ACTIVE_V31, activeChanged.candidate)
    assertEquals(5, activeChanged.activeArgIndex)
  }

  @Test
  fun suppressesOnlyGazeKeepCameraActiveTrueWhenEnabled() {
    val argsV34 = arrayOf<Any?>(
      CameraIndicatorResolver.OP_CAMERA,
      10234,
      "cn.rainafter.app.gazekeep",
      null,
      0,
      true,
      0,
      1,
    )
    assertTrue(
      shouldSuppressCameraActiveNotification(
        args = argsV34,
        activeArgIndex = 5,
        hideEnabled = true,
      ) { uid, pkg -> uid == 10234 && pkg == "cn.rainafter.app.gazekeep" },
    )

    // 关闭实验开关时不拦截
    assertFalse(
      shouldSuppressCameraActiveNotification(
        args = argsV34,
        activeArgIndex = 5,
        hideEnabled = false,
      ) { _, _ -> true },
    )

    // active = false（释放相机）必须放行，避免绿点残留
    val finishArgs = argsV34.copyOf().apply { this[5] = false }
    assertFalse(
      shouldSuppressCameraActiveNotification(
        args = finishArgs,
        activeArgIndex = 5,
        hideEnabled = true,
      ) { _, _ -> true },
    )

    // 非 OP_CAMERA（如麦克风 27）绝不拦截
    val micArgs = argsV34.copyOf().apply { this[0] = 27 }
    assertFalse(
      shouldSuppressCameraActiveNotification(
        args = micArgs,
        activeArgIndex = 5,
        hideEnabled = true,
      ) { _, _ -> true },
    )

    // 其他应用包名绝不拦截
    val otherAppArgs = argsV34.copyOf().apply {
      this[1] = 10999
      this[2] = "com.android.camera"
    }
    assertFalse(
      shouldSuppressCameraActiveNotification(
        args = otherAppArgs,
        activeArgIndex = 5,
        hideEnabled = true,
      ) { uid, pkg -> uid == 10234 && pkg == "cn.rainafter.app.gazekeep" },
    )
  }

  @Test
  fun overridesIsOperationActiveOnlyForGazeKeepCameraWhenEnabled() {
    val args = arrayOf<Any?>(CameraIndicatorResolver.OP_CAMERA, 10234, "cn.rainafter.app.gazekeep")
    assertTrue(
      shouldOverrideCameraOperationActive(args, hideEnabled = true) { uid, pkg ->
        uid == 10234 && pkg == "cn.rainafter.app.gazekeep"
      },
    )
    assertFalse(
      shouldOverrideCameraOperationActive(args, hideEnabled = false) { _, _ -> true },
    )
    val otherOp = arrayOf<Any?>(27, 10234, "cn.rainafter.app.gazekeep")
    assertFalse(
      shouldOverrideCameraOperationActive(otherOp, hideEnabled = true) { _, _ -> true },
    )
  }
}

@Suppress("unused", "UNUSED_PARAMETER")
private class AppOpsFixtureV34 {
  fun scheduleOpActiveChangedIfNeededLocked(
    code: Int,
    uid: Int,
    packageName: String,
    attributionTag: String?,
    virtualDeviceId: Int,
    active: Boolean,
    attributionFlags: Int,
    attributionChainId: Int,
  ) = Unit

  fun isOperationActive(code: Int, uid: Int, packageName: String): Boolean = true
}

@Suppress("unused", "UNUSED_PARAMETER")
private class AppOpsFixtureV31NotifyOnly {
  fun notifyOpActiveChanged(
    callbacks: ArraySet<Any>,
    code: Int,
    uid: Int,
    packageName: String,
    attributionTag: String?,
    active: Boolean,
    attributionFlags: Int,
    attributionChainId: Int,
  ) = Unit
}
