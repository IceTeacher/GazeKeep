package cn.rainafter.app.gazekeep.platform

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import cn.rainafter.app.gazekeep.app.AppGraph
import cn.rainafter.app.gazekeep.applist.AppIconCache
import cn.rainafter.app.gazekeep.controller.AndroidGazeKeepController
import cn.rainafter.app.gazekeep.core.model.ConfigBackupCodec
import cn.rainafter.app.gazekeep.core.model.ConfigExportResult
import cn.rainafter.app.gazekeep.core.policy.RomBackgroundPolicy
import cn.rainafter.app.gazekeep.core.policy.resolveRomBackgroundPolicy
import cn.rainafter.app.gazekeep.engine.config.ConfigStore
import cn.rainafter.app.gazekeep.ui.contract.AppLanguage
import cn.rainafter.app.gazekeep.ui.contract.ConfigReadResult
import cn.rainafter.app.gazekeep.ui.contract.ConfigTransferResult
import cn.rainafter.app.gazekeep.ui.contract.ControllerState
import cn.rainafter.app.gazekeep.ui.contract.PlatformActions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

internal fun readBoundedUtf8(input: InputStream, maxBytes: Int = ConfigBackupCodec.MAX_BACKUP_BYTES): ConfigReadResult {
  val buffer = ByteArray(4_096)
  val out = ByteArrayOutputStream()
  var total = 0
  while (true) {
    val read = input.read(buffer)
    if (read < 0) break
    total += read
    if (total > maxBytes) return ConfigReadResult.TooLarge
    out.write(buffer, 0, read)
  }
  return ConfigReadResult.Loaded(out.toByteArray().decodeToString())
}

/**
 * 仅对已真机验证的小米 HyperOS 保留私有省电策略直达（失败回退应用详情页）；
 * 其余未真机覆盖的 ROM 统一直达系统公开应用详情页，避免误跳或崩溃。
 */
internal fun launchRomBackgroundPolicy(
  policy: RomBackgroundPolicy?,
  packageName: String,
  appLabel: String,
  startIntent: (Intent) -> Unit,
  fallbackAppDetails: () -> Unit,
) {
  if (policy == RomBackgroundPolicy.Xiaomi) {
    val intent = Intent()
      .setClassName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
      .putExtra("package_name", packageName)
      .putExtra("package_label", appLabel)
    runCatching { startIntent(intent) }.onFailure { fallbackAppDetails() }
    return
  }
  fallbackAppDetails()
}

/**
 * 无易失回调状态的 SAF 配置备份协调器：
 * 即使在系统文件选择器打开期间或返回 URI 后发生 Activity 旋转/重建，
 * 固定 key launcher 与进程级传输作用域可保证读写与持久化落盘不被中断并向新界面发布反馈。
 */
internal class SafConfigBackupCoordinator(
  registry: ActivityResultRegistry,
  private val scope: CoroutineScope,
  private val canExportNow: () -> Boolean,
  private val exportConfig: suspend () -> ConfigExportResult,
  private val importConfig: suspend (ConfigReadResult) -> Boolean,
  private val reportExportResult: (ConfigTransferResult) -> Unit,
  private val openOutput: (Uri) -> OutputStream?,
  private val openInput: (Uri) -> InputStream?,
  private val deleteExportedDocument: (Uri) -> Unit = {},
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
  private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) {
  private val exportLauncher: ActivityResultLauncher<String> =
    registry.register(
      EXPORT_KEY,
      ActivityResultContracts.CreateDocument(CONFIG_MIME_TYPE),
    ) { uri ->
      if (uri == null) {
        reportExportResult(ConfigTransferResult.Cancelled)
        return@register
      }
      scope.launch(ioDispatcher) {
        val result = runCatching {
          when (val encoded = exportConfig()) {
            is ConfigExportResult.PackageListTooLarge -> {
              runCatching { deleteExportedDocument(uri) }
              ConfigTransferResult.TooLarge
            }

            is ConfigExportResult.Success -> {
              val stream = openOutput(uri) ?: error("unable to open output stream")
              stream.use { it.write(encoded.json.toByteArray(Charsets.UTF_8)) }
              ConfigTransferResult.Saved
            }
          }
        }.getOrElse { ConfigTransferResult.Failed }
        withContext(mainDispatcher) { reportExportResult(result) }
      }
    }

  private val importLauncher: ActivityResultLauncher<Array<String>> =
    registry.register(
      IMPORT_KEY,
      ActivityResultContracts.OpenDocument(),
    ) { uri ->
      if (uri == null) return@register
      scope.launch(ioDispatcher) {
        val readResult = runCatching {
          val stream = openInput(uri) ?: error("unable to open input stream")
          stream.use { readBoundedUtf8(it, ConfigBackupCodec.MAX_BACKUP_BYTES) }
        }.getOrElse { ConfigReadResult.Failed }
        withContext(mainDispatcher) { importConfig(readResult) }
      }
    }

  fun launchExport() {
    if (!canExportNow()) {
      reportExportResult(ConfigTransferResult.TooLarge)
      return
    }
    runCatching {
      exportLauncher.launch(DEFAULT_CONFIG_FILE_NAME)
    }.onFailure {
      reportExportResult(ConfigTransferResult.Failed)
    }
  }

  fun launchImport() {
    runCatching {
      importLauncher.launch(IMPORT_CONFIG_MIME_TYPES)
    }.onFailure {
      scope.launch(mainDispatcher) { importConfig(ConfigReadResult.Failed) }
    }
  }

  companion object {
    const val EXPORT_KEY = "gazekeep_export_config"
    const val IMPORT_KEY = "gazekeep_import_config"
    const val CONFIG_MIME_TYPE = "application/json"
    const val DEFAULT_CONFIG_FILE_NAME = "gazekeep-config.json"
    val IMPORT_CONFIG_MIME_TYPES = arrayOf("application/json", "text/plain", "*/*")
    val retainedTransferScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  }
}

internal fun createAndroidConfigBackupCoordinator(
  activity: ComponentActivity,
  currentState: () -> ControllerState,
  store: ConfigStore = AppGraph(activity.applicationContext).settings,
): SafConfigBackupCoordinator {
  val appContext = activity.applicationContext
  return SafConfigBackupCoordinator(
    registry = activity.activityResultRegistry,
    scope = SafConfigBackupCoordinator.retainedTransferScope,
    canExportNow = {
      val state = currentState()
      !state.loaded || ConfigBackupCodec.encode(state.config) is ConfigExportResult.Success
    },
    exportConfig = { AndroidGazeKeepController.exportConfigJson(store) },
    importConfig = { readResult -> AndroidGazeKeepController.importConfigContent(appContext, store, readResult) },
    reportExportResult = AndroidGazeKeepController::reportConfigExportResult,
    openOutput = { uri ->
      runCatching { appContext.contentResolver.openOutputStream(uri, "wt") }.getOrNull()
        ?: appContext.contentResolver.openOutputStream(uri)
    },
    openInput = { uri -> appContext.contentResolver.openInputStream(uri) },
    deleteExportedDocument = { uri ->
      runCatching { DocumentsContract.deleteDocument(appContext.contentResolver, uri) }
    },
  )
}

class AndroidPlatformActions(
  private val activity: ComponentActivity,
  private val controller: AndroidGazeKeepController,
) : PlatformActions {
  @Volatile private var pendingExportText: String = ""

  // 界面显示偏好与 GazeConfig 分开保存：不参与门控、配置迁移与诊断转储。
  private val uiPrefs = activity.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
  private val exportLauncher: ActivityResultLauncher<String> =
    activity.activityResultRegistry.register(
      "gazekeep_export_logs",
      ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
      val content = pendingExportText
      if (uri != null && content.isNotEmpty()) {
        runCatching {
          activity.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
            writer.write(content)
          }
        }
      }
    }

  private val configBackupCoordinator = createAndroidConfigBackupCoordinator(
    activity = activity,
    currentState = { controller.state.value },
  )

  override fun requestCameraPermission() =
    ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.CAMERA), 1001)

  override fun requestNotificationPermission() {
    if (Build.VERSION.SDK_INT >= 33) {
      ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1002)
    }
  }

  override fun openAccessibilitySettings() {
    activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
  }

  override fun openAppDetailsSettings() {
    runCatching {
      activity.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null)),
      )
    }
  }

  override fun requestShizukuPermission() {
    runCatching { if (Shizuku.pingBinder()) Shizuku.requestPermission(2301) }
  }

  override fun openShizukuApp() {
    activity.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")?.let(activity::startActivity)
  }

  override fun openUrl(url: String) {
    runCatching {
      activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
  }

  override fun formatLogTimestamp(epochMillis: Long): String = formatLogTimestampUtc8(epochMillis)

  override fun appLanguage(): AppLanguage = AppLocales.current(activity)

  override fun setAppLanguage(language: AppLanguage) = AppLocales.set(activity, language)

  override fun showSystemApps(): Boolean = uiPrefs.getBoolean(KEY_SHOW_SYSTEM_APPS, false)

  override fun setShowSystemApps(show: Boolean) = uiPrefs.edit { putBoolean(KEY_SHOW_SYSTEM_APPS, show) }

  // Xiaomi 12 Pro / HyperOS OS2.0.6 实测保留直达入口；其余 ROM 家族统一引导至系统公开应用详情页。
  override fun romBackgroundPolicy(): RomBackgroundPolicy? = resolveRomBackgroundPolicy(Build.MANUFACTURER, Build.BRAND)

  override fun openRomBackgroundPolicy() {
    launchRomBackgroundPolicy(
      policy = romBackgroundPolicy(),
      packageName = activity.packageName,
      appLabel = activity.applicationInfo.loadLabel(activity.packageManager).toString(),
      startIntent = activity::startActivity,
      fallbackAppDetails = ::openAppDetailsSettings,
    )
  }

  override fun copyToClipboard(text: String) {
    if (text.isBlank()) return
    runCatching {
      activity.getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText("gazekeep_log", text))
    }
  }

  override fun shareLogs(text: String) {
    if (text.isBlank()) return
    runCatching {
      val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
      }
      activity.startActivity(Intent.createChooser(sendIntent, null))
    }
  }

  override fun exportLogs(text: String) {
    if (text.isBlank()) return
    pendingExportText = text
    runCatching {
      exportLauncher.launch("gazekeep-logs-${System.currentTimeMillis()}.txt")
    }
  }

  override fun supportsConfigBackup(): Boolean = true

  override fun exportConfig() = configBackupCoordinator.launchExport()

  override fun importConfig() = configBackupCoordinator.launchImport()

  @Composable override fun HandleBack(enabled: Boolean, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled, onBack)
  }

  @Composable override fun SelfTestPreview(modifier: Modifier) {
    AndroidView(modifier = modifier, factory = { context ->
      PreviewView(context).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        this@AndroidPlatformActions.controller.setPreviewSurface(surfaceProvider)
      }
    }, update = { controller.setPreviewSurface(it.surfaceProvider) })
    DisposableEffect(Unit) {
      onDispose {
        controller.setPreviewSurface(null)
      }
    }
  }

  @Composable override fun AppIcon(packageName: String, modifier: Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sizePx = remember(density) {
      with(density) { 40.dp.roundToPx() }.coerceAtLeast(96)
    }

    val cached = remember(packageName) { AppIconCache.get(packageName) }
    var imageBitmap by remember(packageName) { mutableStateOf(cached) }

    LaunchedEffect(packageName, sizePx) {
      if (imageBitmap == null) {
        val loaded = withContext(Dispatchers.IO) {
          AppIconCache.loadBitmap(context, packageName, sizePx)
        }
        imageBitmap = loaded
      }
    }

    val shape = RoundedCornerShape(8.dp)
    Box(
      modifier = modifier
        .clip(shape)
        .background(MiuixTheme.colorScheme.surfaceContainerHigh),
      contentAlignment = Alignment.Center,
    ) {
      val bitmap = imageBitmap
      if (bitmap != null) {
        Image(
          bitmap = bitmap,
          contentDescription = null,
          modifier = Modifier.fillMaxSize(),
        )
      }
    }
  }

  private companion object {
    const val UI_PREFS = "gazekeep_ui_prefs"
    const val KEY_SHOW_SYSTEM_APPS = "show_system_apps"
  }
}
