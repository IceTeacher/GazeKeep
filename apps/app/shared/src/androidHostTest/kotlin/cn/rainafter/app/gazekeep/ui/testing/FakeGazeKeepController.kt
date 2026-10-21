package cn.rainafter.app.gazekeep.ui.testing

import cn.rainafter.app.gazekeep.core.model.GazeConfig
import cn.rainafter.app.gazekeep.ui.contract.ControllerState
import cn.rainafter.app.gazekeep.ui.contract.GazeKeepController
import cn.rainafter.app.gazekeep.ui.contract.SelfTestState
import kotlinx.coroutines.flow.MutableStateFlow

/** 只记录页面发出的意图并按需改写状态；不连接相机、引擎或后端。 */
class FakeGazeKeepController(initial: ControllerState, selfTest: SelfTestState = SelfTestState()) : GazeKeepController {
  override val state = MutableStateFlow(initial)
  override val selfTest = MutableStateFlow(selfTest)
  val calls = mutableListOf<String>()

  /** 自检采集结果由测试决定，模拟控制器在一次采集后的状态推进。 */
  var onCapture: (SelfTestState) -> SelfTestState = { it }

  /** 配置写入是否成功；失败时页面应保留可重试状态。 */
  var saveSucceeds = true

  override suspend fun updateConfig(transform: (GazeConfig) -> GazeConfig) {
    calls += "updateConfig"
    if (saveSucceeds) state.value = state.value.copy(config = transform(state.value.config))
  }

  override suspend fun setEnabled(enabled: Boolean) {
    calls += "setEnabled:$enabled"
    state.value = state.value.copy(config = state.value.config.copy(enabled = enabled))
  }

  override suspend fun beginSelfTest() {
    calls += "beginSelfTest"
  }

  override suspend fun captureSelfTest() {
    calls += "captureSelfTest"
    selfTest.value = onCapture(selfTest.value)
  }

  override fun cancelSelfTest() {
    calls += "cancelSelfTest"
  }

  override fun clearLogs() {
    calls += "clearLogs"
    state.value = state.value.copy(logs = emptyList())
  }

  override fun refreshLogs() {
    calls += "refreshLogs"
  }

  override fun refresh() {
    calls += "refresh"
  }

  override suspend fun loadApps() {
    calls += "loadApps"
  }
}
