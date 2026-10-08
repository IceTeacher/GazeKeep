package cn.rainafter.app.gazekeep.core.policy

import cn.rainafter.app.gazekeep.core.model.Attention

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
