package cn.rainafter.app.gazekeep

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStarted
import cn.rainafter.app.gazekeep.app.AppGraph
import cn.rainafter.app.gazekeep.controller.AndroidGazeKeepController
import cn.rainafter.app.gazekeep.platform.AndroidPlatformActions
import cn.rainafter.app.gazekeep.platform.AppLocales
import cn.rainafter.app.gazekeep.service.GazeQuickTileService
import cn.rainafter.app.gazekeep.ui.App
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  private lateinit var controller: AndroidGazeKeepController
  override fun attachBaseContext(newBase: Context) {
    super.attachBaseContext(AppLocales.wrap(newBase))
  }
  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    AppLocales.syncDefault(this)
  }
  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    controller = AppGraph(applicationContext).createController()
    handleTileEnableIntent(intent)
    val actions = AndroidPlatformActions(this, controller)
    setContent { App(controller, actions) }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleTileEnableIntent(intent)
  }

  private fun handleTileEnableIntent(intent: Intent?) {
    if (intent?.action != GazeQuickTileService.ACTION_ENABLE_FROM_TILE) return
    intent.action = null
    if (::controller.isInitialized) {
      lifecycleScope.launch {
        lifecycle.withStarted {}
        controller.setEnabled(true)
      }
    }
  }
  override fun onResume() {
    super.onResume()
    if (::controller.isInitialized) {
      controller.resumeSelfTest()
      controller.refresh()
    }
  }
  override fun onPause() {
    if (::controller.isInitialized) controller.pauseSelfTest()
    super.onPause()
  }
  override fun onDestroy() {
    if (::controller.isInitialized) controller.close()
    super.onDestroy()
  }
  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    controller.refresh()
  }
}
