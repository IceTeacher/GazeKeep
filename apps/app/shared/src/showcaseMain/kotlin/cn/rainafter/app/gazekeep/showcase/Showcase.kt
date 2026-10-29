@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package cn.rainafter.app.gazekeep.showcase

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import cn.rainafter.app.gazekeep.core.model.Attention
import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.core.model.BackendLock
import cn.rainafter.app.gazekeep.core.model.GazeConfig
import cn.rainafter.app.gazekeep.core.model.Readiness
import cn.rainafter.app.gazekeep.ui.contract.AppEntry
import cn.rainafter.app.gazekeep.ui.contract.AppLanguage
import cn.rainafter.app.gazekeep.ui.contract.AppListLoadState
import cn.rainafter.app.gazekeep.ui.contract.ControllerState
import cn.rainafter.app.gazekeep.ui.contract.GazeKeepController
import cn.rainafter.app.gazekeep.ui.contract.PlatformActions
import cn.rainafter.app.gazekeep.ui.contract.SelfTestState
import cn.rainafter.app.gazekeep.ui.contract.SystemInfo
import cn.rainafter.app.gazekeep.ui.navigation.SettingsBackStack
import cn.rainafter.app.gazekeep.ui.navigation.SettingsNavHost
import cn.rainafter.app.gazekeep.ui.navigation.SettingsScreen
import gazekeep.shared.generated.resources.Res
import gazekeep.shared.generated.resources.m0_capturing
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.defaultTextStyles

private val route = mutableStateOf("home")
private val requestRevision = mutableStateOf(0)
private val locale = mutableStateOf("zh")
private val dark = mutableStateOf(true)
private val demoStatus = mutableStateOf("idle")

/**
 * 演示应用列表只使用开源应用与 AOSP 系统应用，按标签排序与真机列表一致；
 * 图标从模拟器实际渲染结果导出，位于 composeResources/files/apps/<包名>.webp。
 */
private val DEMO_APPS = listOf(
  AppEntry("cn.rainafter.app.gazekeep", "GazeKeep"),
  AppEntry("org.lsposed.manager", "LSPosed"),
  AppEntry("com.topjohnwu.magisk", "Magisk"),
  AppEntry("moe.shizuku.privileged.api", "Shizuku"),
  AppEntry("com.android.stk", "SIM Toolkit"),
  AppEntry("org.matrix.vector.manager", "Vector"),
)

/** 官网路由到演示页面；自检需要相机，演示中不可进入。 */
private val ROUTES = mapOf(
  "home" to SettingsScreen.Home,
  "backend" to SettingsScreen.Backend,
  "settings" to SettingsScreen.Settings,
  "whitelist" to SettingsScreen.Apps,
  "privacy" to SettingsScreen.Privacy,
)
private val backStack = SettingsBackStack(mutableStateListOf(SettingsScreen.Home), setOf(SettingsScreen.SelfTest))

// 仅供官网演示，平台操作不连接 Android 或申请硬件权限；访客的操作只改内存中的演示状态。
private object DemoController : GazeKeepController {
  val baseline =
    ControllerState(
      loaded = true,
      config = GazeConfig(enabled = true, packages = setOf("cn.rainafter.app.gazekeep", "org.lsposed.manager")),
      selfTestPassed = true, cameraGranted = true,
      backends = BackendId.entries.associateWith { Readiness.Ready },
      activeBackend = BackendId.Lsposed, serviceRunning = true,
      apps = DEMO_APPS,
      appsLoadState = AppListLoadState.Loaded,
      // 应用版本为真实的当前版本；Android 版本与设备信息仍为演示值。
      systemInfo = SystemInfo(
        appVersion = "v$SHOWCASE_VERSION_NAME",
        androidVersion = "—",
        deviceModel = "GazeKeep Demo",
      ),
    )
  override val state = MutableStateFlow(baseline)
  override val selfTest = MutableStateFlow(SelfTestState())
  override suspend fun updateConfig(transform: (GazeConfig) -> GazeConfig) {
    state.value =
      state.value.copy(config = transform(state.value.config))
  }
  override suspend fun setEnabled(enabled: Boolean) {
    updateConfig { it.copy(enabled = enabled) }
  }
  override suspend fun beginSelfTest() {}
  override suspend fun captureSelfTest() {}
  override fun cancelSelfTest() {}
  override fun clearLogs() {}
  override fun refreshLogs() {}
  override fun refresh() {}
  override suspend fun loadApps() {}
}

private object DemoActions : PlatformActions {
  override fun requestCameraPermission() {}
  override fun requestNotificationPermission() {}
  override fun openAccessibilitySettings() {}
  override fun requestShizukuPermission() {}
  override fun openShizukuApp() {}
  override fun openUrl(url: String) {}
  override fun shareLogs(text: String) {}
  override fun exportLogs(text: String) {}
  override fun formatLogTimestamp(epochMillis: Long) = ""

  // 演示语言由官网桥接控制，设置页不显示应用内语言切换。
  override fun appLanguage(): AppLanguage? = null
  override fun setAppLanguage(language: AppLanguage) {}
  override fun hasRomBackgroundPolicy(): Boolean = false
  override fun openRomBackgroundPolicy() {}

  @Composable override fun HandleBack(enabled: Boolean, onBack: () -> Unit) {}

  @Composable override fun SelfTestPreview(modifier: Modifier) {}

  // 与 Android 端一致：圆角容器底色上绘制应用图标，图标异步解码，缺失时只显示容器。
  @OptIn(ExperimentalResourceApi::class)
  @Composable
  override fun AppIcon(packageName: String, modifier: Modifier) {
    val icon by produceState<ImageBitmap?>(null, packageName) {
      value = runCatching { Res.readBytes("files/apps/$packageName.webp").decodeToImageBitmap() }.getOrNull()
    }
    Box(
      modifier
        .clip(RoundedCornerShape(8.dp))
        .background(MiuixTheme.colorScheme.surfaceContainerHigh),
    ) {
      icon?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
    }
  }
}

expect fun installBridge(
  screen: (String, Int, Boolean) -> Unit,
  status: (String) -> Unit,
  appearance: (String, Boolean) -> Unit,
)
expect fun notifyReady()
expect fun notifyRendered(screen: String, revision: Int)

/** 按演示状态覆盖后端、锁定与最近判定；[from] 为复位时的基线。 */
private fun applyStatus(status: String, from: ControllerState = DemoController.state.value) {
  val backend = when (status) {
    "backend_shizuku" -> BackendId.Shizuku
    "backend_accessibility" -> BackendId.Accessibility
    else -> BackendId.Lsposed
  }
  DemoController.state.value = from.copy(
    activeBackend = backend,
    config = from.config.copy(
      backendLock = if (status.startsWith("backend_")) BackendLock.valueOf(backend.name) else BackendLock.Auto,
    ),
    lastDecision = when (status) {
      "gaze_detected" -> Attention.Looking
      "dim_fallback" -> Attention.Unknown
      else -> null
    },
  )
}

@OptIn(ExperimentalResourceApi::class)
fun startShowcase() {
  installBridge(
    { screen, revision, restore ->
      val target = ROUTES[screen] ?: return@installBridge
      route.value = screen
      requestRevision.value = revision
      // 复位用于访客操作后回到滚动段落：丢弃演示状态改动，并以转场回到指定页面。
      if (restore) applyStatus(demoStatus.value, DemoController.baseline)
      backStack.show(target, animate = restore)
    },
    { status ->
      demoStatus.value = status
      applyStatus(status)
    },
    { lang, isDark ->
      locale.value = if (lang == "en") "en" else "zh"
      dark.value = isDark
    },
  )
  ComposeViewport("showcase") {
    val family by produceState<FontFamily?>(null) {
      val bytes = Res.readBytes("files/showcase_cjk.otf")
      value = FontFamily(Font("gazekeep-showcase", bytes))
    }
    val font = family ?: return@ComposeViewport
    val textStyles = remember(font) {
      val base = defaultTextStyles()
      base.copy(
        main = base.main.copy(fontFamily = font),
        paragraph = base.paragraph.copy(fontFamily = font),
        body1 = base.body1.copy(fontFamily = font),
        body2 = base.body2.copy(fontFamily = font),
        button = base.button.copy(fontFamily = font),
        footnote1 = base.footnote1.copy(fontFamily = font),
        footnote2 = base.footnote2.copy(fontFamily = font),
        headline1 = base.headline1.copy(fontFamily = font),
        headline2 = base.headline2.copy(fontFamily = font),
        subtitle = base.subtitle.copy(fontFamily = font),
        title1 = base.title1.copy(fontFamily = font),
        title2 = base.title2.copy(fontFamily = font),
        title3 = base.title3.copy(fontFamily = font),
        title4 = base.title4.copy(fontFamily = font),
      )
    }
    key(locale.value) {
      MiuixTheme(
        textStyles = textStyles,
        controller = remember(dark.value) {
          ThemeController(colorSchemeMode = if (dark.value) ColorSchemeMode.Dark else ColorSchemeMode.Light)
        },
      ) {
        val state by DemoController.state.collectAsState()
        val test by DemoController.selfTest.collectAsState()
        SettingsNavHost(
          backStack = backStack,
          controller = DemoController,
          actions = DemoActions,
          state = state,
          test = test,
          homeStatusOverride = if (demoStatus.value == "sampling") stringResource(Res.string.m0_capturing) else null,
        )
      }
    }
    LaunchedEffect(route.value, requestRevision.value) {
      val screen = route.value
      val revision = requestRevision.value
      withFrameNanos {}
      withFrameNanos {}
      notifyRendered(screen, revision)
    }
    LaunchedEffect(Unit) {
      withFrameNanos {}
      notifyReady()
    }
  }
}
