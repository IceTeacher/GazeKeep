package cn.rainafter.app.gazekeep

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.rainafter.app.gazekeep.core.model.*
import cn.rainafter.app.gazekeep.ui.*
import gazekeep.shared.generated.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.StringResource
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

enum class SettingsScreen { Home, Backend, Detection, Scope, Apps, Protection, Privacy, SelfTest }

@Composable
fun App(controller: GazeKeepController, actions: PlatformActions) {
    val state by controller.state.collectAsState()
    val test by controller.selfTest.collectAsState()
    val scope = rememberCoroutineScope()
    var screen by rememberSaveable { mutableStateOf(SettingsScreen.Home) }
    fun back() { screen = if (screen == SettingsScreen.Apps) SettingsScreen.Scope else SettingsScreen.Home }
    actions.HandleBack(screen != SettingsScreen.Home) { back() }
    LaunchedEffect(screen) {
        when (screen) {
            SettingsScreen.SelfTest -> controller.beginSelfTest()
            SettingsScreen.Apps -> controller.loadApps()
            else -> controller.cancelSelfTest()
        }
    }
    MiuixTheme(controller = remember { ThemeController() }) {
        Scaffold(topBar = {
            TopAppBar(
                title = stringResource(screenTitle(screen)),
                navigationIcon = {
                    if (screen != SettingsScreen.Home) TextButton(text = stringResource(Res.string.settings_back), onClick = { back() })
                },
            )
        }) { padding ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding)
                    .consumeWindowInsets(padding).padding(horizontal = 16.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (!state.loaded) Text(stringResource(Res.string.settings_loading))
                else {
                    state.notice?.let { notice ->
                        Text(stringResource(noticeLabel(notice)), modifier = Modifier.padding(horizontal = 12.dp))
                    }
                    when (screen) {
                        SettingsScreen.Home -> {
                            Card {
                                SwitchPreference(title = stringResource(Res.string.app_title), checked = state.config.enabled,
                                    summary = stringResource(if (!state.config.enabled) Res.string.settings_off else if (state.serviceRunning) Res.string.settings_running else Res.string.settings_waiting),
                                    onCheckedChange = { enabled -> scope.launch {
                                        controller.setEnabled(enabled)
                                        if (controller.state.value.notice in listOf(UiNotice.NeedsCamera, UiNotice.NeedsSelfTest)) screen = SettingsScreen.SelfTest
                                    } })
                                ArrowPreference(title = stringResource(Res.string.settings_backend), summary = backendLabel(state.activeBackend, state.config.backendLock), onClick = { screen = SettingsScreen.Backend })
                                state.lastDecision?.let { BasicComponent(title = attentionLabel(it)) }
                            }
                            Card {
                                listOf(SettingsScreen.Detection, SettingsScreen.Scope, SettingsScreen.Protection, SettingsScreen.Privacy).forEach { page ->
                                    ArrowPreference(title = stringResource(screenTitle(page)), onClick = { screen = page })
                                }
                            }
                            Card { ArrowPreference(title = stringResource(Res.string.settings_self_test), onClick = { screen = SettingsScreen.SelfTest }) }
                        }
                        SettingsScreen.Backend -> {
                            Text(stringResource(Res.string.settings_backend_order), modifier = Modifier.padding(horizontal = 12.dp))
                            Card {
                                listOf(BackendLock.Auto, BackendLock.Lsposed, BackendLock.Shizuku, BackendLock.Accessibility).forEach { lock ->
                                    val id = when (lock) { BackendLock.Lsposed -> BackendId.Lsposed; BackendLock.Shizuku -> BackendId.Shizuku; BackendLock.Accessibility -> BackendId.Accessibility; else -> null }
                                    RadioButtonPreference(title = backendLabel(null, lock), selected = lock == state.config.backendLock,
                                        summary = id?.let { readinessLabel(state.backends[it]) },
                                        onClick = { scope.launch { controller.updateConfig { it.copy(backendLock = lock) } } })
                                }
                            }
                            val selected = when (state.config.backendLock) {
                                BackendLock.Lsposed -> state.backends[BackendId.Lsposed]
                                BackendLock.Shizuku -> state.backends[BackendId.Shizuku]
                                BackendLock.Accessibility -> state.backends[BackendId.Accessibility]
                                BackendLock.Auto -> null
                            }
                            if (selected is Readiness.NeedsSetup) when (selected.step) {
                                SetupStep.EnableAccessibilityService -> Action(Res.string.m2_accessibility_open_settings, actions::openAccessibilitySettings)
                                SetupStep.StartShizuku -> Action(Res.string.settings_open_shizuku, actions::openShizukuApp)
                                SetupStep.GrantShizukuPermission -> Action(Res.string.m2_shizuku_request_permission, actions::requestShizukuPermission)
                                else -> Unit
                            }
                            Action(Res.string.m2_shizuku_refresh, controller::refresh)
                        }
                        SettingsScreen.Detection -> {
                            Card {
                                OverlayDropdownPreference(title = stringResource(Res.string.settings_lead), summary = stringResource(Res.string.settings_lead_summary),
                                    items = listOf(stringResource(Res.string.settings_lead_5), stringResource(Res.string.settings_lead_8)),
                                    selectedIndex = if (state.config.leadMillis == 5_000L) 0 else 1,
                                    onSelectedIndexChange = { index -> scope.launch { controller.updateConfig { it.copy(leadMillis = if (index == 0) 5_000L else 8_000L) } } })
                                OverlayDropdownPreference(title = stringResource(Res.string.settings_sensitivity),
                                    items = listOf(stringResource(Res.string.settings_standard), stringResource(Res.string.settings_loose)),
                                    selectedIndex = if (state.config.sensitivity == Sensitivity.Standard) 0 else 1,
                                    onSelectedIndexChange = { index -> scope.launch { controller.updateConfig { it.copy(sensitivity = if (index == 0) Sensitivity.Standard else Sensitivity.Loose) } } })
                                BasicComponent(title = stringResource(Res.string.settings_failure_policy), summary = stringResource(Res.string.settings_failure_summary))
                            }
                        }
                        SettingsScreen.Scope -> Card {
                            SwitchPreference(title = stringResource(Res.string.settings_whitelist), summary = stringResource(Res.string.settings_whitelist_summary),
                                checked = state.config.whitelistEnabled, onCheckedChange = { enabled -> scope.launch { controller.updateConfig { it.copy(whitelistEnabled = enabled) } } })
                            ArrowPreference(title = stringResource(Res.string.settings_apps), summary = stringResource(Res.string.settings_app_count, state.config.packages.size), onClick = { screen = SettingsScreen.Apps })
                        }
                        SettingsScreen.Apps -> {
                            var search by rememberSaveable { mutableStateOf("") }
                            TextField(value = search, onValueChange = { search = it }, label = stringResource(Res.string.settings_search_apps), singleLine = true, modifier = Modifier.fillMaxWidth())
                            val apps = state.apps.filter { it.label.contains(search, true) || it.packageName.contains(search, true) }
                            if (apps.isEmpty()) Text(stringResource(Res.string.settings_no_apps))
                            else Card {
                                apps.forEach { app ->
                                    SwitchPreference(title = app.label, summary = app.packageName, checked = app.packageName in state.config.packages,
                                        onCheckedChange = { selected -> scope.launch { controller.updateConfig { config -> config.copy(packages = if (selected) config.packages + app.packageName else config.packages - app.packageName) } } })
                                }
                            }
                        }
                        SettingsScreen.Protection -> Card {
                            OverlayDropdownPreference(title = stringResource(Res.string.settings_max_hold), summary = stringResource(Res.string.settings_max_hold_summary),
                                items = listOf(stringResource(Res.string.settings_hold_10), stringResource(Res.string.settings_hold_30), stringResource(Res.string.settings_hold_60)),
                                selectedIndex = listOf(10, 30, 60).indexOf(state.config.maxHoldMinutes),
                                onSelectedIndexChange = { index -> scope.launch { controller.updateConfig { it.copy(maxHoldMinutes = listOf(10, 30, 60)[index]) } } })
                            BasicComponent(title = stringResource(Res.string.settings_camera_pause), summary = stringResource(Res.string.settings_camera_pause_summary))
                            BasicComponent(title = stringResource(Res.string.settings_dark_pause), summary = stringResource(Res.string.settings_dark_pause_summary))
                        }
                        SettingsScreen.Privacy -> {
                            Card { listOf(Res.string.privacy_line_1, Res.string.privacy_line_2, Res.string.privacy_line_3).forEach { line -> BasicComponent(title = stringResource(line)) } }
                            Text(stringResource(Res.string.settings_version), modifier = Modifier.padding(horizontal = 12.dp))
                        }
                        SettingsScreen.SelfTest -> {
                            Text(stringResource(Res.string.self_test_description), modifier = Modifier.padding(horizontal = 12.dp))
                            Card { BasicComponent(title = stringResource(when (test.step) {
                                SelfTestStep.Looking -> Res.string.self_test_looking
                                SelfTestStep.Away -> Res.string.self_test_away
                                SelfTestStep.Complete -> Res.string.self_test_complete
                            }), summary = test.attention?.let { attentionLabel(it) }) }
                            if (state.cameraGranted && test.step != SelfTestStep.Complete) {
                                actions.SelfTestPreview(Modifier.fillMaxWidth().height(220.dp))
                                Text(stringResource(Res.string.self_test_preview_hint), modifier = Modifier.padding(horizontal = 12.dp))
                            }
                            if (!state.cameraGranted) Action(Res.string.self_test_camera_permission, actions::requestCameraPermission)
                            else if (test.step == SelfTestStep.Complete) Action(Res.string.self_test_enable) { scope.launch { controller.setEnabled(true); screen = SettingsScreen.Home } }
                            else Button(onClick = { scope.launch { controller.captureSelfTest() } }, enabled = !test.running, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(if (test.running) Res.string.m0_capturing else Res.string.self_test_capture))
                            }
                            Action(Res.string.self_test_notification_permission, actions::requestNotificationPermission)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun Action(label: StringResource, onClick: () -> Unit) { Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(stringResource(label)) } }
private fun screenTitle(screen: SettingsScreen) = when (screen) {
    SettingsScreen.Home -> Res.string.app_title
    SettingsScreen.Backend -> Res.string.settings_backend
    SettingsScreen.Detection -> Res.string.settings_detection
    SettingsScreen.Scope, SettingsScreen.Apps -> Res.string.settings_scope
    SettingsScreen.Protection -> Res.string.settings_protection
    SettingsScreen.Privacy -> Res.string.settings_privacy
    SettingsScreen.SelfTest -> Res.string.settings_self_test
}
private fun noticeLabel(notice: UiNotice) = when (notice) {
    UiNotice.NeedsCamera -> Res.string.settings_need_camera
    UiNotice.NeedsSelfTest -> Res.string.settings_need_self_test
    UiNotice.EmptyScope -> Res.string.settings_empty_scope
    UiNotice.SaveFailed -> Res.string.settings_save_failed
}
@Composable private fun attentionLabel(attention: Attention) = stringResource(when (attention) {
    Attention.Looking -> Res.string.m0_result_looking
    Attention.Away -> Res.string.m0_result_away
    Attention.Unknown -> Res.string.m0_result_unknown
})
@Composable private fun backendLabel(active: BackendId?, selected: BackendLock): String = stringResource(when {
    active == BackendId.Lsposed || active == null && selected == BackendLock.Lsposed -> Res.string.settings_lsposed
    active == BackendId.Shizuku || active == null && selected == BackendLock.Shizuku -> Res.string.settings_shizuku
    active == BackendId.Accessibility || active == null && selected == BackendLock.Accessibility -> Res.string.settings_accessibility
    else -> Res.string.settings_auto
})
@Composable private fun readinessLabel(readiness: Readiness?) = stringResource(when (readiness) {
    Readiness.Ready -> Res.string.settings_ready
    is Readiness.NeedsSetup -> when (readiness.step) {
        SetupStep.StartShizuku -> Res.string.m2_shizuku_needs_start
        SetupStep.GrantShizukuPermission -> Res.string.m2_shizuku_needs_permission
        SetupStep.EnableAccessibilityService -> Res.string.m2_accessibility_unavailable
        else -> Res.string.m3_lsposed_unavailable
    }
    else -> Res.string.settings_not_ready
})
