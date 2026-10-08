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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import cn.rainafter.app.gazekeep.core.model.Sensitivity
import cn.rainafter.app.gazekeep.ui.components.AppScaffold
import cn.rainafter.app.gazekeep.ui.components.NoticeCard
import cn.rainafter.app.gazekeep.ui.components.SettingsGroupTitle
import cn.rainafter.app.gazekeep.ui.contract.ControllerState
import cn.rainafter.app.gazekeep.ui.contract.GazeKeepController
import cn.rainafter.app.gazekeep.ui.contract.UiNotice
import cn.rainafter.app.gazekeep.ui.navigation.SettingsScreen
import cn.rainafter.app.gazekeep.ui.text.attentionLabel
import cn.rainafter.app.gazekeep.ui.text.backendLabel
import gazekeep.shared.generated.resources.Res
import gazekeep.shared.generated.resources.app_title
import gazekeep.shared.generated.resources.settings_app_count
import gazekeep.shared.generated.resources.settings_backend
import gazekeep.shared.generated.resources.settings_detection
import gazekeep.shared.generated.resources.settings_group_basic
import gazekeep.shared.generated.resources.settings_group_security
import gazekeep.shared.generated.resources.settings_group_tools
import gazekeep.shared.generated.resources.settings_last_decision
import gazekeep.shared.generated.resources.settings_lead_5
import gazekeep.shared.generated.resources.settings_lead_8
import gazekeep.shared.generated.resources.settings_loading
import gazekeep.shared.generated.resources.settings_loose
import gazekeep.shared.generated.resources.settings_max_hold
import gazekeep.shared.generated.resources.settings_need_camera
import gazekeep.shared.generated.resources.settings_off
import gazekeep.shared.generated.resources.settings_privacy
import gazekeep.shared.generated.resources.settings_privacy_commitments
import gazekeep.shared.generated.resources.settings_protection
import gazekeep.shared.generated.resources.settings_ready
import gazekeep.shared.generated.resources.settings_running
import gazekeep.shared.generated.resources.settings_scope
import gazekeep.shared.generated.resources.settings_self_test
import gazekeep.shared.generated.resources.settings_standard
import gazekeep.shared.generated.resources.settings_waiting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
internal fun HomeScreen(
  state: ControllerState,
  scope: CoroutineScope,
  controller: GazeKeepController,
  onNavigate: (SettingsScreen) -> Unit,
) {
  AppScaffold(
    title = stringResource(Res.string.app_title),
    onBack = null,
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
      if (!state.loaded) {
        Card(modifier = Modifier.fillMaxWidth()) {
          BasicComponent(title = stringResource(Res.string.settings_loading))
        }
      } else {
        state.notice?.let { notice ->
          NoticeCard(
            notice = notice,
            onClick = when (notice) {
              UiNotice.NeedsCamera, UiNotice.NeedsSelfTest -> {
                { onNavigate(SettingsScreen.SelfTest) }
              }

              UiNotice.EmptyScope -> {
                { onNavigate(SettingsScreen.Scope) }
              }

              UiNotice.SaveFailed -> null
            },
          )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
          SwitchPreference(
            title = stringResource(Res.string.app_title),
            checked = state.config.enabled,
            summary = stringResource(
              if (!state.config.enabled) {
                Res.string.settings_off
              } else if (state.serviceRunning) {
                Res.string.settings_running
              } else {
                Res.string.settings_waiting
              },
            ),
            onCheckedChange = { enabled ->
              scope.launch {
                controller.setEnabled(enabled)
                if (controller.state.value.notice in
                  listOf(UiNotice.NeedsCamera, UiNotice.NeedsSelfTest)
                ) {
                  onNavigate(SettingsScreen.SelfTest)
                }
              }
            },
          )
          state.lastDecision?.let { decision ->
            BasicComponent(
              title = stringResource(Res.string.settings_last_decision),
              summary = attentionLabel(decision),
            )
          }
        }

        SettingsGroupTitle(text = stringResource(Res.string.settings_group_basic))
        Card(modifier = Modifier.fillMaxWidth()) {
          ArrowPreference(
            title = stringResource(Res.string.settings_backend),
            summary = backendLabel(state.activeBackend, state.config.backendLock),
            onClick = { onNavigate(SettingsScreen.Backend) },
          )
          ArrowPreference(
            title = stringResource(Res.string.settings_detection),
            summary = stringResource(
              if (state.config.leadMillis ==
                5_000L
              ) {
                Res.string.settings_lead_5
              } else {
                Res.string.settings_lead_8
              },
            ) + " · " + stringResource(
              if (state.config.sensitivity ==
                Sensitivity.Standard
              ) {
                Res.string.settings_standard
              } else {
                Res.string.settings_loose
              },
            ),
            onClick = { onNavigate(SettingsScreen.Detection) },
          )
          ArrowPreference(
            title = stringResource(Res.string.settings_scope),
            summary = if (state.config.whitelistEnabled) {
              stringResource(Res.string.settings_app_count, state.config.packages.size)
            } else {
              stringResource(Res.string.settings_off)
            },
            onClick = { onNavigate(SettingsScreen.Scope) },
          )
        }

        SettingsGroupTitle(text = stringResource(Res.string.settings_group_security))
        Card(modifier = Modifier.fillMaxWidth()) {
          ArrowPreference(
            title = stringResource(Res.string.settings_protection),
            summary =
            stringResource(Res.string.settings_max_hold) + " " + state.config.maxHoldMinutes + " 分钟",
            onClick = { onNavigate(SettingsScreen.Protection) },
          )
          ArrowPreference(
            title = stringResource(Res.string.settings_privacy),
            summary = stringResource(Res.string.settings_privacy_commitments),
            onClick = { onNavigate(SettingsScreen.Privacy) },
          )
        }

        SettingsGroupTitle(text = stringResource(Res.string.settings_group_tools))
        Card(modifier = Modifier.fillMaxWidth()) {
          ArrowPreference(
            title = stringResource(Res.string.settings_self_test),
            summary = if (state.cameraGranted) {
              stringResource(
                Res.string.settings_ready,
              )
            } else {
              stringResource(Res.string.settings_need_camera)
            },
            onClick = { onNavigate(SettingsScreen.SelfTest) },
          )
        }

        Spacer(Modifier.height(16.dp))
      }
    }
  }
}
