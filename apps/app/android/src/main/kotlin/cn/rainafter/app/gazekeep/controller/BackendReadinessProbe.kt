package cn.rainafter.app.gazekeep.controller

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import cn.rainafter.app.gazekeep.backend.a11y.GazeA11yService
import cn.rainafter.app.gazekeep.backend.lsposed.LsposedKeepAwakeBackend
import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.core.model.Readiness
import cn.rainafter.app.gazekeep.core.model.SetupStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

data class BackendReadinessSnapshot(val cameraGranted: Boolean, val backends: Map<BackendId, Readiness>)

/** 设置页只探测能力，不注册单客户端的运行回调。 */
class BackendReadinessProbe(private val context: Context) {
    private val lsposed = LsposedKeepAwakeBackend(context)
    suspend fun read(): BackendReadinessSnapshot {
        val lsposedReadiness = withContext(Dispatchers.IO) { lsposed.checkReadiness() }
        val shizukuReadiness = runCatching {
            when {
                !Shizuku.pingBinder() -> Readiness.NeedsSetup(SetupStep.StartShizuku)
                Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> Readiness.NeedsSetup(SetupStep.GrantShizukuPermission)
                else -> Readiness.Ready
            }
        }.getOrDefault(Readiness.NeedsSetup(SetupStep.StartShizuku))
        val expected = ComponentName(context, GazeA11yService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty().split(':').any { it.equals(expected, true) }
        return BackendReadinessSnapshot(
            cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
            backends = mapOf(
                BackendId.Lsposed to lsposedReadiness,
                BackendId.Shizuku to shizukuReadiness,
                BackendId.Accessibility to if (enabled && GazeA11yService.connected.value) {
                    Readiness.Ready
                } else {
                    Readiness.NeedsSetup(SetupStep.EnableAccessibilityService)
                },
            ),
        )
    }
}
