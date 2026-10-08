package cn.rainafter.app.gazekeep.app

import android.content.Context
import cn.rainafter.app.gazekeep.controller.AndroidGazeKeepController
import cn.rainafter.app.gazekeep.core.model.GazeConfig
import cn.rainafter.app.gazekeep.engine.config.ConfigStore
import cn.rainafter.app.gazekeep.notification.GazeNotifier
import cn.rainafter.app.gazekeep.runtime.BackendSession
import kotlinx.coroutines.CoroutineScope

class AppGraph(context: Context) {
  private val context = context.applicationContext
  val settings = ConfigStore(this.context)
  fun createController() = AndroidGazeKeepController(context, settings)
  fun createNotifier() = GazeNotifier(context)
  fun createSession(scope: CoroutineScope, config: GazeConfig, notifier: GazeNotifier) =
    BackendSession(context, scope, config, notifier)
}
