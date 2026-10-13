package cn.rainafter.app.gazekeep.xposed.entry

internal enum class ModernStartupResult {
  Accepted,
  UnsupportedApi,
  MissingSystemCapability,
  WrongProcess,
  NotAuthorized,
  AlreadyAttempted,
  ContextUnavailable,
  InstallationFailed,
  Installed,
}

/** 初始化不重试：失败后等待重启，避免在系统进程留下多代钩子。 */
internal class ModernStartup {
  private var loaded = false
  private var accepted = false
  private var attempted = false

  @Synchronized
  fun load(apiVersion: Int, properties: Long, isSystemServer: Boolean): ModernStartupResult {
    if (loaded) return ModernStartupResult.AlreadyAttempted
    loaded = true
    val result = when {
      apiVersion < 102 -> ModernStartupResult.UnsupportedApi
      properties and 1L == 0L -> ModernStartupResult.MissingSystemCapability
      !isSystemServer -> ModernStartupResult.WrongProcess
      else -> ModernStartupResult.Accepted
    }
    accepted = result == ModernStartupResult.Accepted
    return result
  }

  @Synchronized
  fun <T : Any> start(resolve: () -> T?, install: (T) -> Boolean): ModernStartupResult {
    if (!accepted) return ModernStartupResult.NotAuthorized
    if (attempted) return ModernStartupResult.AlreadyAttempted
    attempted = true
    val context = runCatching(resolve).getOrNull() ?: return ModernStartupResult.ContextUnavailable
    return if (runCatching { install(context) }.getOrDefault(false)) {
      ModernStartupResult.Installed
    } else {
      ModernStartupResult.InstallationFailed
    }
  }
}
