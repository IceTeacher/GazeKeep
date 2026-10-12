package cn.rainafter.app.gazekeep.ui.contract

import cn.rainafter.app.gazekeep.core.model.Attention
import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.core.model.GazeConfig
import cn.rainafter.app.gazekeep.core.model.Readiness

enum class UiNotice { NeedsCamera, NeedsSelfTest, EmptyScope, SaveFailed }
enum class SelfTestStep { Looking, Away, Complete }
enum class LogLevel(val badge: String, val priority: Int) {
  Verbose("V", 2),
  Debug("D", 3),
  Info("I", 4),
  Warning("W", 5),
  Error("E", 6),
  Fatal("F", 7),
}

data class LogEntry(
  val id: String,
  val timestampMs: Long,
  val level: LogLevel,
  val tag: String,
  val source: String,
  val target: String,
  val message: String,
)

data class SelfTestState(
  val step: SelfTestStep = SelfTestStep.Looking,
  val running: Boolean = false,
  val attention: Attention? = null,
  val lastStepFailed: Boolean = false,
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
  val hideCameraIndicatorReady: Boolean = false,
  val activeBackend: BackendId? = null,
  val serviceRunning: Boolean = false,
  val lastDecision: Attention? = null,
  val notice: UiNotice? = null,
  val apps: List<AppEntry> = emptyList(),
  val logs: List<LogEntry> = emptyList(),
  val systemInfo: SystemInfo = SystemInfo(),
  val thirdPartyNotices: String = "",
)
