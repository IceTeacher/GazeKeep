package cn.rainafter.app.gazekeep.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import cn.rainafter.app.gazekeep.ui.contract.ControllerState
import cn.rainafter.app.gazekeep.ui.contract.GazeKeepController
import cn.rainafter.app.gazekeep.ui.contract.PlatformActions
import cn.rainafter.app.gazekeep.ui.contract.SelfTestState
import cn.rainafter.app.gazekeep.ui.screens.AppsScreen
import cn.rainafter.app.gazekeep.ui.screens.BackendScreen
import cn.rainafter.app.gazekeep.ui.screens.DetectionScreen
import cn.rainafter.app.gazekeep.ui.screens.HomeScreen
import cn.rainafter.app.gazekeep.ui.screens.PrivacyScreen
import cn.rainafter.app.gazekeep.ui.screens.ProtectionScreen
import cn.rainafter.app.gazekeep.ui.screens.ScopeScreen
import cn.rainafter.app.gazekeep.ui.screens.SelfTestScreen
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

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
private const val MIUIX_TRANSITION_DURATION = 450

@Composable
internal fun SettingsNavigation(controller: GazeKeepController, actions: PlatformActions, state: ControllerState, test: SelfTestState) {
    val scope = rememberCoroutineScope()

    val backStack = rememberSaveable(
        saver = listSaver(
            save = { it.map { screen -> screen.name } },
            restore = { saved -> saved.map { SettingsScreen.valueOf(it) }.toMutableStateList() },
        ),
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

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            val isPop = isNavigatingBack ||
                backStack.indexOf(targetState) < backStack.indexOf(initialState) ||
                (targetState == SettingsScreen.Home && initialState != SettingsScreen.Home)
            val animSpec = tween<IntOffset>(durationMillis = MIUIX_TRANSITION_DURATION, easing = MiuixNavEasing)
            val fadeSpec = tween<Float>(durationMillis = MIUIX_TRANSITION_DURATION, easing = MiuixNavEasing)
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
        modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface),
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
