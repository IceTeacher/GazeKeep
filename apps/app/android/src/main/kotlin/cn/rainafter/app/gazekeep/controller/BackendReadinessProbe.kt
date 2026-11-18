package cn.rainafter.app.gazekeep.controller

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import cn.rainafter.app.gazekeep.backend.a11y.GazeA11yService
import cn.rainafter.app.gazekeep.backend.lsposed.LsposedKeepAwakeBackend
import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.core.model.Readiness
import cn.rainafter.app.gazekeep.core.model.SetupStep
import cn.rainafter.app.gazekeep.runtime.RuntimeStatus
import cn.rainafter.app.gazekeep.runtime.RuntimeStatusStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

data class BackendReadinessSnapshot(
  val cameraGranted: Boolean,
  val backends: Map<BackendId, Readiness>,
  val hideCameraIndicatorReady: Boolean,
)

/** 设置页只探测能力，不注册单客户端的运行回调。 */
class BackendReadinessProbe(private val context: Context) {
  private val lsposed = LsposedKeepAwakeBackend()

  suspend fun syncHideCameraIndicator(enabled: Boolean) {
    withContext(Dispatchers.IO) {
      lsposed.syncHideCameraIndicator(enabled)
    }
  }

  suspend fun read(): BackendReadinessSnapshot {
    val lsposedReadiness = withContext(Dispatchers.IO) { lsposed.checkReadiness() }
    val runtimeStatus = RuntimeStatusStore.status.value
    val shizukuReadiness = runCatching {
      resolveShizukuReadiness(
        binderAlive = Shizuku.pingBinder(),
        permissionGranted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED,
        runtimeStatus = runtimeStatus,
      )
    }.getOrDefault(Readiness.NeedsSetup(SetupStep.StartShizuku))
    val expected = ComponentName(context, GazeA11yService::class.java)
    val enabledInSettings = isAccessibilityServiceListed(
      Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
      expected,
    )
    val enabledInManager = context.getSystemService(AccessibilityManager::class.java)
      ?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
      ?.any { info ->
        val serviceInfo = info.resolveInfo?.serviceInfo
        serviceInfo != null &&
          serviceInfo.packageName == expected.packageName &&
          serviceInfo.name == expected.className
      } == true
    val enabled = enabledInSettings || enabledInManager
    return BackendReadinessSnapshot(
      cameraGranted =
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED,
      backends = mapOf(
        BackendId.Lsposed to lsposedReadiness,
        BackendId.Shizuku to shizukuReadiness,
        BackendId.Accessibility to if (enabled && GazeA11yService.connected.value) {
          Readiness.Ready
        } else {
          Readiness.NeedsSetup(SetupStep.EnableAccessibilityService)
        },
      ),
      hideCameraIndicatorReady = withContext(Dispatchers.IO) {
        lsposed.checkIndicatorHookReadiness()
      },
    )
  }

  internal companion object {
    fun resolveShizukuReadiness(
      binderAlive: Boolean,
      permissionGranted: Boolean,
      runtimeStatus: RuntimeStatus,
    ): Readiness = when {
      !binderAlive -> Readiness.NeedsSetup(SetupStep.StartShizuku)

      !permissionGranted -> Readiness.NeedsSetup(SetupStep.GrantShizukuPermission)

      runtimeStatus.running && runtimeStatus.shizukuReadiness is Readiness.Unavailable ->
        runtimeStatus.shizukuReadiness

      else -> Readiness.Ready
    }

    fun isAccessibilityServiceListed(rawSetting: String?, expected: ComponentName): Boolean {
      if (rawSetting.isNullOrBlank()) return false
      return rawSetting.split(':').any { entry ->
        val trimmed = entry.trim()
        trimmed.equals(expected.flattenToString(), ignoreCase = true) ||
          ComponentName.unflattenFromString(trimmed) == expected
      }
    }
  }
}
