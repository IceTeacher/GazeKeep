package cn.rainafter.app.gazekeep.platform

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import androidx.lifecycle.lifecycleScope
import cn.rainafter.app.gazekeep.applist.AppIconCache
import cn.rainafter.app.gazekeep.controller.AndroidGazeKeepController
import cn.rainafter.app.gazekeep.core.model.ConfigBackupCodec
import cn.rainafter.app.gazekeep.ui.contract.AppLanguage
import cn.rainafter.app.gazekeep.ui.contract.ConfigReadResult
import cn.rainafter.app.gazekeep.ui.contract.ConfigTransferResult
import cn.rainafter.app.gazekeep.ui.contract.GazeKeepController
import cn.rainafter.app.gazekeep.ui.contract.PlatformActions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
 * 无易失回调状态的 SAF 配置备份协调器：
 * 即使在系统文件选择器打开期间 Activity 旋转或重建，新实例按固定 key 重新注册 launcher 后，
 * 仍可直接从 [controller] 实时编码导出或将读取内容合入配置并发布跨重建反馈。
 */
internal class SafConfigBackupCoordinator(
  registry: ActivityResultRegistry,
  private val scope: CoroutineScope,
  private val controller: GazeKeepController,
  private val openOutput: (Uri) -> OutputStream?,
  private val openInput: (Uri) -> InputStream?,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
  private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) {
  private val exportLauncher: ActivityResultLauncher<String> =
    registry.register(
      EXPORT_KEY,
      ActivityResultContracts.CreateDocument(CONFIG_MIME_TYPE),
    ) { uri ->
      if (uri == null) {
        controller.reportConfigExportResult(ConfigTransferResult.Cancelled)
        return@register
      }
      scope.launch(ioDispatcher) {
        val result = runCatching {
          val content = controller.exportConfigJson()
          val stream = openOutput(uri) ?: error("unable to open output stream")
          stream.use { it.write(content.toByteArray(Charsets.UTF_8)) }
        }.fold(
          onSuccess = { ConfigTransferResult.Saved },
          onFailure = { ConfigTransferResult.Failed },
        )
        withContext(mainDispatcher) { controller.reportConfigExportResult(result) }
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
        withContext(mainDispatcher) { controller.importConfigContent(readResult) }
      }
    }

  fun launchExport() {
    runCatching {
      exportLauncher.launch(DEFAULT_CONFIG_FILE_NAME)
    }.onFailure {
      controller.reportConfigExportResult(ConfigTransferResult.Failed)
    }
  }

  fun launchImport() {
    runCatching {
      importLauncher.launch(IMPORT_CONFIG_MIME_TYPES)
    }.onFailure {
      scope.launch(mainDispatcher) { controller.importConfigContent(ConfigReadResult.Failed) }
    }
  }

  companion object {
    const val EXPORT_KEY = "gazekeep_export_config"
    const val IMPORT_KEY = "gazekeep_import_config"
    const val CONFIG_MIME_TYPE = "application/json"
    const val DEFAULT_CONFIG_FILE_NAME = "gazekeep-config.json"
    val IMPORT_CONFIG_MIME_TYPES = arrayOf("application/json", "text/plain", "*/*")
  }
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

  private val configBackupCoordinator = SafConfigBackupCoordinator(
    registry = activity.activityResultRegistry,
    scope = activity.lifecycleScope,
    controller = controller,
    openOutput = { uri ->
      runCatching { activity.contentResolver.openOutputStream(uri, "wt") }.getOrNull()
        ?: activity.contentResolver.openOutputStream(uri)
    },
    openInput = { uri -> activity.contentResolver.openInputStream(uri) },
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

  // Xiaomi 12 Pro / HyperOS OS2.0.6 实测：默认「智能」策略会在系统相机使用后冻结前台服务进程，只有「无限制」可避免。
  override fun hasRomBackgroundPolicy(): Boolean = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)

  override fun openRomBackgroundPolicy() {
    val policy = Intent()
      .setClassName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
      .putExtra("package_name", activity.packageName)
      .putExtra("package_label", activity.applicationInfo.loadLabel(activity.packageManager).toString())
    // 省电策略页属于 ROM 私有入口，不可用时退回应用详情页，由用户手动进入省电策略。
    runCatching { activity.startActivity(policy) }.onFailure {
      openAppDetailsSettings()
    }
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
