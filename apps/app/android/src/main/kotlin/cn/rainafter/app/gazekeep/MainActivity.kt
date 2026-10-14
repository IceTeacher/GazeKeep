package cn.rainafter.app.gazekeep

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import cn.rainafter.app.gazekeep.app.AppGraph
import cn.rainafter.app.gazekeep.controller.AndroidGazeKeepController
import cn.rainafter.app.gazekeep.platform.AndroidPlatformActions
import cn.rainafter.app.gazekeep.ui.App

class MainActivity : ComponentActivity() {
  private lateinit var controller: AndroidGazeKeepController
  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    controller = AppGraph(applicationContext).createController()
    val actions = AndroidPlatformActions(this, controller)
    setContent { App(controller, actions) }
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
