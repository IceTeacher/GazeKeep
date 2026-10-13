package cn.rainafter.app.gazekeep.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
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
import cn.rainafter.app.gazekeep.ui.screens.AboutScreen
import cn.rainafter.app.gazekeep.ui.screens.AppsScreen
import cn.rainafter.app.gazekeep.ui.screens.BackendScreen
import cn.rainafter.app.gazekeep.ui.screens.HomeScreen
import cn.rainafter.app.gazekeep.ui.screens.LogScreen
import cn.rainafter.app.gazekeep.ui.screens.NoticesScreen
import cn.rainafter.app.gazekeep.ui.screens.PrivacyScreen
import cn.rainafter.app.gazekeep.ui.screens.SelfTestScreen
import cn.rainafter.app.gazekeep.ui.screens.SettingsTabScreen
import gazekeep.shared.generated.resources.Res
import gazekeep.shared.generated.resources.nav_home
import gazekeep.shared.generated.resources.nav_settings
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class SettingsScreen {
  Home,
  Settings,
  Backend,
  Apps,
  Logs,
  Privacy,
  SelfTest,
  About,
  Notices,
}

private val TopLevelTabs = setOf(SettingsScreen.Home, SettingsScreen.Settings)

private const val MIUIX_TRANSITION_DURATION = 450

@Composable
internal fun SettingsNavigation(
  controller: GazeKeepController,
  actions: PlatformActions,
  state: ControllerState,
  test: SelfTestState,
) {
  val scope = rememberCoroutineScope()

  val backStack = rememberSaveable(
    saver = listSaver(
      save = { it.map { screen -> screen.name } },
      restore = { saved ->
        saved.mapNotNull { runCatching { SettingsScreen.valueOf(it) }.getOrNull() }
          .ifEmpty { listOf(SettingsScreen.Home) }
          .toMutableStateList()
      },
    ),
  ) { mutableStateListOf(SettingsScreen.Home) }

  val currentScreen = backStack.lastOrNull() ?: SettingsScreen.Home
  var isNavigatingBack by remember { mutableStateOf(false) }

  fun selectTab(tab: SettingsScreen) {
    if (currentScreen == tab) return
    isNavigatingBack = tab == SettingsScreen.Home
    backStack.clear()
    backStack.add(SettingsScreen.Home)
    if (tab != SettingsScreen.Home) {
      backStack.add(tab)
    }
  }

  fun navigateTo(screen: SettingsScreen) {
    if (screen in TopLevelTabs) {
      selectTab(screen)
      return
    }
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

      SettingsScreen.Apps -> {
        controller.cancelSelfTest()
        controller.loadApps()
      }

      SettingsScreen.Logs -> {
        controller.cancelSelfTest()
      }

      else -> controller.cancelSelfTest()
    }
  }

  @Composable
  fun BottomNavBar(selectedTab: SettingsScreen) {
    NavigationBar {
      NavigationBarItem(
        selected = selectedTab == SettingsScreen.Home,
        onClick = { selectTab(SettingsScreen.Home) },
        icon = MiuixIcons.Home,
        label = stringResource(Res.string.nav_home),
      )
      NavigationBarItem(
        selected = selectedTab == SettingsScreen.Settings,
        onClick = { selectTab(SettingsScreen.Settings) },
        icon = MiuixIcons.Settings,
        label = stringResource(Res.string.nav_settings),
      )
    }
  }

  AnimatedContent(
    targetState = currentScreen,
    transitionSpec = {
      val isTabSwitch = initialState in TopLevelTabs && targetState in TopLevelTabs
      val isPop = isNavigatingBack ||
        backStack.indexOf(targetState) < backStack.indexOf(initialState) ||
        (targetState == SettingsScreen.Home && initialState != SettingsScreen.Home)
      val animSpec = tween<IntOffset>(durationMillis = MIUIX_TRANSITION_DURATION, easing = FastOutSlowInEasing)
      val fadeSpec = tween<Float>(durationMillis = MIUIX_TRANSITION_DURATION, easing = FastOutSlowInEasing)
      if (isTabSwitch) {
        fadeIn(animationSpec = tween(durationMillis = 220))
          .togetherWith(fadeOut(animationSpec = tween(durationMillis = 220)))
      } else if (isPop) {
        (
          slideInHorizontally(animationSpec = animSpec) { -it / 4 } +
            fadeIn(animationSpec = fadeSpec, initialAlpha = 0.9f)
          )
          .togetherWith(slideOutHorizontally(animationSpec = animSpec) { it })
          .apply { targetContentZIndex = -1f }
      } else {
        slideInHorizontally(animationSpec = animSpec) { it }
          .togetherWith(
            slideOutHorizontally(animationSpec = animSpec) { -it / 4 } +
              fadeOut(animationSpec = fadeSpec, targetAlpha = 0.9f),
          )
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
        bottomBar = { BottomNavBar(selectedTab = SettingsScreen.Home) },
      )

      SettingsScreen.Settings -> SettingsTabScreen(
        state = state,
        scope = scope,
        controller = controller,
        onNavigate = ::navigateTo,
        bottomBar = { BottomNavBar(selectedTab = SettingsScreen.Settings) },
      )

      SettingsScreen.Backend -> BackendScreen(
        state = state,
        scope = scope,
        controller = controller,
        actions = actions,
        onBack = ::navigateBack,
      )

      SettingsScreen.Apps -> AppsScreen(
        state = state,
        scope = scope,
        controller = controller,
        actions = actions,
        onBack = ::navigateBack,
      )

      SettingsScreen.Logs -> LogScreen(
        state = state,
        scope = scope,
        controller = controller,
        actions = actions,
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

      SettingsScreen.About -> AboutScreen(
        state = state,
        actions = actions,
        onNavigate = ::navigateTo,
        onBack = ::navigateBack,
      )

      SettingsScreen.Notices -> NoticesScreen(
        state = state,
        actions = actions,
        onBack = ::navigateBack,
      )
    }
  }
}
