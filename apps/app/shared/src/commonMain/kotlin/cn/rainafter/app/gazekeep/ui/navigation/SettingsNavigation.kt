package cn.rainafter.app.gazekeep.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
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
import cn.rainafter.app.gazekeep.ui.showcase.ShowcaseKeys
import cn.rainafter.app.gazekeep.ui.showcase.showcaseAnchor
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

/** 转场以页面和重置代次为键：同一页面被外部重置时也换成新内容并淡入。 */
internal data class NavEntry(val screen: SettingsScreen, val generation: Int)

/**
 * 返回栈与转场方向。顶层页签只保留首页在栈底；[unavailable] 中的页面不可进入，
 * 供官网演示屏蔽需要相机的自检页。
 */
@Stable
internal class SettingsBackStack(
  private val entries: SnapshotStateList<SettingsScreen>,
  private val unavailable: Set<SettingsScreen> = emptySet(),
) {
  var isNavigatingBack by mutableStateOf(false)
    private set

  /** 外部立即换页时为 false：官网脚本换页由父页面遮罩，转场动画会在揭开时露出半程画面。 */
  var animated by mutableStateOf(true)
    private set
  private var generation by mutableIntStateOf(0)

  val size get() = entries.size
  val current get() = entries.lastOrNull() ?: SettingsScreen.Home
  val entry get() = NavEntry(current, generation)

  fun indexOf(screen: SettingsScreen) = entries.indexOf(screen)

  fun selectTab(tab: SettingsScreen) {
    if (current == tab) return
    animated = true
    isNavigatingBack = tab == SettingsScreen.Home
    entries.clear()
    entries.add(SettingsScreen.Home)
    if (tab != SettingsScreen.Home) {
      entries.add(tab)
    }
  }

  fun navigateTo(screen: SettingsScreen) {
    if (screen in unavailable) return
    if (screen in TopLevelTabs) {
      selectTab(screen)
      return
    }
    if (entries.lastOrNull() != screen) {
      animated = true
      isNavigatingBack = false
      entries.add(screen)
    }
  }

  fun navigateBack() {
    if (entries.size > 1) {
      animated = true
      isNavigatingBack = true
      entries.removeAt(entries.lastIndex)
    }
  }

  fun navigateToHome() {
    if (entries.size > 1) {
      animated = true
      isNavigatingBack = true
      while (entries.size > 1) {
        entries.removeAt(entries.lastIndex)
      }
    }
  }

  /**
   * 外部指定页面，栈重置为首页加目标页。[animate] 为 true 时按返回方向转场，
   * 并总是换成新内容，丢弃页面内的滚动与弹窗等临时状态。
   */
  fun show(screen: SettingsScreen, animate: Boolean) {
    val target = if (screen == SettingsScreen.Home) listOf(screen) else listOf(SettingsScreen.Home, screen)
    if (!animate && entries.toList() == target) return
    animated = animate
    isNavigatingBack = true
    generation++
    entries.clear()
    entries.addAll(target)
  }
}

@Composable
internal fun SettingsNavigation(
  controller: GazeKeepController,
  actions: PlatformActions,
  state: ControllerState,
  test: SelfTestState,
) {
  val entries = rememberSaveable(
    saver = listSaver(
      save = { it.map { screen -> screen.name } },
      restore = { saved ->
        saved.mapNotNull { runCatching { SettingsScreen.valueOf(it) }.getOrNull() }
          .ifEmpty { listOf(SettingsScreen.Home) }
          .toMutableStateList()
      },
    ),
  ) { mutableStateListOf(SettingsScreen.Home) }
  val backStack = remember(entries) { SettingsBackStack(entries) }
  SettingsNavHost(backStack, controller, actions, state, test)
}

/** 页面、底部栏与转场；Android 应用与官网演示共用。 */
@Composable
internal fun SettingsNavHost(
  backStack: SettingsBackStack,
  controller: GazeKeepController,
  actions: PlatformActions,
  state: ControllerState,
  test: SelfTestState,
  homeStatusOverride: String? = null,
) {
  val scope = rememberCoroutineScope()
  val currentScreen = backStack.current

  actions.HandleBack(backStack.size > 1) { backStack.navigateBack() }

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
    NavigationBar(modifier = Modifier.showcaseAnchor(ShowcaseKeys.NAV)) {
      NavigationBarItem(
        selected = selectedTab == SettingsScreen.Home,
        onClick = { backStack.selectTab(SettingsScreen.Home) },
        icon = MiuixIcons.Home,
        label = stringResource(Res.string.nav_home),
      )
      NavigationBarItem(
        selected = selectedTab == SettingsScreen.Settings,
        onClick = { backStack.selectTab(SettingsScreen.Settings) },
        icon = MiuixIcons.Settings,
        label = stringResource(Res.string.nav_settings),
      )
    }
  }

  AnimatedContent(
    targetState = backStack.entry,
    transitionSpec = {
      val from = initialState.screen
      val to = targetState.screen
      val isTabSwitch = from in TopLevelTabs && to in TopLevelTabs
      val isPop = backStack.isNavigatingBack ||
        backStack.indexOf(to) < backStack.indexOf(from) ||
        (to == SettingsScreen.Home && from != SettingsScreen.Home)
      val animSpec = tween<IntOffset>(durationMillis = MIUIX_TRANSITION_DURATION, easing = FastOutSlowInEasing)
      val fadeSpec = tween<Float>(durationMillis = MIUIX_TRANSITION_DURATION, easing = FastOutSlowInEasing)
      if (!backStack.animated) {
        EnterTransition.None togetherWith ExitTransition.None
      } else if (isTabSwitch || from == to) {
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
  ) { entry ->
    when (entry.screen) {
      SettingsScreen.Home -> HomeScreen(
        state = state,
        scope = scope,
        controller = controller,
        onNavigate = backStack::navigateTo,
        bottomBar = { BottomNavBar(selectedTab = SettingsScreen.Home) },
        statusSubtitleOverride = homeStatusOverride,
      )

      SettingsScreen.Settings -> SettingsTabScreen(
        state = state,
        scope = scope,
        controller = controller,
        actions = actions,
        onNavigate = backStack::navigateTo,
        bottomBar = { BottomNavBar(selectedTab = SettingsScreen.Settings) },
      )

      SettingsScreen.Backend -> BackendScreen(
        state = state,
        scope = scope,
        controller = controller,
        actions = actions,
        onBack = backStack::navigateBack,
      )

      SettingsScreen.Apps -> AppsScreen(
        state = state,
        scope = scope,
        controller = controller,
        actions = actions,
        onBack = backStack::navigateBack,
      )

      SettingsScreen.Logs -> LogScreen(
        state = state,
        scope = scope,
        controller = controller,
        actions = actions,
        onBack = backStack::navigateBack,
      )

      SettingsScreen.Privacy -> PrivacyScreen(
        onBack = backStack::navigateBack,
      )

      SettingsScreen.SelfTest -> SelfTestScreen(
        state = state,
        test = test,
        scope = scope,
        controller = controller,
        actions = actions,
        onBack = backStack::navigateBack,
        onComplete = backStack::navigateToHome,
      )

      SettingsScreen.About -> AboutScreen(
        state = state,
        actions = actions,
        onNavigate = backStack::navigateTo,
        onBack = backStack::navigateBack,
      )

      SettingsScreen.Notices -> NoticesScreen(
        state = state,
        actions = actions,
        onBack = backStack::navigateBack,
      )
    }
  }
}
