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
}
