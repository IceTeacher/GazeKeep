package cn.rainafter.app.gazekeep.ui.testing

import cn.rainafter.app.gazekeep.core.model.ConfigBackupCodec
import cn.rainafter.app.gazekeep.core.model.ConfigExportResult
import cn.rainafter.app.gazekeep.core.model.ConfigImportResult
import cn.rainafter.app.gazekeep.core.model.GazeConfig
import cn.rainafter.app.gazekeep.ui.contract.ConfigBackupFeedback
import cn.rainafter.app.gazekeep.ui.contract.ConfigBackupNotice
import cn.rainafter.app.gazekeep.ui.contract.ConfigReadResult
import cn.rainafter.app.gazekeep.ui.contract.ConfigTransferResult
import cn.rainafter.app.gazekeep.ui.contract.ControllerState
import cn.rainafter.app.gazekeep.ui.contract.GazeKeepController
import cn.rainafter.app.gazekeep.ui.contract.SelfTestState
import kotlinx.coroutines.flow.MutableStateFlow

/** 只记录页面发出的意图并按需改写状态；不连接相机、引擎或后端。 */
class FakeGazeKeepController(initial: ControllerState, selfTest: SelfTestState = SelfTestState()) : GazeKeepController {
  override val state = MutableStateFlow(initial)
  override val selfTest = MutableStateFlow(selfTest)
  val calls = mutableListOf<String>()
  private var nextBackupNoticeToken = 0L

  /** 自检采集结果由测试决定，模拟控制器在一次采集后的状态推进。 */
  var onCapture: (SelfTestState) -> SelfTestState = { it }

  /** 配置写入是否成功；失败时页面应保留可重试状态。 */
  var saveSucceeds = true

  override suspend fun updateConfig(transform: (GazeConfig) -> GazeConfig) {
    calls += "updateConfig"
    if (saveSucceeds) state.value = state.value.copy(config = transform(state.value.config))
  }

  override suspend fun exportConfigJson(): ConfigExportResult {
    calls += "exportConfigJson"
    return ConfigBackupCodec.encode(state.value.config)
  }

  override suspend fun importConfigContent(readResult: ConfigReadResult): Boolean {
    calls += "importConfigContent"
    return when (readResult) {
      ConfigReadResult.Cancelled -> false

      ConfigReadResult.TooLarge -> {
        emitBackupFeedback(ConfigBackupFeedback.ImportTooLarge)
        false
      }

      ConfigReadResult.Failed -> {
        emitBackupFeedback(ConfigBackupFeedback.ImportFailed)
        false
      }

      is ConfigReadResult.Loaded -> {
        val current = state.value
        when (val decoded = ConfigBackupCodec.decode(readResult.content, current.config)) {
          is ConfigImportResult.InvalidFormat -> {
            emitBackupFeedback(ConfigBackupFeedback.ImportInvalid)
            false
          }

          is ConfigImportResult.Success -> {
            if (!saveSucceeds) {
              emitBackupFeedback(ConfigBackupFeedback.ImportFailed)
              return false
            }
            val merged = decoded.config.copy(enabled = if (current.selfTestPassed) current.config.enabled else false)
            state.value = current.copy(config = merged)
            emitBackupFeedback(ConfigBackupFeedback.Imported)
            true
          }
        }
      }
    }
  }

  override fun reportConfigExportResult(result: ConfigTransferResult) {
    calls += "reportConfigExportResult:$result"
    when (result) {
      ConfigTransferResult.Saved -> emitBackupFeedback(ConfigBackupFeedback.Exported)
      ConfigTransferResult.TooLarge -> emitBackupFeedback(ConfigBackupFeedback.ExportTooLarge)
      ConfigTransferResult.Failed -> emitBackupFeedback(ConfigBackupFeedback.ExportFailed)
      ConfigTransferResult.Cancelled -> Unit
    }
  }

  override fun clearBackupNotice(token: Long) {
    val current = state.value
    if (current.backupNotice?.token == token) {
      state.value = current.copy(backupNotice = null)
    }
  }

  private fun emitBackupFeedback(feedback: ConfigBackupFeedback) {
    state.value = state.value.copy(backupNotice = ConfigBackupNotice(feedback, ++nextBackupNoticeToken))
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
