package cn.rainafter.app.gazekeep.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import cn.rainafter.app.gazekeep.core.model.Sensitivity
import cn.rainafter.app.gazekeep.ui.components.AppScaffold
import cn.rainafter.app.gazekeep.ui.contract.ControllerState
import cn.rainafter.app.gazekeep.ui.contract.GazeKeepController
import cn.rainafter.app.gazekeep.ui.navigation.SettingsScreen
import cn.rainafter.app.gazekeep.ui.text.backendLabel
import gazekeep.shared.generated.resources.Res
import gazekeep.shared.generated.resources.settings_about
import gazekeep.shared.generated.resources.settings_about_summary
import gazekeep.shared.generated.resources.settings_app_count
import gazekeep.shared.generated.resources.settings_apps
import gazekeep.shared.generated.resources.settings_backend
import gazekeep.shared.generated.resources.settings_debug_log
import gazekeep.shared.generated.resources.settings_debug_log_summary
import gazekeep.shared.generated.resources.settings_hold_10
import gazekeep.shared.generated.resources.settings_hold_30
import gazekeep.shared.generated.resources.settings_hold_60
import gazekeep.shared.generated.resources.settings_lead
import gazekeep.shared.generated.resources.settings_lead_5
import gazekeep.shared.generated.resources.settings_lead_8
import gazekeep.shared.generated.resources.settings_lead_summary
import gazekeep.shared.generated.resources.settings_loose
import gazekeep.shared.generated.resources.settings_max_hold
import gazekeep.shared.generated.resources.settings_max_hold_summary
import gazekeep.shared.generated.resources.settings_need_camera
import gazekeep.shared.generated.resources.settings_need_self_test
import gazekeep.shared.generated.resources.settings_privacy
import gazekeep.shared.generated.resources.settings_privacy_commitments
import gazekeep.shared.generated.resources.settings_ready
import gazekeep.shared.generated.resources.settings_self_test
import gazekeep.shared.generated.resources.settings_sensitivity
import gazekeep.shared.generated.resources.settings_standard
import gazekeep.shared.generated.resources.settings_title
import gazekeep.shared.generated.resources.settings_whitelist
import gazekeep.shared.generated.resources.settings_whitelist_summary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.icon.extended.Stopwatch
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
private fun StartSettingIcon(imageVector: ImageVector) {
  Icon(
    imageVector = imageVector,
    contentDescription = null,
    tint = MiuixTheme.colorScheme.onSurface,
    modifier = Modifier
      .padding(end = 8.dp)
      .size(24.dp),
  )
}

@Composable
internal fun SettingsTabScreen(
  state: ControllerState,
  scope: CoroutineScope,
  controller: GazeKeepController,
  onNavigate: (SettingsScreen) -> Unit,
  bottomBar: @Composable () -> Unit = {},
) {
  AppScaffold(
    title = stringResource(Res.string.settings_title),
    onBack = null,
    bottomBar = bottomBar,
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
      Card(modifier = Modifier.fillMaxWidth()) {
        ArrowPreference(
          title = stringResource(Res.string.settings_backend),
          summary = backendLabel(state.activeBackend, state.config.backendLock),
          startAction = { StartSettingIcon(MiuixIcons.Layers) },
          onClick = { onNavigate(SettingsScreen.Backend) },
        )
        ArrowPreference(
          title = stringResource(Res.string.settings_self_test),
          summary = when {
            !state.cameraGranted -> stringResource(Res.string.settings_need_camera)
            !state.selfTestPassed -> stringResource(Res.string.settings_need_self_test)
            else -> stringResource(Res.string.settings_ready)
          },
          startAction = { StartSettingIcon(MiuixIcons.Scan) },
          onClick = { onNavigate(SettingsScreen.SelfTest) },
        )
      }

      Card(modifier = Modifier.fillMaxWidth()) {
        OverlayDropdownPreference(
          title = stringResource(Res.string.settings_lead),
          summary = stringResource(Res.string.settings_lead_summary),
          startAction = { StartSettingIcon(MiuixIcons.Timer) },
          items = listOf(
            stringResource(Res.string.settings_lead_5),
            stringResource(Res.string.settings_lead_8),
          ),
          selectedIndex = if (state.config.leadMillis == 5_000L) 0 else 1,
          onSelectedIndexChange = { index ->
            scope.launch {
              controller.updateConfig {
                it.copy(leadMillis = if (index == 0) 5_000L else 8_000L)
              }
            }
          },
        )
        OverlayDropdownPreference(
          title = stringResource(Res.string.settings_sensitivity),
          summary = stringResource(
            if (state.config.sensitivity == Sensitivity.Standard) {
              Res.string.settings_standard
            } else {
              Res.string.settings_loose
            },
          ),
          startAction = { StartSettingIcon(MiuixIcons.Tune) },
          items = listOf(
            stringResource(Res.string.settings_standard),
            stringResource(Res.string.settings_loose),
          ),
          selectedIndex = if (state.config.sensitivity == Sensitivity.Standard) 0 else 1,
          onSelectedIndexChange = { index ->
            scope.launch {
              controller.updateConfig {
                it.copy(
                  sensitivity = if (index == 0) Sensitivity.Standard else Sensitivity.Loose,
                )
              }
            }
          },
        )
        OverlayDropdownPreference(
          title = stringResource(Res.string.settings_max_hold),
          summary = stringResource(Res.string.settings_max_hold_summary),
          startAction = { StartSettingIcon(MiuixIcons.Stopwatch) },
          items = listOf(
            stringResource(Res.string.settings_hold_10),
            stringResource(Res.string.settings_hold_30),
            stringResource(Res.string.settings_hold_60),
          ),
          selectedIndex = listOf(10, 30, 60).indexOf(state.config.maxHoldMinutes).coerceAtLeast(0),
          onSelectedIndexChange = { index ->
            scope.launch {
              controller.updateConfig {
                it.copy(maxHoldMinutes = listOf(10, 30, 60)[index])
              }
            }
          },
        )
      }

      Card(modifier = Modifier.fillMaxWidth()) {
        SwitchPreference(
          title = stringResource(Res.string.settings_whitelist),
          summary = stringResource(Res.string.settings_whitelist_summary),
          startAction = { StartSettingIcon(MiuixIcons.Filter) },
          checked = state.config.whitelistEnabled,
          onCheckedChange = { enabled ->
            scope.launch { controller.updateConfig { it.copy(whitelistEnabled = enabled) } }
          },
        )
        ArrowPreference(
          title = stringResource(Res.string.settings_apps),
          summary = stringResource(Res.string.settings_app_count, state.config.packages.size),
          startAction = { StartSettingIcon(MiuixIcons.GridView) },
          onClick = { onNavigate(SettingsScreen.Apps) },
        )
      }

      Card(modifier = Modifier.fillMaxWidth()) {
        SwitchPreference(
          title = stringResource(Res.string.settings_debug_log),
          summary = stringResource(Res.string.settings_debug_log_summary),
          startAction = { StartSettingIcon(MiuixIcons.Notes) },
          checked = state.config.debugLogEnabled,
          onCheckedChange = { enabled ->
            scope.launch { controller.updateConfig { it.copy(debugLogEnabled = enabled) } }
          },
        )
        ArrowPreference(
          title = stringResource(Res.string.settings_privacy),
          summary = stringResource(Res.string.settings_privacy_commitments),
          startAction = { StartSettingIcon(MiuixIcons.Lock) },
          onClick = { onNavigate(SettingsScreen.Privacy) },
        )
        ArrowPreference(
          title = stringResource(Res.string.settings_about),
          summary = stringResource(Res.string.settings_about_summary),
          startAction = { StartSettingIcon(MiuixIcons.Info) },
          onClick = { onNavigate(SettingsScreen.About) },
        )
      }

      Spacer(Modifier.height(16.dp))
    }
  }
}
