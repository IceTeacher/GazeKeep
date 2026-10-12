package cn.rainafter.app.gazekeep.core.policy

import cn.rainafter.app.gazekeep.core.model.Attention
import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.core.model.BackendLock
import cn.rainafter.app.gazekeep.core.model.GazeConfig
import cn.rainafter.app.gazekeep.core.model.Readiness

data class SelfTestProgress(val lookingPassed: Boolean = false, val awayPassed: Boolean = false) {
  val complete: Boolean get() = lookingPassed && awayPassed
}
fun advanceSelfTest(progress: SelfTestProgress, attention: Attention): SelfTestProgress = when {
  !progress.lookingPassed && attention == Attention.Looking -> progress.copy(lookingPassed = true)
  progress.lookingPassed && attention == Attention.Away -> progress.copy(awayPassed = true)
  else -> progress
}

enum class EnableRequirement { Camera, SelfTest, Ready }
fun enableRequirement(cameraGranted: Boolean, selfTestPassed: Boolean): EnableRequirement = when {
  !cameraGranted -> EnableRequirement.Camera
  !selfTestPassed -> EnableRequirement.SelfTest
  else -> EnableRequirement.Ready
}

fun canConfigureHideCameraIndicator(
  activeBackend: BackendId?,
  backendLock: BackendLock,
  backends: Map<BackendId, Readiness>,
): Boolean {
  if (backendLock != BackendLock.Auto && backendLock != BackendLock.Lsposed) return false
  return if (activeBackend != null) {
    activeBackend == BackendId.Lsposed
  } else {
    backends[BackendId.Lsposed] == Readiness.Ready
  }
}

fun shouldSyncHideCameraIndicator(
  config: GazeConfig,
  activeBackend: BackendId?,
  backends: Map<BackendId, Readiness>,
): Boolean = config.hideCameraIndicator &&
  canConfigureHideCameraIndicator(activeBackend, config.backendLock, backends)
