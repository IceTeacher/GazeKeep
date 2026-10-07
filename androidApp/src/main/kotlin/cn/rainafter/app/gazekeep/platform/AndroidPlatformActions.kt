package cn.rainafter.app.gazekeep.platform

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import cn.rainafter.app.gazekeep.controller.AndroidGazeKeepController
import cn.rainafter.app.gazekeep.apps.AppIconCache
import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import cn.rainafter.app.gazekeep.ui.contract.PlatformActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import top.yukonga.miuix.kmp.theme.MiuixTheme

class AndroidPlatformActions(private val activity: ComponentActivity, private val controller: AndroidGazeKeepController) : PlatformActions {
    override fun requestCameraPermission() = ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.CAMERA), 1001)
    override fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1002)
    }
    override fun openAccessibilitySettings() { activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    override fun requestShizukuPermission() { runCatching { if (Shizuku.pingBinder()) Shizuku.requestPermission(2301) } }
    override fun openShizukuApp() {
        activity.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")?.let(activity::startActivity)
    }
    @Composable override fun HandleBack(enabled: Boolean, onBack: () -> Unit) { androidx.activity.compose.BackHandler(enabled, onBack) }
    @Composable override fun SelfTestPreview(modifier: Modifier) {
        AndroidView(modifier = modifier, factory = { context ->
            PreviewView(context).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                this@AndroidPlatformActions.controller.setPreviewSurface(surfaceProvider)
            }
        }, update = { controller.setPreviewSurface(it.surfaceProvider) })
        DisposableEffect(Unit) { onDispose { controller.setPreviewSurface(null); controller.cancelSelfTest() } }
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
}
