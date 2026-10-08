package cn.rainafter.app.gazekeep.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.core.model.BackendLock
import cn.rainafter.app.gazekeep.core.model.Readiness
import cn.rainafter.app.gazekeep.core.model.SetupStep
import cn.rainafter.app.gazekeep.ui.components.AppScaffold
import cn.rainafter.app.gazekeep.ui.components.SettingsGroupTitle
import cn.rainafter.app.gazekeep.ui.contract.ControllerState
import cn.rainafter.app.gazekeep.ui.contract.GazeKeepController
import cn.rainafter.app.gazekeep.ui.contract.PlatformActions
import cn.rainafter.app.gazekeep.ui.text.backendLabel
import cn.rainafter.app.gazekeep.ui.text.backendName
import cn.rainafter.app.gazekeep.ui.text.readinessLabel
import gazekeep.shared.generated.resources.Res
import gazekeep.shared.generated.resources.m2_accessibility_open_settings
import gazekeep.shared.generated.resources.m2_backend_auto_status
import gazekeep.shared.generated.resources.m2_shizuku_refresh
import gazekeep.shared.generated.resources.m2_shizuku_request_permission
import gazekeep.shared.generated.resources.settings_auto_current
import gazekeep.shared.generated.resources.settings_backend
import gazekeep.shared.generated.resources.settings_backend_order
import gazekeep.shared.generated.resources.settings_open_shizuku
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun BackendScreen(
    state: ControllerState,
    scope: CoroutineScope,
    controller: GazeKeepController,
    actions: PlatformActions,
    onBack: () -> Unit,
) {
    AppScaffold(
        title = stringResource(Res.string.settings_backend),
        onBack = onBack,
    ) { padding, scrollBehavior ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsGroupTitle(text = stringResource(Res.string.settings_backend))
            Card(modifier = Modifier.fillMaxWidth()) {
                listOf(BackendLock.Auto, BackendLock.Lsposed, BackendLock.Shizuku, BackendLock.Accessibility).forEach { lock ->
                    val id = when (lock) {
                        BackendLock.Lsposed -> BackendId.Lsposed
                        BackendLock.Shizuku -> BackendId.Shizuku
                        BackendLock.Accessibility -> BackendId.Accessibility
                        else -> null
                    }
                    val summaryText = if (lock == BackendLock.Auto) {
                        state.activeBackend?.let { stringResource(Res.string.settings_auto_current, backendName(it)) }
                            ?: stringResource(Res.string.m2_backend_auto_status)
                    } else {
                        id?.let { readinessLabel(state.backends[it]) }
                    }

                    RadioButtonPreference(
                        title = backendLabel(null, lock),
                        selected = lock == state.config.backendLock,
                        summary = summaryText,
                        onClick = {
                            scope.launch {
                                controller.updateConfig { it.copy(backendLock = lock) }
                            }
                        },
                    )
                }
            }

            SmallTitle(
                text = stringResource(Res.string.settings_backend_order),
                textColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                insideMargin = PaddingValues(start = 16.dp, top = 4.dp, bottom = 4.dp),
            )

            val selected = when (state.config.backendLock) {
                BackendLock.Lsposed -> state.backends[BackendId.Lsposed]
                BackendLock.Shizuku -> state.backends[BackendId.Shizuku]
                BackendLock.Accessibility -> state.backends[BackendId.Accessibility]
                BackendLock.Auto -> null
            }
            if (selected is Readiness.NeedsSetup) {
                when (selected.step) {
                    SetupStep.EnableAccessibilityService -> {
                        Button(
                            onClick = actions::openAccessibilitySettings,
                            colors = ButtonDefaults.buttonColorsPrimary(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(Res.string.m2_accessibility_open_settings))
                        }
                    }

                    SetupStep.StartShizuku -> {
                        Button(
                            onClick = actions::openShizukuApp,
                            colors = ButtonDefaults.buttonColorsPrimary(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(Res.string.settings_open_shizuku))
                        }
                    }

                    SetupStep.GrantShizukuPermission -> {
                        Button(
                            onClick = actions::requestShizukuPermission,
                            colors = ButtonDefaults.buttonColorsPrimary(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(Res.string.m2_shizuku_request_permission))
                        }
                    }

                    else -> Unit
                }
            }

            Button(
                onClick = controller::refresh,
                colors = ButtonDefaults.buttonColors(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.m2_shizuku_refresh))
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
