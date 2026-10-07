package cn.rainafter.app.gazekeep.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import cn.rainafter.app.gazekeep.ui.contract.GazeKeepController
import cn.rainafter.app.gazekeep.ui.contract.ControllerState
import gazekeep.shared.generated.resources.Res
import gazekeep.shared.generated.resources.settings_app_count
import gazekeep.shared.generated.resources.settings_apps
import gazekeep.shared.generated.resources.settings_empty_scope
import gazekeep.shared.generated.resources.settings_scope
import gazekeep.shared.generated.resources.settings_whitelist
import gazekeep.shared.generated.resources.settings_whitelist_summary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

import cn.rainafter.app.gazekeep.ui.components.AppScaffold
import cn.rainafter.app.gazekeep.ui.components.SettingsGroupTitle
import cn.rainafter.app.gazekeep.ui.navigation.SettingsScreen

@Composable
internal fun ScopeScreen(
    state: ControllerState,
    scope: CoroutineScope,
    controller: GazeKeepController,
    onNavigate: (SettingsScreen) -> Unit,
    onBack: () -> Unit,
) {
    LaunchedEffect(Unit) {
        controller.loadApps()
    }

    AppScaffold(
        title = stringResource(Res.string.settings_scope),
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
            SettingsGroupTitle(text = stringResource(Res.string.settings_scope))
            Card(modifier = Modifier.fillMaxWidth()) {
                SwitchPreference(
                    title = stringResource(Res.string.settings_whitelist),
                    summary = stringResource(Res.string.settings_whitelist_summary),
                    checked = state.config.whitelistEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch { controller.updateConfig { it.copy(whitelistEnabled = enabled) } }
                    }
                )
                ArrowPreference(
                    title = stringResource(Res.string.settings_apps),
                    summary = stringResource(Res.string.settings_app_count, state.config.packages.size),
                    onClick = { onNavigate(SettingsScreen.Apps) }
                )
            }

            if (state.config.whitelistEnabled && state.config.packages.isEmpty()) {
                SmallTitle(
                    text = stringResource(Res.string.settings_empty_scope),
                    textColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    insideMargin = PaddingValues(start = 16.dp, top = 4.dp, bottom = 4.dp),
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
