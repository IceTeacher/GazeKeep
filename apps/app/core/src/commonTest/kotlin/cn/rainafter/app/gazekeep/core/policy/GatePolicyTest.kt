package cn.rainafter.app.gazekeep.core.policy

import cn.rainafter.app.gazekeep.core.model.AppFilterMode
import cn.rainafter.app.gazekeep.core.model.GazeConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class GatePolicyTest {
  private val reader = "com.example.reader"
  private val video = "com.example.video"
  private val camera = "com.android.camera"

  private fun input(foreground: String?, interactive: Boolean = true, keyguardLocked: Boolean = false) = GateInput(
    interactive = interactive,
    keyguardLocked = keyguardLocked,
    foregroundPackage = foreground,
    audioMode = 0,
    frontCameraAvailable = true,
    proximityNear = false,
    lux = 100f,
    faceDown = false,
  )

  private fun config(mode: AppFilterMode, whitelist: Set<String> = emptySet(), blacklist: Set<String> = emptySet()) =
    GazeConfig(appFilterMode = mode, whitelistPackages = whitelist, blacklistPackages = blacklist)

  private fun decide(input: GateInput, config: GazeConfig) = GatePolicy.decide(input, GateContext(), config)

  private fun skip(reason: SkipReason) = GateDecision.Skip(reason)

  @Test fun allAppsProceedsForAnyForegroundIncludingUnknown() {
    val all = config(AppFilterMode.All, whitelist = setOf(reader), blacklist = setOf(video))
    assertEquals(GateDecision.Proceed, decide(input(reader), all))
    assertEquals(GateDecision.Proceed, decide(input(video), all))
    assertEquals(GateDecision.Proceed, decide(input(null), all))
  }

  @Test fun whitelistProceedsOnlyForKnownListedForeground() {
    val whitelist = config(AppFilterMode.Whitelist, whitelist = setOf(reader), blacklist = setOf(video))
    assertEquals(GateDecision.Proceed, decide(input(reader), whitelist))
    assertEquals(skip(SkipReason.NotWhitelisted), decide(input(video), whitelist))
    assertEquals(skip(SkipReason.ForegroundUnknown), decide(input(null), whitelist))
  }

  @Test fun emptyWhitelistNeverProceeds() {
    assertEquals(skip(SkipReason.NotWhitelisted), decide(input(reader), config(AppFilterMode.Whitelist)))
  }

  @Test fun blacklistSkipsListedAndUnknownForeground() {
    val blacklist = config(AppFilterMode.Blacklist, whitelist = setOf(video), blacklist = setOf(video))
    assertEquals(skip(SkipReason.Blacklisted), decide(input(video), blacklist))
    assertEquals(GateDecision.Proceed, decide(input(reader), blacklist))
    // 无法确认前台不在黑名单内时不放行。
    assertEquals(skip(SkipReason.ForegroundUnknown), decide(input(null), blacklist))
  }

  @Test fun emptyBlacklistBehavesLikeAllAppsForKnownForeground() {
    assertEquals(GateDecision.Proceed, decide(input(reader), config(AppFilterMode.Blacklist)))
  }

  @Test fun knownCameraAppIsSkippedInEveryScope() {
    assertEquals(skip(SkipReason.CameraSensitiveApp), decide(input(camera), config(AppFilterMode.All)))
    assertEquals(
      skip(SkipReason.CameraSensitiveApp),
      decide(input(camera), config(AppFilterMode.Whitelist, whitelist = setOf(camera))),
    )
    assertEquals(skip(SkipReason.CameraSensitiveApp), decide(input(camera), config(AppFilterMode.Blacklist)))
  }

  @Test fun screenAndLockChecksPrecedeScope() {
    for (mode in AppFilterMode.entries) {
      val scoped = config(mode, whitelist = setOf(reader))
      assertEquals(skip(SkipReason.ScreenOff), decide(input(null, interactive = false), scoped))
      assertEquals(skip(SkipReason.Locked), decide(input(null, keyguardLocked = true), scoped))
    }
  }

  @Test fun scopeChecksPrecedeEnvironmentChecks() {
    val inCall = input(video).copy(audioMode = 2)
    assertEquals(
      skip(SkipReason.Blacklisted),
      decide(inCall, config(AppFilterMode.Blacklist, blacklist = setOf(video))),
    )
    assertEquals(skip(SkipReason.InCall), decide(inCall, config(AppFilterMode.All)))
  }

  // 运行器先以空传感器读数判定，放行后才打开传感器窗口；缺失读数绝不能导致跳过。
  @Test fun missingSensorReadingsNeverSkip() {
    val noSensors = input(reader).copy(proximityNear = null, lux = null, faceDown = null)
    assertEquals(
      GateDecision.Proceed,
      GatePolicy.decide(noSensors, GateContext(darkUnknownStreak = 5), config(AppFilterMode.All)),
    )
  }

  @Test fun powerSaveSkipsWhenEnabledAndPrecedesScope() {
    val cfg = config(AppFilterMode.Whitelist).copy(pauseOnPowerSave = true)
    assertEquals(skip(SkipReason.PowerSaveMode), decide(input(null).copy(powerSaveMode = true), cfg))
    assertEquals(
      skip(SkipReason.ScreenOff),
      decide(input(reader, interactive = false).copy(powerSaveMode = true), cfg),
    )
    assertEquals(
      skip(SkipReason.Locked),
      decide(input(reader, keyguardLocked = true).copy(powerSaveMode = true), cfg),
    )
    assertEquals(
      GateDecision.Proceed,
      decide(
        input(reader).copy(powerSaveMode = true),
        config(AppFilterMode.Whitelist, whitelist = setOf(reader)).copy(pauseOnPowerSave = false),
      ),
    )
  }

  @Test fun lowBatterySkipsUnpluggedAtOrBelowThreshold() {
    val cfg = config(AppFilterMode.All).copy(pauseOnPowerSave = false, lowBatteryThreshold = 15)
    assertEquals(skip(SkipReason.LowBattery), decide(input(reader).copy(batteryPercent = 15, isCharging = false), cfg))
    assertEquals(skip(SkipReason.LowBattery), decide(input(reader).copy(batteryPercent = 5, isCharging = false), cfg))
    assertEquals(GateDecision.Proceed, decide(input(reader).copy(batteryPercent = 16, isCharging = false), cfg))
    assertEquals(GateDecision.Proceed, decide(input(reader).copy(batteryPercent = 5, isCharging = true), cfg))
    assertEquals(GateDecision.Proceed, decide(input(reader).copy(batteryPercent = null, isCharging = false), cfg))
    assertEquals(
      GateDecision.Proceed,
      decide(input(reader).copy(batteryPercent = 5, isCharging = false), cfg.copy(lowBatteryThreshold = 0)),
    )
    assertEquals(
      skip(SkipReason.PowerSaveMode),
      decide(
        input(reader).copy(powerSaveMode = true, batteryPercent = 5, isCharging = false),
        cfg.copy(pauseOnPowerSave = true),
      ),
    )
  }

  @Test fun oemCameraAppsAreSkipped() {
    val oemCameras = listOf(
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
      "com.sonyericsson.android.camera",
    )
    for (pkg in oemCameras) {
      assertEquals(skip(SkipReason.CameraSensitiveApp), decide(input(pkg), config(AppFilterMode.All)), pkg)
    }
  }

  @Test fun faceDownGateRespectsToggleWhileKeepingPocketAndDarkFallbacks() {
    val defaultCfg = config(AppFilterMode.All)
    assertEquals(skip(SkipReason.FaceDown), decide(input(reader).copy(faceDown = true), defaultCfg))

    val relaxedFaceDown = defaultCfg.copy(faceDownGateEnabled = false)
    assertEquals(GateDecision.Proceed, decide(input(reader).copy(faceDown = true), relaxedFaceDown))
    assertEquals(
      skip(SkipReason.Pocket),
      decide(input(reader).copy(faceDown = true, proximityNear = true), relaxedFaceDown),
    )
    assertEquals(
      skip(SkipReason.TooDarkRepeatedly),
      GatePolicy.decide(
        input(reader).copy(faceDown = true, lux = 1f),
        GateContext(darkUnknownStreak = 2),
        relaxedFaceDown,
      ),
    )
  }
}
