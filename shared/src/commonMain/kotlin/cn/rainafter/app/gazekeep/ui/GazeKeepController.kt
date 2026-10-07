package cn.rainafter.app.gazekeep.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cn.rainafter.app.gazekeep.core.model.*
import kotlinx.coroutines.flow.StateFlow

enum class UiNotice { NeedsCamera, NeedsSelfTest, EmptyScope, SaveFailed }
enum class SelfTestStep { Looking, Away, Complete }
data class SelfTestState(val step: SelfTestStep = SelfTestStep.Looking, val running: Boolean = false, val attention: Attention? = null)
data class AppEntry(val packageName: String, val label: String)
data class ControllerState(
    val loaded: Boolean = false,
    val config: GazeConfig = GazeConfig(),
    val selfTestPassed: Boolean = false,
    val cameraGranted: Boolean = false,
    val backends: Map<BackendId, Readiness> = emptyMap(),
    val activeBackend: BackendId? = null,
    val serviceRunning: Boolean = false,
    val lastDecision: Attention? = null,
    val notice: UiNotice? = null,
    val apps: List<AppEntry> = emptyList(),
)
interface GazeKeepController {
    val state: StateFlow<ControllerState>
    val selfTest: StateFlow<SelfTestState>
    suspend fun updateConfig(transform: (GazeConfig) -> GazeConfig)
    suspend fun setEnabled(enabled: Boolean)
    suspend fun beginSelfTest()
    suspend fun captureSelfTest()
    fun cancelSelfTest()
    fun refresh()
    suspend fun loadApps()
}
interface PlatformActions {
    fun requestCameraPermission()
    fun requestNotificationPermission()
    fun openAccessibilitySettings()
    fun requestShizukuPermission()
    fun openShizukuApp()
    @Composable fun HandleBack(enabled: Boolean, onBack: () -> Unit)
    @Composable fun SelfTestPreview(modifier: Modifier)
    @Composable fun AppIcon(packageName: String, modifier: Modifier)
}
