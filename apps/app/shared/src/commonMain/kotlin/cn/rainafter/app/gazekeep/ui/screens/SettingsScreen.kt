package cn.rainafter.app.gazekeep.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
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
import gazekeep.shared.generated.resources.settings_custom
import gazekeep.shared.generated.resources.settings_debug_log
import gazekeep.shared.generated.resources.settings_debug_log_summary
import gazekeep.shared.generated.resources.settings_dialog_cancel
import gazekeep.shared.generated.resources.settings_dialog_confirm
import gazekeep.shared.generated.resources.settings_hold_10
import gazekeep.shared.generated.resources.settings_hold_30
import gazekeep.shared.generated.resources.settings_hold_60
import gazekeep.shared.generated.resources.settings_hold_custom_hint
import gazekeep.shared.generated.resources.settings_hold_custom_value
import gazekeep.shared.generated.resources.settings_hold_unlimited
import gazekeep.shared.generated.resources.settings_lead
import gazekeep.shared.generated.resources.settings_lead_5
import gazekeep.shared.generated.resources.settings_lead_8
import gazekeep.shared.generated.resources.settings_lead_custom_hint
import gazekeep.shared.generated.resources.settings_lead_custom_value
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
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
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
import top.yukonga.miuix.kmp.overlay.OverlayDialog
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
private fun CustomIntDialog(
  show: Boolean,
  title: String,
  hint: String,
  value: String,
  isValid: Boolean,
  onValueChange: (String) -> Unit,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
) {
  OverlayDialog(
    show = show,
    title = title,
    summary = hint,
    onDismissRequest = onDismiss,
  ) {
    TextField(
      value = value,
      onValueChange = { input -> onValueChange(input.filter(Char::isDigit).take(4)) },
      label = hint,
      useLabelAsPlaceholder = true,
      singleLine = true,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(16.dp))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Button(
        onClick = onDismiss,
        modifier = Modifier.weight(1f),
      ) {
        Text(stringResource(Res.string.settings_dialog_cancel))
      }
      Button(
        onClick = onConfirm,
        enabled = isValid,
        colors = ButtonDefaults.buttonColorsPrimary(),
        modifier = Modifier.weight(1f),
      ) {
        Text(stringResource(Res.string.settings_dialog_confirm))
      }
    }
  }
}

@Composable
internal fun SettingsTabScreen(
  state: ControllerState,
  scope: CoroutineScope,
  controller: GazeKeepController,
  onNavigate: (SettingsScreen) -> Unit,
  bottomBar: @Composable () -> Unit = {},
) {
  var showLeadCustomDialog by rememberSaveable { mutableStateOf(false) }
  var leadCustomInput by rememberSaveable { mutableStateOf("") }
  var showHoldCustomDialog by rememberSaveable { mutableStateOf(false) }
  var holdCustomInput by rememberSaveable { mutableStateOf("") }

  val leadSeconds = (state.config.leadMillis / 1_000L).toInt()
  val leadSelectedIndex = when (state.config.leadMillis) {
    5_000L -> 0
    8_000L -> 1
    else -> 2
  }
  val leadCustomLabel = if (leadSelectedIndex == 2) {
    stringResource(Res.string.settings_lead_custom_value, leadSeconds)
  } else {
    stringResource(Res.string.settings_custom)
  }

  val holdMinutes = state.config.maxHoldMinutes
  val holdSelectedIndex = when (holdMinutes) {
    10 -> 0
    30 -> 1
    60 -> 2
    0 -> 4
    else -> 3
  }
  val holdCustomLabel = if (holdSelectedIndex == 3) {
    stringResource(Res.string.settings_hold_custom_value, holdMinutes)
  } else {
    stringResource(Res.string.settings_custom)
  }

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
            leadCustomLabel,
          ),
          selectedIndex = leadSelectedIndex,
          onSelectedIndexChange = { index ->
            when (index) {
              0 -> scope.launch { controller.updateConfig { it.copy(leadMillis = 5_000L) } }

              1 -> scope.launch { controller.updateConfig { it.copy(leadMillis = 8_000L) } }

              else -> {
                leadCustomInput = leadSeconds.toString()
                showLeadCustomDialog = true
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
            holdCustomLabel,
            stringResource(Res.string.settings_hold_unlimited),
          ),
          selectedIndex = holdSelectedIndex,
          onSelectedIndexChange = { index ->
            when (index) {
              0 -> scope.launch { controller.updateConfig { it.copy(maxHoldMinutes = 10) } }

              1 -> scope.launch { controller.updateConfig { it.copy(maxHoldMinutes = 30) } }

              2 -> scope.launch { controller.updateConfig { it.copy(maxHoldMinutes = 60) } }

              3 -> {
                holdCustomInput = holdMinutes.takeIf { it > 0 }?.toString().orEmpty()
                showHoldCustomDialog = true
              }

              else -> scope.launch { controller.updateConfig { it.copy(maxHoldMinutes = 0) } }
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

    val parsedLeadSeconds = leadCustomInput.toIntOrNull()?.takeIf { it in 0..60 }
    CustomIntDialog(
      show = showLeadCustomDialog,
      title = stringResource(Res.string.settings_lead),
      hint = stringResource(Res.string.settings_lead_custom_hint),
      value = leadCustomInput,
      isValid = parsedLeadSeconds != null,
      onValueChange = { leadCustomInput = it },
      onDismiss = { showLeadCustomDialog = false },
      onConfirm = {
        val seconds = parsedLeadSeconds ?: return@CustomIntDialog
        showLeadCustomDialog = false
        scope.launch { controller.updateConfig { it.copy(leadMillis = seconds * 1_000L) } }
      },
    )

    val parsedHoldMinutes = holdCustomInput.toIntOrNull()?.takeIf { it in 1..1_440 }
    CustomIntDialog(
      show = showHoldCustomDialog,
      title = stringResource(Res.string.settings_max_hold),
      hint = stringResource(Res.string.settings_hold_custom_hint),
      value = holdCustomInput,
      isValid = parsedHoldMinutes != null,
      onValueChange = { holdCustomInput = it },
      onDismiss = { showHoldCustomDialog = false },
      onConfirm = {
        val minutes = parsedHoldMinutes ?: return@CustomIntDialog
        showHoldCustomDialog = false
        scope.launch { controller.updateConfig { it.copy(maxHoldMinutes = minutes) } }
      },
    )
  }
}
