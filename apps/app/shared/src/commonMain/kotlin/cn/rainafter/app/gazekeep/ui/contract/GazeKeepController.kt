package cn.rainafter.app.gazekeep.ui.contract

import cn.rainafter.app.gazekeep.core.model.GazeConfig
import kotlinx.coroutines.flow.StateFlow

interface GazeKeepController {
  val state: StateFlow<ControllerState>
  val selfTest: StateFlow<SelfTestState>
  suspend fun updateConfig(transform: (GazeConfig) -> GazeConfig)
  suspend fun setEnabled(enabled: Boolean)
  suspend fun beginSelfTest()
  suspend fun captureSelfTest()
  fun cancelSelfTest()
  fun refresh()
  suspend fun loadApps()
}
