@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package cn.rainafter.app.gazekeep.showcase

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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
import cn.rainafter.app.gazekeep.ui.contract.AppListLoadState
import cn.rainafter.app.gazekeep.ui.contract.ControllerState
import cn.rainafter.app.gazekeep.ui.contract.GazeKeepController
import cn.rainafter.app.gazekeep.ui.contract.PlatformActions
import cn.rainafter.app.gazekeep.ui.contract.SelfTestState
import cn.rainafter.app.gazekeep.ui.contract.SystemInfo
import cn.rainafter.app.gazekeep.ui.screens.AppsScreen
import cn.rainafter.app.gazekeep.ui.screens.BackendScreen
import cn.rainafter.app.gazekeep.ui.screens.HomeScreen
import cn.rainafter.app.gazekeep.ui.screens.PrivacyScreen
import cn.rainafter.app.gazekeep.ui.screens.SettingsTabScreen
import gazekeep.shared.generated.resources.Res
import gazekeep.shared.generated.resources.m0_capturing
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.ExperimentalResourceApi
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

// 仅供官网演示，平台操作不连接 Android 或申请硬件权限。
private object DemoController : GazeKeepController {
  override val state = MutableStateFlow(
    ControllerState(
      loaded = true,
      config = GazeConfig(enabled = true, packages = setOf("demo.reader", "demo.browser")),
      selfTestPassed = true, cameraGranted = true,
      backends = BackendId.entries.associateWith { Readiness.Ready },
      activeBackend = BackendId.Lsposed, serviceRunning = true,
      apps = listOf(
        AppEntry("demo.reader", "Reader"),
        AppEntry("demo.browser", "Browser"),
        AppEntry("demo.documents", "Documents"),
      ),
      appsLoadState = AppListLoadState.Loaded,
      systemInfo = SystemInfo(appVersion = "Demo", androidVersion = "—", deviceModel = "GazeKeep Demo"),
    ),
  )
  override val selfTest = MutableStateFlow(SelfTestState())
  override suspend fun updateConfig(transform: (GazeConfig) -> GazeConfig) {
    state.value =
      state.value.copy(config = transform(state.value.config))
  }
  override suspend fun setEnabled(enabled: Boolean) {}
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

  @Composable override fun HandleBack(enabled: Boolean, onBack: () -> Unit) {}

  @Composable override fun SelfTestPreview(modifier: Modifier) {}

  @Composable override fun AppIcon(packageName: String, modifier: Modifier) {
    val color = when (packageName) {
      "demo.reader" -> Color(0xFF279D78)
      "demo.browser" -> Color(0xFF4E78DB)
      else -> Color(0xFFE7A142)
    }
    Canvas(modifier.clip(RoundedCornerShape(11.dp)).background(color)) {
      val w = size.width
      val h = size.height
      val ink = Color.White
      val stroke = w * 0.035f
      when (packageName) {
        "demo.reader" -> {
          val book = Path().apply {
            moveTo(w * 0.19f, h * 0.25f)
            quadraticTo(w * 0.36f, h * 0.20f, w * 0.5f, h * 0.31f)
            quadraticTo(w * 0.64f, h * 0.20f, w * 0.81f, h * 0.25f)
            lineTo(w * 0.81f, h * 0.72f)
            quadraticTo(w * 0.64f, h * 0.67f, w * 0.5f, h * 0.78f)
            quadraticTo(w * 0.36f, h * 0.67f, w * 0.19f, h * 0.72f)
            close()
          }
          drawPath(book, ink)
          drawLine(color, Offset(w * 0.5f, h * 0.32f), Offset(w * 0.5f, h * 0.73f), stroke)
        }

        "demo.browser" -> {
          drawCircle(ink, w * 0.3f, style = Stroke(stroke))
          drawOval(ink, Offset(w * 0.37f, h * 0.20f), Size(w * 0.26f, h * 0.60f), style = Stroke(stroke))
          drawLine(ink, Offset(w * 0.21f, h * 0.5f), Offset(w * 0.79f, h * 0.5f), stroke)
        }

        else -> {
          drawRoundRect(
            ink,
            Offset(w * 0.27f, h * 0.18f),
            Size(w * 0.46f, h * 0.64f),
            androidx.compose.ui.geometry.CornerRadius(w * 0.05f),
          )
          for (y in listOf(0.39f, 0.51f, 0.63f)) {
            drawLine(color, Offset(w * 0.36f, h * y), Offset(w * 0.64f, h * y), stroke)
          }
        }
      }
    }
  }
}

expect fun installBridge(screen: (String, Int) -> Unit, status: (String) -> Unit, appearance: (String, Boolean) -> Unit)
expect fun notifyReady()
expect fun notifyRendered(screen: String, revision: Int)

@OptIn(ExperimentalResourceApi::class)
fun startShowcase() {
  installBridge(
    { screen, revision ->
      if (screen in listOf("home", "backend", "settings", "whitelist", "privacy")) {
        route.value = screen
        requestRevision.value = revision
      }
    },
    { status ->
      demoStatus.value = status
      val backend = when (status) {
        "backend_shizuku" -> BackendId.Shizuku
        "backend_accessibility" -> BackendId.Accessibility
        else -> BackendId.Lsposed
      }
      DemoController.state.value = DemoController.state.value.copy(
        activeBackend = backend,
        config = DemoController.state.value.config.copy(
          backendLock = if (status.startsWith("backend_")) BackendLock.valueOf(backend.name) else BackendLock.Auto,
        ),
        lastDecision = when (status) {
          "gaze_detected" -> Attention.Looking
          "dim_fallback" -> Attention.Unknown
          else -> null
        },
      )
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
        val scope = rememberCoroutineScope()
        Box(Modifier.fillMaxSize()) {
          when (route.value) {
            "backend" -> BackendScreen(state, scope, DemoController, DemoActions, {})

            "settings" -> SettingsTabScreen(state, scope, DemoController, {})

            "privacy" -> PrivacyScreen({})

            "whitelist" -> AppsScreen(state, scope, DemoController, DemoActions, {})

            else -> HomeScreen(
              state,
              scope,
              DemoController,
              {},
              statusSubtitleOverride = if (demoStatus.value ==
                "sampling"
              ) {
                stringResource(Res.string.m0_capturing)
              } else {
                null
              },
            )
          }
        }
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
