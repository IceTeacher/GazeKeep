package cn.rainafter.app.gazekeep

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import cn.rainafter.app.gazekeep.core.model.Attention
import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.core.model.BackendLock
import cn.rainafter.app.gazekeep.core.model.Readiness
import cn.rainafter.app.gazekeep.core.model.Sensitivity
import cn.rainafter.app.gazekeep.core.model.SetupStep
import cn.rainafter.app.gazekeep.ui.GazeKeepController
import cn.rainafter.app.gazekeep.ui.PlatformActions
import cn.rainafter.app.gazekeep.ui.AppEntry
import cn.rainafter.app.gazekeep.ui.ControllerState
import cn.rainafter.app.gazekeep.ui.SelfTestState
import cn.rainafter.app.gazekeep.ui.SelfTestStep
import cn.rainafter.app.gazekeep.ui.UiNotice
import gazekeep.shared.generated.resources.Res
import gazekeep.shared.generated.resources.app_title
import gazekeep.shared.generated.resources.m0_capturing
import gazekeep.shared.generated.resources.m0_result_away
import gazekeep.shared.generated.resources.m0_result_looking
import gazekeep.shared.generated.resources.m0_result_unknown
import gazekeep.shared.generated.resources.m2_accessibility_open_settings
import gazekeep.shared.generated.resources.m2_accessibility_unavailable
import gazekeep.shared.generated.resources.m2_backend_auto_status
import gazekeep.shared.generated.resources.m2_shizuku_needs_permission
import gazekeep.shared.generated.resources.m2_shizuku_needs_start
import gazekeep.shared.generated.resources.m2_shizuku_refresh
import gazekeep.shared.generated.resources.m2_shizuku_request_permission
import gazekeep.shared.generated.resources.m3_lsposed_unavailable
import gazekeep.shared.generated.resources.privacy_line_1
import gazekeep.shared.generated.resources.privacy_line_2
import gazekeep.shared.generated.resources.privacy_line_3
import gazekeep.shared.generated.resources.self_test_camera_permission
import gazekeep.shared.generated.resources.self_test_capture
import gazekeep.shared.generated.resources.self_test_complete
import gazekeep.shared.generated.resources.self_test_description
import gazekeep.shared.generated.resources.self_test_enable
import gazekeep.shared.generated.resources.self_test_looking
import gazekeep.shared.generated.resources.self_test_away
import gazekeep.shared.generated.resources.self_test_notification_permission
import gazekeep.shared.generated.resources.self_test_preview_hint
import gazekeep.shared.generated.resources.settings_accessibility
import gazekeep.shared.generated.resources.settings_app_count
import gazekeep.shared.generated.resources.settings_apps
import gazekeep.shared.generated.resources.settings_apps_selected
import gazekeep.shared.generated.resources.settings_auto
import gazekeep.shared.generated.resources.settings_auto_current
import gazekeep.shared.generated.resources.settings_back
import gazekeep.shared.generated.resources.settings_backend
import gazekeep.shared.generated.resources.settings_backend_order
import gazekeep.shared.generated.resources.settings_camera_pause
import gazekeep.shared.generated.resources.settings_camera_pause_summary
import gazekeep.shared.generated.resources.settings_dark_pause
import gazekeep.shared.generated.resources.settings_dark_pause_summary
import gazekeep.shared.generated.resources.settings_detection
import gazekeep.shared.generated.resources.settings_empty_scope
import gazekeep.shared.generated.resources.settings_failure_policy
import gazekeep.shared.generated.resources.settings_failure_summary
import gazekeep.shared.generated.resources.settings_group_basic
import gazekeep.shared.generated.resources.settings_group_security
import gazekeep.shared.generated.resources.settings_group_tools
import gazekeep.shared.generated.resources.settings_hold_10
import gazekeep.shared.generated.resources.settings_hold_30
import gazekeep.shared.generated.resources.settings_hold_60
import gazekeep.shared.generated.resources.settings_last_decision
import gazekeep.shared.generated.resources.settings_lead
import gazekeep.shared.generated.resources.settings_lead_5
import gazekeep.shared.generated.resources.settings_lead_8
import gazekeep.shared.generated.resources.settings_lead_summary
import gazekeep.shared.generated.resources.settings_loading
import gazekeep.shared.generated.resources.settings_loose
import gazekeep.shared.generated.resources.settings_lsposed
import gazekeep.shared.generated.resources.settings_max_hold
import gazekeep.shared.generated.resources.settings_max_hold_summary
import gazekeep.shared.generated.resources.settings_need_camera
import gazekeep.shared.generated.resources.settings_need_self_test
import gazekeep.shared.generated.resources.settings_no_apps
import gazekeep.shared.generated.resources.settings_not_ready
import gazekeep.shared.generated.resources.settings_notice_title
import gazekeep.shared.generated.resources.settings_off
import gazekeep.shared.generated.resources.settings_open_shizuku
import gazekeep.shared.generated.resources.settings_privacy
import gazekeep.shared.generated.resources.settings_privacy_commitments
import gazekeep.shared.generated.resources.settings_protection
import gazekeep.shared.generated.resources.settings_ready
import gazekeep.shared.generated.resources.settings_running
import gazekeep.shared.generated.resources.settings_save_failed
import gazekeep.shared.generated.resources.settings_scope
import gazekeep.shared.generated.resources.settings_search_apps
import gazekeep.shared.generated.resources.settings_search_clear
import gazekeep.shared.generated.resources.settings_self_test
import gazekeep.shared.generated.resources.settings_sensitivity
import gazekeep.shared.generated.resources.settings_shizuku
import gazekeep.shared.generated.resources.settings_standard
import gazekeep.shared.generated.resources.settings_version
import gazekeep.shared.generated.resources.settings_waiting
import gazekeep.shared.generated.resources.settings_whitelist
import gazekeep.shared.generated.resources.settings_whitelist_summary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Close
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

enum class SettingsScreen { Home, Backend, Detection, Scope, Apps, Protection, Privacy, SelfTest }

private class MiuixNavSettleEasing(
    response: Float = 0.8f,
    damping: Float = 0.95f,
) : Easing {
    private val r: Float
    private val w: Float
    private val c2: Float

    init {
        val omega = 2.0 * PI / response
        val k = omega * omega
        val c = damping * 4.0 * PI / response

        w = (sqrt(4.0 * k - c * c) / 2.0).toFloat()
        r = (-c / 2.0).toFloat()
        c2 = r / w
    }

    override fun transform(fraction: Float): Float {
        val t = fraction.toDouble()
        val decay = exp(r * t)
        return (decay * (-cos(w * t) + c2 * sin(w * t)) + 1.0).toFloat()
    }
}

private val MiuixNavEasing: Easing = MiuixNavSettleEasing()
private const val MiuixTransitionDuration = 450

@Composable
private fun SettingsGroupTitle(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = MiuixTheme.colorScheme.onBackgroundVariant,
    insideMargin: PaddingValues = PaddingValues(start = 16.dp, top = 8.dp, bottom = 2.dp),
) {
    SmallTitle(
        text = text,
        modifier = modifier,
        textColor = textColor,
        insideMargin = insideMargin,
    )
}

@Composable
fun App(controller: GazeKeepController, actions: PlatformActions) {
    val state by controller.state.collectAsState()
    val test by controller.selfTest.collectAsState()
    val scope = rememberCoroutineScope()

    val backStack = rememberSaveable(
        saver = listSaver(
            save = { it.map { screen -> screen.name } },
            restore = { saved -> saved.map { SettingsScreen.valueOf(it) }.toMutableStateList() }
        )
    ) { mutableStateListOf(SettingsScreen.Home) }

    val currentScreen = backStack.lastOrNull() ?: SettingsScreen.Home
    var isNavigatingBack by remember { mutableStateOf(false) }

    fun navigateTo(screen: SettingsScreen) {
        if (backStack.lastOrNull() != screen) {
            isNavigatingBack = false
            backStack.add(screen)
        }
    }

    fun navigateBack() {
        if (backStack.size > 1) {
            isNavigatingBack = true
            backStack.removeAt(backStack.lastIndex)
        }
    }

    fun navigateToHome() {
        if (backStack.size > 1) {
            isNavigatingBack = true
            while (backStack.size > 1) {
                backStack.removeAt(backStack.lastIndex)
            }
        }
    }

    actions.HandleBack(backStack.size > 1) { navigateBack() }

    LaunchedEffect(currentScreen) {
        when (currentScreen) {
            SettingsScreen.SelfTest -> controller.beginSelfTest()
            SettingsScreen.Scope, SettingsScreen.Apps -> controller.loadApps()
            else -> controller.cancelSelfTest()
        }
    }

    MiuixTheme(controller = remember { ThemeController() }) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                val isPop = isNavigatingBack ||
                    backStack.indexOf(targetState) < backStack.indexOf(initialState) ||
                    (targetState == SettingsScreen.Home && initialState != SettingsScreen.Home)
                val animSpec = tween<IntOffset>(durationMillis = MiuixTransitionDuration, easing = MiuixNavEasing)
                val fadeSpec = tween<Float>(durationMillis = MiuixTransitionDuration, easing = MiuixNavEasing)
                if (isPop) {
                    (slideInHorizontally(animationSpec = animSpec) { -it / 4 } + fadeIn(animationSpec = fadeSpec, initialAlpha = 0.9f))
                        .togetherWith(slideOutHorizontally(animationSpec = animSpec) { it })
                        .apply { targetContentZIndex = -1f }
                } else {
                    slideInHorizontally(animationSpec = animSpec) { it }
                        .togetherWith(slideOutHorizontally(animationSpec = animSpec) { -it / 4 } + fadeOut(animationSpec = fadeSpec, targetAlpha = 0.9f))
                        .apply { targetContentZIndex = 1f }
                }
            },
            label = "SettingsScreenTransition",
            modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface)
        ) { screen ->
            when (screen) {
                SettingsScreen.Home -> HomeScreen(
                    state = state,
                    scope = scope,
                    controller = controller,
                    onNavigate = ::navigateTo,
                )
                SettingsScreen.Backend -> BackendScreen(
                    state = state,
                    scope = scope,
                    controller = controller,
                    actions = actions,
                    onBack = ::navigateBack,
                )
                SettingsScreen.Detection -> DetectionScreen(
                    state = state,
                    scope = scope,
                    controller = controller,
                    onBack = ::navigateBack,
                )
                SettingsScreen.Scope -> ScopeScreen(
                    state = state,
                    scope = scope,
                    controller = controller,
                    onNavigate = ::navigateTo,
                    onBack = ::navigateBack,
                )
                SettingsScreen.Apps -> AppsScreen(
                    state = state,
                    scope = scope,
                    controller = controller,
                    actions = actions,
                    onBack = ::navigateBack,
                )
                SettingsScreen.Protection -> ProtectionScreen(
                    state = state,
                    scope = scope,
                    controller = controller,
                    onBack = ::navigateBack,
                )
                SettingsScreen.Privacy -> PrivacyScreen(
                    onBack = ::navigateBack,
                )
                SettingsScreen.SelfTest -> SelfTestScreen(
                    state = state,
                    test = test,
                    scope = scope,
                    controller = controller,
                    actions = actions,
                    onBack = ::navigateBack,
                    onComplete = ::navigateToHome,
                )
            }
        }
    }
}

@Composable
private fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    scrollBehavior: ScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = title,
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = MiuixIcons.Back,
                        contentDescription = stringResource(Res.string.settings_back),
                    )
                }
            }
        },
        actions = actions,
    )
}

@Composable
private fun AppScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    scrollBehavior: ScrollBehavior = MiuixScrollBehavior(),
    content: @Composable (PaddingValues, ScrollBehavior) -> Unit,
) {
    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            AppTopBar(
                title = title,
                onBack = onBack,
                scrollBehavior = scrollBehavior,
            )
        }
    ) { padding ->
        content(padding, scrollBehavior)
    }
}

@Composable
private fun NoticeCard(
    notice: UiNotice,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surfaceContainerHigh,
            contentColor = MiuixTheme.colorScheme.onSurfaceContainerHigh,
        )
    ) {
        BasicComponent(
            title = stringResource(Res.string.settings_notice_title),
            summary = stringResource(noticeLabel(notice)),
            onClick = onClick,
        )
    }
}

@Composable
private fun HomeScreen(
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
                            UiNotice.NeedsCamera, UiNotice.NeedsSelfTest -> { { onNavigate(SettingsScreen.SelfTest) } }
                            UiNotice.EmptyScope -> { { onNavigate(SettingsScreen.Scope) } }
                            UiNotice.SaveFailed -> null
                        }
                    )
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    SwitchPreference(
                        title = stringResource(Res.string.app_title),
                        checked = state.config.enabled,
                        summary = stringResource(
                            if (!state.config.enabled) Res.string.settings_off
                            else if (state.serviceRunning) Res.string.settings_running
                            else Res.string.settings_waiting
                        ),
                        onCheckedChange = { enabled ->
                            scope.launch {
                                controller.setEnabled(enabled)
                                if (controller.state.value.notice in listOf(UiNotice.NeedsCamera, UiNotice.NeedsSelfTest)) {
                                    onNavigate(SettingsScreen.SelfTest)
                                }
                            }
                        }
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
                        onClick = { onNavigate(SettingsScreen.Backend) }
                    )
                    ArrowPreference(
                        title = stringResource(Res.string.settings_detection),
                        summary = stringResource(
                            if (state.config.leadMillis == 5_000L) Res.string.settings_lead_5 else Res.string.settings_lead_8
                        ) + " · " + stringResource(
                            if (state.config.sensitivity == Sensitivity.Standard) Res.string.settings_standard else Res.string.settings_loose
                        ),
                        onClick = { onNavigate(SettingsScreen.Detection) }
                    )
                    ArrowPreference(
                        title = stringResource(Res.string.settings_scope),
                        summary = if (state.config.whitelistEnabled) {
                            stringResource(Res.string.settings_app_count, state.config.packages.size)
                        } else {
                            stringResource(Res.string.settings_off)
                        },
                        onClick = { onNavigate(SettingsScreen.Scope) }
                    )
                }

                SettingsGroupTitle(text = stringResource(Res.string.settings_group_security))
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = stringResource(Res.string.settings_protection),
                        summary = stringResource(Res.string.settings_max_hold) + " " + state.config.maxHoldMinutes + " 分钟",
                        onClick = { onNavigate(SettingsScreen.Protection) }
                    )
                    ArrowPreference(
                        title = stringResource(Res.string.settings_privacy),
                        summary = stringResource(Res.string.settings_privacy_commitments),
                        onClick = { onNavigate(SettingsScreen.Privacy) }
                    )
                }

                SettingsGroupTitle(text = stringResource(Res.string.settings_group_tools))
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = stringResource(Res.string.settings_self_test),
                        summary = if (state.cameraGranted) stringResource(Res.string.settings_ready) else stringResource(Res.string.settings_need_camera),
                        onClick = { onNavigate(SettingsScreen.SelfTest) }
                    )
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun BackendScreen(
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
                        }
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
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(Res.string.m2_accessibility_open_settings))
                        }
                    }
                    SetupStep.StartShizuku -> {
                        Button(
                            onClick = actions::openShizukuApp,
                            colors = ButtonDefaults.buttonColorsPrimary(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(Res.string.settings_open_shizuku))
                        }
                    }
                    SetupStep.GrantShizukuPermission -> {
                        Button(
                            onClick = actions::requestShizukuPermission,
                            colors = ButtonDefaults.buttonColorsPrimary(),
                            modifier = Modifier.fillMaxWidth()
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
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(Res.string.m2_shizuku_refresh))
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DetectionScreen(
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
                    }
                )
                OverlayDropdownPreference(
                    title = stringResource(Res.string.settings_sensitivity),
                    items = listOf(stringResource(Res.string.settings_standard), stringResource(Res.string.settings_loose)),
                    selectedIndex = if (state.config.sensitivity == Sensitivity.Standard) 0 else 1,
                    onSelectedIndexChange = { index ->
                        scope.launch { controller.updateConfig { it.copy(sensitivity = if (index == 0) Sensitivity.Standard else Sensitivity.Loose) } }
                    }
                )
            }

            SettingsGroupTitle(text = stringResource(Res.string.settings_failure_policy))
            Card(modifier = Modifier.fillMaxWidth()) {
                BasicComponent(
                    title = stringResource(Res.string.settings_failure_policy),
                    summary = stringResource(Res.string.settings_failure_summary)
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ScopeScreen(
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

@Composable
private fun AppListItem(
    app: AppEntry,
    isChecked: Boolean,
    actions: PlatformActions,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 12.dp
    ) {
        SwitchPreference(
            title = app.label,
            summary = app.packageName,
            checked = isChecked,
            startAction = {
                actions.AppIcon(
                    packageName = app.packageName,
                    modifier = Modifier.size(40.dp)
                )
            },
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun AppsScreen(
    state: ControllerState,
    scope: CoroutineScope,
    controller: GazeKeepController,
    actions: PlatformActions,
    onBack: () -> Unit,
) {
    var search by rememberSaveable { mutableStateOf("") }
    val filteredApps = remember(state.apps, search) {
        if (search.isBlank()) state.apps
        else state.apps.filter {
            it.label.contains(search, ignoreCase = true) || it.packageName.contains(search, ignoreCase = true)
        }
    }

    AppScaffold(
        title = stringResource(Res.string.settings_apps),
        onBack = onBack,
    ) { padding, scrollBehavior ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())
        ) {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                TextField(
                    value = search,
                    onValueChange = { search = it },
                    label = stringResource(Res.string.settings_search_apps),
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(
                            imageVector = MiuixIcons.Basic.Search,
                            contentDescription = null,
                            modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    },
                    trailingIcon = if (search.isNotEmpty()) {
                        {
                            IconButton(
                                onClick = { search = "" },
                                modifier = Modifier.padding(end = 4.dp),
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Basic.Close,
                                    contentDescription = stringResource(Res.string.settings_search_clear),
                                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    } else null,
                )
            }

            SettingsGroupTitle(
                text = stringResource(Res.string.settings_apps_selected, state.config.packages.size),
                insideMargin = PaddingValues(start = 32.dp, top = 4.dp, bottom = 6.dp)
            )

            if (filteredApps.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(if (state.apps.isEmpty()) Res.string.settings_loading else Res.string.settings_no_apps),
                        style = MiuixTheme.textStyles.subtitle,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(
                        items = filteredApps,
                        key = { it.packageName },
                        contentType = { "app" },
                    ) { app ->
                        val isChecked = app.packageName in state.config.packages
                        AppListItem(
                            app = app,
                            isChecked = isChecked,
                            actions = actions,
                            onCheckedChange = { selected ->
                                scope.launch {
                                    controller.updateConfig { config ->
                                        config.copy(
                                            packages = if (selected) config.packages + app.packageName else config.packages - app.packageName
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProtectionScreen(
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

@Composable
private fun PrivacyScreen(
    onBack: () -> Unit,
) {
    AppScaffold(
        title = stringResource(Res.string.settings_privacy),
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
            SettingsGroupTitle(text = stringResource(Res.string.settings_privacy_commitments))
            Card(modifier = Modifier.fillMaxWidth()) {
                BasicComponent(title = stringResource(Res.string.privacy_line_1))
                BasicComponent(title = stringResource(Res.string.privacy_line_2))
                BasicComponent(title = stringResource(Res.string.privacy_line_3))
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(Res.string.settings_version),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SelfTestScreen(
    state: ControllerState,
    test: SelfTestState,
    scope: CoroutineScope,
    controller: GazeKeepController,
    actions: PlatformActions,
    onBack: () -> Unit,
    onComplete: () -> Unit,
) {
    AppScaffold(
        title = stringResource(Res.string.settings_self_test),
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
            SmallTitle(
                text = stringResource(Res.string.self_test_description),
                textColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                insideMargin = PaddingValues(start = 16.dp, top = 4.dp, bottom = 4.dp),
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                BasicComponent(
                    title = stringResource(when (test.step) {
                        SelfTestStep.Looking -> Res.string.self_test_looking
                        SelfTestStep.Away -> Res.string.self_test_away
                        SelfTestStep.Complete -> Res.string.self_test_complete
                    }),
                    summary = test.attention?.let { attentionLabel(it) }
                )
            }

            if (state.cameraGranted && test.step != SelfTestStep.Complete) {
                Card(
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                    cornerRadius = 16.dp,
                ) {
                    actions.SelfTestPreview(Modifier.fillMaxSize())
                }
                SmallTitle(
                    text = stringResource(Res.string.self_test_preview_hint),
                    textColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    insideMargin = PaddingValues(start = 16.dp, top = 4.dp, bottom = 4.dp),
                )
            }

            Spacer(Modifier.height(8.dp))
            if (!state.cameraGranted) {
                Button(
                    onClick = actions::requestCameraPermission,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(Res.string.self_test_camera_permission))
                }
            } else if (test.step == SelfTestStep.Complete) {
                Button(
                    onClick = {
                        scope.launch {
                            controller.setEnabled(true)
                            onComplete()
                        }
                    },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(Res.string.self_test_enable))
                }
            } else {
                Button(
                    onClick = { scope.launch { controller.captureSelfTest() } },
                    enabled = !test.running,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(if (test.running) Res.string.m0_capturing else Res.string.self_test_capture))
                }
            }

            Button(
                onClick = actions::requestNotificationPermission,
                colors = ButtonDefaults.buttonColors(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(Res.string.self_test_notification_permission))
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun noticeLabel(notice: UiNotice) = when (notice) {
    UiNotice.NeedsCamera -> Res.string.settings_need_camera
    UiNotice.NeedsSelfTest -> Res.string.settings_need_self_test
    UiNotice.EmptyScope -> Res.string.settings_empty_scope
    UiNotice.SaveFailed -> Res.string.settings_save_failed
}

@Composable
private fun attentionLabel(attention: Attention) = stringResource(when (attention) {
    Attention.Looking -> Res.string.m0_result_looking
    Attention.Away -> Res.string.m0_result_away
    Attention.Unknown -> Res.string.m0_result_unknown
})

@Composable
private fun backendLabel(active: BackendId?, selected: BackendLock): String = stringResource(when {
    active == BackendId.Lsposed || active == null && selected == BackendLock.Lsposed -> Res.string.settings_lsposed
    active == BackendId.Shizuku || active == null && selected == BackendLock.Shizuku -> Res.string.settings_shizuku
    active == BackendId.Accessibility || active == null && selected == BackendLock.Accessibility -> Res.string.settings_accessibility
    else -> Res.string.settings_auto
})

@Composable
private fun backendName(id: BackendId): String = stringResource(when (id) {
    BackendId.Lsposed -> Res.string.settings_lsposed
    BackendId.Shizuku -> Res.string.settings_shizuku
    BackendId.Accessibility -> Res.string.settings_accessibility
})

@Composable
private fun readinessLabel(readiness: Readiness?) = stringResource(when (readiness) {
    Readiness.Ready -> Res.string.settings_ready
    is Readiness.NeedsSetup -> when (readiness.step) {
        SetupStep.StartShizuku -> Res.string.m2_shizuku_needs_start
        SetupStep.GrantShizukuPermission -> Res.string.m2_shizuku_needs_permission
        SetupStep.EnableAccessibilityService -> Res.string.m2_accessibility_unavailable
        else -> Res.string.m3_lsposed_unavailable
    }
    else -> Res.string.settings_not_ready
})
