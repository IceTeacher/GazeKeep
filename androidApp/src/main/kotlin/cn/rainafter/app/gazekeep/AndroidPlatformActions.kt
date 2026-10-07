package cn.rainafter.app.gazekeep

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import cn.rainafter.app.gazekeep.ui.PlatformActions
import rikka.shizuku.Shizuku

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
}
