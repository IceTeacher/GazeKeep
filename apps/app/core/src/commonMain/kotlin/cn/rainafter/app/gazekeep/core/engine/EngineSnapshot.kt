package cn.rainafter.app.gazekeep.core.engine

import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.core.model.GazeConfig

data class EngineContext(
  val holdStartUptime: Long? = null,
  val lastExternalActivityUptime: Long? = null,
  val lookingStreak: Int = 0,
  val darkUnknownStreak: Int = 0,
  val forcedYieldPending: Boolean = false,
  val extendFailures: Int = 0,
  val foregroundPackage: String? = null,
  val captureLatencyMs: Long? = null,
  /** 最近一次门控跳过对应的熄屏截止时间；同一截止周期内的微小抖动不再重新门控。 */
  val skippedScreenOffAtUptime: Long? = null,
)

data class EngineSnapshot(
  val state: EngineState = EngineState.Disabled,
  val context: EngineContext = EngineContext(),
  val config: GazeConfig = GazeConfig(),
  val backend: BackendId? = null,
  val cameraPermissionGranted: Boolean = true,
)

data class Transition(val snapshot: EngineSnapshot, val effects: List<EngineEffect> = emptyList())
