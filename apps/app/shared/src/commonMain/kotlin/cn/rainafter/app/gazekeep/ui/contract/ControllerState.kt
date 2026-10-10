package cn.rainafter.app.gazekeep.ui.contract

import cn.rainafter.app.gazekeep.core.model.Attention
import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.core.model.GazeConfig
import cn.rainafter.app.gazekeep.core.model.Readiness

enum class UiNotice { NeedsCamera, NeedsSelfTest, EmptyScope, SaveFailed }
enum class SelfTestStep { Looking, Away, Complete }
data class SelfTestState(
  val step: SelfTestStep = SelfTestStep.Looking,
  val running: Boolean = false,
  val attention: Attention? = null,
)
data class AppEntry(val packageName: String, val label: String)
data class SystemInfo(
  val appVersion: String = "v1.0 (1)",
  val androidVersion: String = "-",
  val deviceModel: String = "-",
  val systemFingerprint: String = "-",
)
data class ControllerState(
  val loaded: Boolean = false,
  val config: GazeConfig = GazeConfig(),
  val selfTestPassed: Boolean = false,
  val cameraGranted: Boolean = false,
  val backends: Map<BackendId, Readiness> = emptyMap(),
  val activeBackend: BackendId? = null,
  val serviceRunning: Boolean = false,
  val lastDecision: Attention? = null,
  val notice: UiNotice? = null,
  val apps: List<AppEntry> = emptyList(),
  val systemInfo: SystemInfo = SystemInfo(),
  val thirdPartyNotices: String = "",
)
