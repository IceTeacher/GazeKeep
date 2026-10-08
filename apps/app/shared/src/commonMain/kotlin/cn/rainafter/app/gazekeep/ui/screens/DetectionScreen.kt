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
import cn.rainafter.app.gazekeep.core.model.Sensitivity
import cn.rainafter.app.gazekeep.ui.components.AppScaffold
import cn.rainafter.app.gazekeep.ui.components.SettingsGroupTitle
import cn.rainafter.app.gazekeep.ui.contract.ControllerState
import cn.rainafter.app.gazekeep.ui.contract.GazeKeepController
import gazekeep.shared.generated.resources.Res
import gazekeep.shared.generated.resources.settings_detection
import gazekeep.shared.generated.resources.settings_failure_policy
import gazekeep.shared.generated.resources.settings_failure_summary
import gazekeep.shared.generated.resources.settings_lead
import gazekeep.shared.generated.resources.settings_lead_5
import gazekeep.shared.generated.resources.settings_lead_8
import gazekeep.shared.generated.resources.settings_lead_summary
import gazekeep.shared.generated.resources.settings_loose
import gazekeep.shared.generated.resources.settings_sensitivity
import gazekeep.shared.generated.resources.settings_standard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference

@Composable
internal fun DetectionScreen(
    state: ControllerState,
    scope: CoroutineScope,
    controller: GazeKeepController,
    onBack: () -> Unit,
) {
    AppScaffold(
        title = stringResource(Res.string.settings_detection),
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
            SettingsGroupTitle(text = stringResource(Res.string.settings_detection))
            Card(modifier = Modifier.fillMaxWidth()) {
                OverlayDropdownPreference(
                    title = stringResource(Res.string.settings_lead),
                    summary = stringResource(Res.string.settings_lead_summary),
                    items = listOf(stringResource(Res.string.settings_lead_5), stringResource(Res.string.settings_lead_8)),
                    selectedIndex = if (state.config.leadMillis == 5_000L) 0 else 1,
                    onSelectedIndexChange = { index ->
                        scope.launch { controller.updateConfig { it.copy(leadMillis = if (index == 0) 5_000L else 8_000L) } }
                    },
                )
                OverlayDropdownPreference(
                    title = stringResource(Res.string.settings_sensitivity),
                    items = listOf(stringResource(Res.string.settings_standard), stringResource(Res.string.settings_loose)),
                    selectedIndex = if (state.config.sensitivity == Sensitivity.Standard) 0 else 1,
                    onSelectedIndexChange = { index ->
                        scope.launch { controller.updateConfig { it.copy(sensitivity = if (index == 0) Sensitivity.Standard else Sensitivity.Loose) } }
                    },
                )
            }

            SettingsGroupTitle(text = stringResource(Res.string.settings_failure_policy))
            Card(modifier = Modifier.fillMaxWidth()) {
                BasicComponent(
                    title = stringResource(Res.string.settings_failure_policy),
                    summary = stringResource(Res.string.settings_failure_summary),
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
