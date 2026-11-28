package cn.rainafter.app.gazekeep.core.policy

import cn.rainafter.app.gazekeep.core.model.AppFilterMode
import cn.rainafter.app.gazekeep.core.model.GazeConfig

data class GateInput(
  val interactive: Boolean,
  val keyguardLocked: Boolean,
  val foregroundPackage: String?,
  val audioMode: Int,
  val frontCameraAvailable: Boolean,
  val proximityNear: Boolean?,
  val lux: Float?,
  val faceDown: Boolean?,
  val powerSaveMode: Boolean = false,
  val batteryPercent: Int? = null,
  val isCharging: Boolean = false,
)

data class GateContext(val darkUnknownStreak: Int = 0)

sealed interface GateDecision {
  data object Proceed : GateDecision
  data class Skip(val reason: SkipReason) : GateDecision
}

enum class SkipReason {
  ScreenOff,
  Locked,
  PowerSaveMode,
  LowBattery,
  NotWhitelisted,
  Blacklisted,
  ForegroundUnknown,
  InCall,
  CameraSensitiveApp,
  CameraBusy,
  Pocket,
  FaceDown,
  TooDarkRepeatedly,
}

object GatePolicy {
  private val cameraPackages = setOf(
    "com.android.camera",
    "com.android.camera2",
    "com.google.android.GoogleCamera",
    "com.miui.camera",
    "com.oplus.camera",
    "com.oppo.camera",
    "com.oneplus.camera",
    "com.vivo.camera",
    "com.sec.android.app.camera",
    "com.huawei.camera",
    "com.hihonor.camera",
    "com.nothing.camera",
    "com.meizu.media.camera",
    "com.asus.camera",
    "com.motorola.camera3",
    "com.motorola.cameraone",
    "com.sonyericsson.android.camera",
    "com.sonymobile.photopro",
  )

  fun decide(input: GateInput, context: GateContext, config: GazeConfig): GateDecision {
    if (!input.interactive) return GateDecision.Skip(SkipReason.ScreenOff)
    if (input.keyguardLocked) return GateDecision.Skip(SkipReason.Locked)
    if (config.pauseOnPowerSave && input.powerSaveMode) {
      return GateDecision.Skip(SkipReason.PowerSaveMode)
    }
    if (config.lowBatteryThreshold > 0 &&
      !input.isCharging &&
      input.batteryPercent != null &&
      input.batteryPercent <= config.lowBatteryThreshold
    ) {
      return GateDecision.Skip(SkipReason.LowBattery)
    }
    val packageName = input.foregroundPackage
    // 白名单与黑名单都必须先确认前台：无法确认不在黑名单内时同样不放行。
    if (packageName == null && config.appFilterMode != AppFilterMode.All) {
      return GateDecision.Skip(SkipReason.ForegroundUnknown)
    }
    when (config.appFilterMode) {
      AppFilterMode.All -> Unit

      AppFilterMode.Whitelist ->
        if (packageName !in config.whitelistPackages) return GateDecision.Skip(SkipReason.NotWhitelisted)

      AppFilterMode.Blacklist ->
        if (packageName in config.blacklistPackages) return GateDecision.Skip(SkipReason.Blacklisted)
    }
    if (packageName in cameraPackages) return GateDecision.Skip(SkipReason.CameraSensitiveApp)
    if (input.audioMode in setOf(2, 3, 5)) return GateDecision.Skip(SkipReason.InCall)
    if (!input.frontCameraAvailable) {
      return GateDecision.Skip(SkipReason.CameraBusy)
    }
    if (input.proximityNear == true) return GateDecision.Skip(SkipReason.Pocket)
    if (config.faceDownGateEnabled && input.faceDown == true) return GateDecision.Skip(SkipReason.FaceDown)
    if (context.darkUnknownStreak >= 2 && input.lux != null && input.lux < 3f) {
      return GateDecision.Skip(SkipReason.TooDarkRepeatedly)
    }
    return GateDecision.Proceed
  }
}
