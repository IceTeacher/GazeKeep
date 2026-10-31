package cn.rainafter.app.gazekeep.core.model

data class GazeConfig(
  val enabled: Boolean = false,
  val backendLock: BackendLock = BackendLock.Auto,
  val leadMillis: Long = 8_000L,
  val sensitivity: Sensitivity = Sensitivity.Standard,
  val appFilterMode: AppFilterMode = AppFilterMode.Whitelist,
  val whitelistPackages: Set<String> = emptySet(),
  val blacklistPackages: Set<String> = emptySet(),
  val maxHoldMinutes: Int = 30,
  val debugLogEnabled: Boolean = false,
  val hideCameraIndicator: Boolean = false,
) {
  init {
    require(leadMillis >= 0L)
    require(maxHoldMinutes >= 0)
  }
}

/** 生效范围；两份名单分开保存，切换范围不互相覆盖。 */
enum class AppFilterMode { All, Whitelist, Blacklist }

/** 当前范围编辑的名单；全部应用不使用名单，回落到白名单以免误改黑名单。 */
val GazeConfig.scopedPackages: Set<String>
  get() = if (appFilterMode == AppFilterMode.Blacklist) blacklistPackages else whitelistPackages

fun GazeConfig.withScopedPackages(packages: Set<String>): GazeConfig = if (appFilterMode == AppFilterMode.Blacklist) {
  copy(blacklistPackages = packages)
} else {
  copy(whitelistPackages = packages)
}

enum class BackendId { Lsposed, Shizuku, Accessibility }
enum class BackendLock { Auto, Lsposed, Shizuku, Accessibility }

sealed interface Readiness {
  data object Ready : Readiness
  data class NeedsSetup(val step: SetupStep) : Readiness
  data class Unavailable(val cause: String) : Readiness
}

enum class SetupStep {
  InstallFramework,
  EnableModuleScope,
  Reboot,
  StartShizuku,
  GrantShizukuPermission,
  EnableAccessibilityService,
}
