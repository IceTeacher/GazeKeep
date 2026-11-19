package cn.rainafter.app.gazekeep.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cn.rainafter.app.gazekeep.core.model.AppFilterMode
import cn.rainafter.app.gazekeep.core.model.ConfigBackupCodec
import cn.rainafter.app.gazekeep.core.model.ConfigImportResult
import cn.rainafter.app.gazekeep.core.model.GazeConfig
import cn.rainafter.app.gazekeep.core.model.Sensitivity
import cn.rainafter.app.gazekeep.core.model.scopedPackages
import cn.rainafter.app.gazekeep.core.policy.canConfigureHideCameraIndicator
import cn.rainafter.app.gazekeep.ui.components.AppScaffold
import cn.rainafter.app.gazekeep.ui.components.ExperimentalSwitchPreference
import cn.rainafter.app.gazekeep.ui.contract.AppLanguage
import cn.rainafter.app.gazekeep.ui.contract.ConfigReadResult
import cn.rainafter.app.gazekeep.ui.contract.ConfigTransferResult
import cn.rainafter.app.gazekeep.ui.contract.ControllerState
import cn.rainafter.app.gazekeep.ui.contract.GazeKeepController
import cn.rainafter.app.gazekeep.ui.contract.PlatformActions
import cn.rainafter.app.gazekeep.ui.navigation.SettingsScreen
import cn.rainafter.app.gazekeep.ui.showcase.ShowcaseInput
import cn.rainafter.app.gazekeep.ui.showcase.ShowcaseKeys
import cn.rainafter.app.gazekeep.ui.showcase.ShowcaseScrollTarget
import cn.rainafter.app.gazekeep.ui.showcase.showcaseAnchor
import cn.rainafter.app.gazekeep.ui.text.backendLabel
import gazekeep.shared.generated.resources.Res
import gazekeep.shared.generated.resources.settings_about
import gazekeep.shared.generated.resources.settings_about_summary
import gazekeep.shared.generated.resources.settings_app_count
import gazekeep.shared.generated.resources.settings_app_scope
import gazekeep.shared.generated.resources.settings_app_scope_all
import gazekeep.shared.generated.resources.settings_app_scope_all_summary
import gazekeep.shared.generated.resources.settings_app_scope_blacklist
import gazekeep.shared.generated.resources.settings_app_scope_blacklist_summary
import gazekeep.shared.generated.resources.settings_app_scope_whitelist
import gazekeep.shared.generated.resources.settings_app_scope_whitelist_summary
import gazekeep.shared.generated.resources.settings_apps
import gazekeep.shared.generated.resources.settings_backend
import gazekeep.shared.generated.resources.settings_background_policy
import gazekeep.shared.generated.resources.settings_background_policy_summary
import gazekeep.shared.generated.resources.settings_blacklist_count
import gazekeep.shared.generated.resources.settings_blacklist_empty
import gazekeep.shared.generated.resources.settings_config_export_failed
import gazekeep.shared.generated.resources.settings_config_exported
import gazekeep.shared.generated.resources.settings_config_import_failed
import gazekeep.shared.generated.resources.settings_config_import_invalid
import gazekeep.shared.generated.resources.settings_config_import_too_large
import gazekeep.shared.generated.resources.settings_config_imported
import gazekeep.shared.generated.resources.settings_custom
import gazekeep.shared.generated.resources.settings_debug_log
import gazekeep.shared.generated.resources.settings_debug_log_summary
import gazekeep.shared.generated.resources.settings_dialog_cancel
import gazekeep.shared.generated.resources.settings_dialog_confirm
import gazekeep.shared.generated.resources.settings_export_config
import gazekeep.shared.generated.resources.settings_export_config_summary
import gazekeep.shared.generated.resources.settings_hide_camera_indicator
import gazekeep.shared.generated.resources.settings_hide_camera_indicator_disabled_summary
import gazekeep.shared.generated.resources.settings_hide_camera_indicator_summary
import gazekeep.shared.generated.resources.settings_hold_10
import gazekeep.shared.generated.resources.settings_hold_30
import gazekeep.shared.generated.resources.settings_hold_60
import gazekeep.shared.generated.resources.settings_hold_custom_hint
import gazekeep.shared.generated.resources.settings_hold_custom_value
import gazekeep.shared.generated.resources.settings_hold_unlimited
import gazekeep.shared.generated.resources.settings_import_config
import gazekeep.shared.generated.resources.settings_import_config_summary
import gazekeep.shared.generated.resources.settings_import_confirm_action
import gazekeep.shared.generated.resources.settings_import_confirm_message
import gazekeep.shared.generated.resources.settings_import_confirm_title
import gazekeep.shared.generated.resources.settings_language
import gazekeep.shared.generated.resources.settings_language_chinese
import gazekeep.shared.generated.resources.settings_language_english
import gazekeep.shared.generated.resources.settings_language_system
import gazekeep.shared.generated.resources.settings_lead
import gazekeep.shared.generated.resources.settings_lead_5
import gazekeep.shared.generated.resources.settings_lead_8
import gazekeep.shared.generated.resources.settings_lead_custom_hint
import gazekeep.shared.generated.resources.settings_lead_custom_value
import gazekeep.shared.generated.resources.settings_lead_summary
import gazekeep.shared.generated.resources.settings_loose
import gazekeep.shared.generated.resources.settings_low_battery_level
import gazekeep.shared.generated.resources.settings_low_battery_off
import gazekeep.shared.generated.resources.settings_low_battery_threshold
import gazekeep.shared.generated.resources.settings_low_battery_threshold_summary
import gazekeep.shared.generated.resources.settings_max_hold
import gazekeep.shared.generated.resources.settings_max_hold_summary
import gazekeep.shared.generated.resources.settings_need_camera
import gazekeep.shared.generated.resources.settings_need_self_test
import gazekeep.shared.generated.resources.settings_power_save_pause
import gazekeep.shared.generated.resources.settings_power_save_pause_summary
import gazekeep.shared.generated.resources.settings_ready
import gazekeep.shared.generated.resources.settings_self_test
import gazekeep.shared.generated.resources.settings_sensitivity
import gazekeep.shared.generated.resources.settings_standard
import gazekeep.shared.generated.resources.settings_title
import gazekeep.shared.generated.resources.settings_view_logs
import gazekeep.shared.generated.resources.settings_view_logs_summary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.icon.extended.Stopwatch
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.icon.extended.Translate
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
  inputKey: String,
  confirmKey: String,
) {
  ShowcaseInput(inputKey) { input -> onValueChange(input.filter(Char::isDigit).take(4)) }
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
        modifier = Modifier.weight(1f).showcaseAnchor(confirmKey),
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
  actions: PlatformActions,
  onNavigate: (SettingsScreen) -> Unit,
  bottomBar: @Composable () -> Unit = {},
) {
  var showLeadCustomDialog by rememberSaveable { mutableStateOf(false) }
  var leadCustomInput by rememberSaveable { mutableStateOf("") }
  var showHoldCustomDialog by rememberSaveable { mutableStateOf(false) }
  var holdCustomInput by rememberSaveable { mutableStateOf("") }
  var showImportConfirmDialog by rememberSaveable { mutableStateOf(false) }
  var backupBannerRes by remember { mutableStateOf<StringResource?>(null) }
  var backupBannerToken by remember { mutableIntStateOf(0) }

  LaunchedEffect(backupBannerToken) {
    if (backupBannerToken > 0) {
      delay(2_400L)
      backupBannerRes = null
    }
  }

  fun showBackupBanner(message: StringResource) {
    backupBannerRes = message
    backupBannerToken++
  }

  fun launchConfigImport() {
    actions.importConfig { result ->
      when (result) {
        ConfigReadResult.Cancelled -> Unit

        ConfigReadResult.TooLarge -> showBackupBanner(Res.string.settings_config_import_too_large)

        ConfigReadResult.Failed -> showBackupBanner(Res.string.settings_config_import_failed)

        is ConfigReadResult.Loaded -> {
          when (val decoded = ConfigBackupCodec.decode(result.content, state.config)) {
            is ConfigImportResult.InvalidFormat -> showBackupBanner(Res.string.settings_config_import_invalid)

            is ConfigImportResult.Success -> scope.launch {
              val saved = controller.importConfig(decoded.config)
              showBackupBanner(
                if (saved) Res.string.settings_config_imported else Res.string.settings_config_import_failed,
              )
            }
          }
        }
      }
    }
  }

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
  val canHideCameraIndicator = canConfigureHideCameraIndicator(
    activeBackend = state.activeBackend,
    backendLock = state.config.backendLock,
    backends = state.backends,
  ) && state.hideCameraIndicatorReady

  AppScaffold(
    title = stringResource(Res.string.settings_title),
    onBack = null,
    bottomBar = bottomBar,
  ) { padding, scrollBehavior ->
    val scroll = rememberScrollState()
    ShowcaseScrollTarget(ShowcaseKeys.SETTINGS, scroll, scrollBehavior)
    Box(modifier = Modifier.fillMaxSize()) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .nestedScroll(scrollBehavior.nestedScrollConnection)
          .verticalScroll(scroll)
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
          if (actions.hasRomBackgroundPolicy()) {
            // HyperOS 默认省电策略会在系统相机使用后冻结服务进程，且无公开接口读取当前策略，只能常驻引导。
            ArrowPreference(
              title = stringResource(Res.string.settings_background_policy),
              summary = stringResource(Res.string.settings_background_policy_summary),
              startAction = { StartSettingIcon(MiuixIcons.Info) },
              onClick = actions::openRomBackgroundPolicy,
            )
          }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
          OverlayDropdownPreference(
            modifier = Modifier.showcaseAnchor(ShowcaseKeys.LEAD),
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
            modifier = Modifier.showcaseAnchor(ShowcaseKeys.SENSITIVITY),
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
            modifier = Modifier.showcaseAnchor(ShowcaseKeys.MAX_HOLD),
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
          ExperimentalSwitchPreference(
            modifier = Modifier.showcaseAnchor(ShowcaseKeys.HIDE_INDICATOR),
            title = stringResource(Res.string.settings_hide_camera_indicator),
            summary = stringResource(
              if (canHideCameraIndicator) {
                Res.string.settings_hide_camera_indicator_summary
              } else {
                Res.string.settings_hide_camera_indicator_disabled_summary
              },
            ),
            startAction = { StartSettingIcon(MiuixIcons.Lock) },
            enabled = canHideCameraIndicator,
            checked = state.config.hideCameraIndicator && canHideCameraIndicator,
            onCheckedChange = { enabled ->
              scope.launch { controller.updateConfig { it.copy(hideCameraIndicator = enabled) } }
            },
          )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
          val filterModes = AppFilterMode.entries
          val filterMode = state.config.appFilterMode
          OverlayDropdownPreference(
            title = stringResource(Res.string.settings_app_scope),
            summary = stringResource(
              when (filterMode) {
                AppFilterMode.All -> Res.string.settings_app_scope_all_summary
                AppFilterMode.Whitelist -> Res.string.settings_app_scope_whitelist_summary
                AppFilterMode.Blacklist -> Res.string.settings_app_scope_blacklist_summary
              },
            ),
            startAction = { StartSettingIcon(MiuixIcons.Filter) },
            items = filterModes.map {
              stringResource(
                when (it) {
                  AppFilterMode.All -> Res.string.settings_app_scope_all
                  AppFilterMode.Whitelist -> Res.string.settings_app_scope_whitelist
                  AppFilterMode.Blacklist -> Res.string.settings_app_scope_blacklist
                },
              )
            },
            selectedIndex = filterModes.indexOf(filterMode),
            onSelectedIndexChange = { index ->
              val selected = filterModes[index]
              if (selected != filterMode) {
                scope.launch { controller.updateConfig { it.copy(appFilterMode = selected) } }
              }
            },
          )
          if (filterMode != AppFilterMode.All) {
            val count = state.config.scopedPackages.size
            ArrowPreference(
              title = stringResource(Res.string.settings_apps),
              summary = when {
                filterMode == AppFilterMode.Whitelist -> stringResource(Res.string.settings_app_count, count)
                count == 0 -> stringResource(Res.string.settings_blacklist_empty)
                else -> stringResource(Res.string.settings_blacklist_count, count)
              },
              startAction = { StartSettingIcon(MiuixIcons.GridView) },
              onClick = { onNavigate(SettingsScreen.Apps) },
            )
          }
        }

        val lowBatteryLevels = GazeConfig.LOW_BATTERY_LEVELS.sorted()
        val lowBatteryIndex = lowBatteryLevels.indexOf(state.config.lowBatteryThreshold).let {
          if (it >= 0) it else 0
        }
        val lowBatteryLabels = lowBatteryLevels.map { level ->
          if (level == 0) {
            stringResource(Res.string.settings_low_battery_off)
          } else {
            stringResource(Res.string.settings_low_battery_level, level)
          }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
          SwitchPreference(
            title = stringResource(Res.string.settings_power_save_pause),
            summary = stringResource(Res.string.settings_power_save_pause_summary),
            startAction = { StartSettingIcon(MiuixIcons.Layers) },
            checked = state.config.pauseOnPowerSave,
            onCheckedChange = { enabled ->
              scope.launch { controller.updateConfig { it.copy(pauseOnPowerSave = enabled) } }
            },
          )
          OverlayDropdownPreference(
            title = stringResource(Res.string.settings_low_battery_threshold),
            summary = stringResource(Res.string.settings_low_battery_threshold_summary),
            startAction = { StartSettingIcon(MiuixIcons.Timer) },
            items = lowBatteryLabels,
            selectedIndex = lowBatteryIndex,
            onSelectedIndexChange = { index ->
              val selectedLevel = lowBatteryLevels[index]
              scope.launch { controller.updateConfig { it.copy(lowBatteryThreshold = selectedLevel) } }
            },
          )
        }

        val appLanguage = actions.appLanguage()
        if (appLanguage != null) {
          Card(modifier = Modifier.fillMaxWidth()) {
            val languages = AppLanguage.entries
            val languageLabels = languages.map {
              stringResource(
                when (it) {
                  AppLanguage.System -> Res.string.settings_language_system
                  AppLanguage.Chinese -> Res.string.settings_language_chinese
                  AppLanguage.English -> Res.string.settings_language_english
                },
              )
            }
            OverlayDropdownPreference(
              title = stringResource(Res.string.settings_language),
              summary = languageLabels[languages.indexOf(appLanguage)],
              startAction = { StartSettingIcon(MiuixIcons.Translate) },
              items = languageLabels,
              selectedIndex = languages.indexOf(appLanguage),
              onSelectedIndexChange = { index ->
                languages[index].takeIf { it != appLanguage }?.let(actions::setAppLanguage)
              },
            )
          }
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
          if (state.config.debugLogEnabled) {
            ArrowPreference(
              title = stringResource(Res.string.settings_view_logs),
              summary = stringResource(Res.string.settings_view_logs_summary),
              startAction = { StartSettingIcon(MiuixIcons.Notes) },
              onClick = { onNavigate(SettingsScreen.Logs) },
            )
          }
          if (actions.supportsConfigBackup()) {
            ArrowPreference(
              title = stringResource(Res.string.settings_export_config),
              summary = stringResource(Res.string.settings_export_config_summary),
              startAction = { StartSettingIcon(MiuixIcons.Backup) },
              onClick = {
                val payload = ConfigBackupCodec.encode(state.config)
                actions.exportConfig(payload) { result ->
                  when (result) {
                    ConfigTransferResult.Saved -> showBackupBanner(Res.string.settings_config_exported)
                    ConfigTransferResult.Failed -> showBackupBanner(Res.string.settings_config_export_failed)
                    ConfigTransferResult.Cancelled -> Unit
                  }
                }
              },
            )
            ArrowPreference(
              title = stringResource(Res.string.settings_import_config),
              summary = stringResource(Res.string.settings_import_config_summary),
              startAction = { StartSettingIcon(MiuixIcons.Import) },
              onClick = {
                val hasSavedLists = state.config.whitelistPackages.isNotEmpty() ||
                  state.config.blacklistPackages.isNotEmpty()
                if (hasSavedLists) {
                  showImportConfirmDialog = true
                } else {
                  launchConfigImport()
                }
              },
            )
          }
          ArrowPreference(
            title = stringResource(Res.string.settings_about),
            summary = stringResource(Res.string.settings_about_summary),
            startAction = { StartSettingIcon(MiuixIcons.Info) },
            onClick = { onNavigate(SettingsScreen.About) },
          )
        }

        Spacer(Modifier.height(16.dp))
      }
      backupBannerRes?.let { messageRes ->
        Text(
          text = stringResource(messageRes),
          style = MiuixTheme.textStyles.footnote1,
          fontWeight = FontWeight.Medium,
          color = MiuixTheme.colorScheme.onPrimaryContainer,
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = padding.calculateBottomPadding() + 12.dp)
            .clip(RoundedCornerShape(50))
            .background(MiuixTheme.colorScheme.primaryContainer)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        )
      }
    }

    OverlayDialog(
      show = showImportConfirmDialog,
      title = stringResource(Res.string.settings_import_confirm_title),
      summary = stringResource(Res.string.settings_import_confirm_message),
      onDismissRequest = { showImportConfirmDialog = false },
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Button(
          onClick = { showImportConfirmDialog = false },
          modifier = Modifier.weight(1f),
        ) {
          Text(stringResource(Res.string.settings_dialog_cancel))
        }
        Button(
          onClick = {
            showImportConfirmDialog = false
            launchConfigImport()
          },
          colors = ButtonDefaults.buttonColorsPrimary(),
          modifier = Modifier.weight(1f),
        ) {
          Text(stringResource(Res.string.settings_import_confirm_action))
        }
      }
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
      inputKey = ShowcaseKeys.LEAD_INPUT,
      confirmKey = ShowcaseKeys.LEAD_CONFIRM,
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
      inputKey = ShowcaseKeys.HOLD_INPUT,
      confirmKey = ShowcaseKeys.HOLD_CONFIRM,
    )
  }
}
