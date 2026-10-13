package cn.rainafter.app.gazekeep.core.model

data class GazeConfig(
  val enabled: Boolean = false,
  val backendLock: BackendLock = BackendLock.Auto,
  val leadMillis: Long = 8_000L,
  val sensitivity: Sensitivity = Sensitivity.Standard,
  val whitelistEnabled: Boolean = true,
  val packages: Set<String> = emptySet(),
  val maxHoldMinutes: Int = 30,
  val debugLogEnabled: Boolean = false,
  val hideCameraIndicator: Boolean = false,
) {
  init {
    require(leadMillis >= 0L)
    require(maxHoldMinutes >= 0)
  }
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
