package cn.rainafter.app.gazekeep.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import cn.rainafter.app.gazekeep.ui.contract.GazeKeepController
import cn.rainafter.app.gazekeep.ui.contract.ControllerState
import gazekeep.shared.generated.resources.Res
import gazekeep.shared.generated.resources.settings_camera_pause
import gazekeep.shared.generated.resources.settings_camera_pause_summary
import gazekeep.shared.generated.resources.settings_dark_pause
import gazekeep.shared.generated.resources.settings_dark_pause_summary
import gazekeep.shared.generated.resources.settings_group_security
import gazekeep.shared.generated.resources.settings_hold_10
import gazekeep.shared.generated.resources.settings_hold_30
import gazekeep.shared.generated.resources.settings_hold_60
import gazekeep.shared.generated.resources.settings_max_hold
import gazekeep.shared.generated.resources.settings_max_hold_summary
import gazekeep.shared.generated.resources.settings_protection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference

import cn.rainafter.app.gazekeep.ui.components.AppScaffold
import cn.rainafter.app.gazekeep.ui.components.SettingsGroupTitle

@Composable
internal fun ProtectionScreen(
    state: ControllerState,
    scope: CoroutineScope,
    controller: GazeKeepController,
    onBack: () -> Unit,
) {
    AppScaffold(
        title = stringResource(Res.string.settings_protection),
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
            SettingsGroupTitle(text = stringResource(Res.string.settings_max_hold))
            Card(modifier = Modifier.fillMaxWidth()) {
                OverlayDropdownPreference(
                    title = stringResource(Res.string.settings_max_hold),
                    summary = stringResource(Res.string.settings_max_hold_summary),
                    items = listOf(stringResource(Res.string.settings_hold_10), stringResource(Res.string.settings_hold_30), stringResource(Res.string.settings_hold_60)),
                    selectedIndex = listOf(10, 30, 60).indexOf(state.config.maxHoldMinutes).coerceAtLeast(0),
                    onSelectedIndexChange = { index ->
                        scope.launch { controller.updateConfig { it.copy(maxHoldMinutes = listOf(10, 30, 60)[index]) } }
                    }
                )
            }

            SettingsGroupTitle(text = stringResource(Res.string.settings_group_security))
            Card(modifier = Modifier.fillMaxWidth()) {
                BasicComponent(
                    title = stringResource(Res.string.settings_camera_pause),
                    summary = stringResource(Res.string.settings_camera_pause_summary)
                )
                BasicComponent(
                    title = stringResource(Res.string.settings_dark_pause),
                    summary = stringResource(Res.string.settings_dark_pause_summary)
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
