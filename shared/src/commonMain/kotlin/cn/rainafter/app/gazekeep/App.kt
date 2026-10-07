package cn.rainafter.app.gazekeep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.rainafter.app.gazekeep.core.model.BackendLock
import cn.rainafter.app.gazekeep.ui.M2ShizukuController
import cn.rainafter.app.gazekeep.ui.M2ShizukuStatus
import gazekeep.shared.generated.resources.Res
import gazekeep.shared.generated.resources.app_title
import gazekeep.shared.generated.resources.m2_accessibility_enabled
import gazekeep.shared.generated.resources.m2_accessibility_open_settings
import gazekeep.shared.generated.resources.m2_accessibility_unavailable
import gazekeep.shared.generated.resources.m2_backend_accessibility
import gazekeep.shared.generated.resources.m2_backend_auto
import gazekeep.shared.generated.resources.m2_backend_auto_status
import gazekeep.shared.generated.resources.m2_backend_description
import gazekeep.shared.generated.resources.m2_backend_shizuku
import gazekeep.shared.generated.resources.m2_backend_title
import gazekeep.shared.generated.resources.m3_lsposed_ready
import gazekeep.shared.generated.resources.m3_lsposed_title
import gazekeep.shared.generated.resources.m3_lsposed_unavailable
import gazekeep.shared.generated.resources.m2_shizuku_needs_permission
import gazekeep.shared.generated.resources.m2_shizuku_needs_start
import gazekeep.shared.generated.resources.m2_shizuku_ready
import gazekeep.shared.generated.resources.m2_shizuku_refresh
import gazekeep.shared.generated.resources.m2_shizuku_request_permission
import gazekeep.shared.generated.resources.m2_shizuku_unavailable
import gazekeep.shared.generated.resources.m2_shizuku_unknown
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun App(
    shizukuController: M2ShizukuController = remember { PreviewM2Controller() },
) {
    val backendLock by shizukuController.backendLock.collectAsState()
    val shizukuStatus by shizukuController.status.collectAsState()
    val accessibilityEnabled by shizukuController.accessibilityEnabled.collectAsState()
    val lsposedReady by shizukuController.lsposedReady.collectAsState()
    MaterialTheme {
        Scaffold(topBar = { TopAppBar(title = { Text(stringResource(Res.string.app_title)) }) }) { padding ->
            BackendSelectionScreen(
                backendLock = backendLock,
                shizukuStatus = shizukuStatus,
                accessibilityEnabled = accessibilityEnabled,
                lsposedReady = lsposedReady,
                onBackendLockChange = shizukuController::setBackendLock,
                onShizukuRefresh = shizukuController::refresh,
                onShizukuPermission = shizukuController::requestPermission,
                onOpenAccessibilitySettings = shizukuController::openAccessibilitySettings,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun BackendSelectionScreen(
    backendLock: BackendLock,
    shizukuStatus: M2ShizukuStatus,
    accessibilityEnabled: Boolean,
    lsposedReady: Boolean,
    onBackendLockChange: (BackendLock) -> Unit,
    onShizukuRefresh: () -> Unit,
    onShizukuPermission: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(Res.string.m2_backend_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(Res.string.m2_backend_description), style = MaterialTheme.typography.bodyMedium)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                backendLockButton(BackendLock.Auto, backendLock, onBackendLockChange, Res.string.m2_backend_auto)
                backendLockButton(BackendLock.Shizuku, backendLock, onBackendLockChange, Res.string.m2_backend_shizuku)
                backendLockButton(BackendLock.Accessibility, backendLock, onBackendLockChange, Res.string.m2_backend_accessibility)
                backendLockButton(BackendLock.Lsposed, backendLock, onBackendLockChange, Res.string.m3_lsposed_title)
                when (backendLock) {
                    BackendLock.Accessibility -> {
                        Text(
                            stringResource(
                                if (accessibilityEnabled) Res.string.m2_accessibility_enabled
                                else Res.string.m2_accessibility_unavailable,
                            ),
                        )
                        if (!accessibilityEnabled) {
                            Button(onClick = onOpenAccessibilitySettings, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(Res.string.m2_accessibility_open_settings))
                            }
                        }
                    }
                    BackendLock.Shizuku -> {
                        Text(shizukuStatusLabel(shizukuStatus))
                        Button(
                            onClick = if (shizukuStatus == M2ShizukuStatus.NeedsPermission) onShizukuPermission else onShizukuRefresh,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(if (shizukuStatus == M2ShizukuStatus.NeedsPermission) Res.string.m2_shizuku_request_permission else Res.string.m2_shizuku_refresh))
                        }
                    }
                    BackendLock.Auto -> Text(stringResource(Res.string.m2_backend_auto_status))
                    BackendLock.Lsposed -> Text(stringResource(if (lsposedReady) Res.string.m3_lsposed_ready else Res.string.m3_lsposed_unavailable))
                }
            }
        }
    }
}

@Composable
private fun backendLockButton(
    lock: BackendLock,
    selected: BackendLock,
    onSelected: (BackendLock) -> Unit,
    label: StringResource,
) {
    Button(onClick = { onSelected(lock) }, modifier = Modifier.fillMaxWidth()) {
        Text(if (lock == selected) "✓ ${stringResource(label)}" else stringResource(label))
    }
}

@Composable
private fun shizukuStatusLabel(status: M2ShizukuStatus): String = when (status) {
    M2ShizukuStatus.Unknown -> stringResource(Res.string.m2_shizuku_unknown)
    M2ShizukuStatus.NeedsStart -> stringResource(Res.string.m2_shizuku_needs_start)
    M2ShizukuStatus.NeedsPermission -> stringResource(Res.string.m2_shizuku_needs_permission)
    M2ShizukuStatus.Ready -> stringResource(Res.string.m2_shizuku_ready)
    M2ShizukuStatus.Unavailable -> stringResource(Res.string.m2_shizuku_unavailable)
}

private class PreviewM2Controller : M2ShizukuController {
    override val status = MutableStateFlow(M2ShizukuStatus.Unknown)
    override val backendLock = MutableStateFlow(BackendLock.Auto)
    override val accessibilityEnabled = MutableStateFlow(false)
    override val lsposedReady = MutableStateFlow(false)
    override fun refresh() = Unit
    override fun requestPermission() = Unit
    override fun setBackendLock(lock: BackendLock) {
        backendLock.value = lock
    }
    override fun openAccessibilitySettings() = Unit
    override fun close() = Unit
}
