package cn.rainafter.app.gazekeep.xposed.entry

import android.content.Context
import android.util.Log
import cn.rainafter.app.gazekeep.xposed.hook.HookFacade
import cn.rainafter.app.gazekeep.xposed.hook.HookPlan
import cn.rainafter.app.gazekeep.xposed.hook.HookRegistrations
import cn.rainafter.app.gazekeep.xposed.hook.TrackingHookFacade
import cn.rainafter.app.gazekeep.xposed.power.PowerHookCore

/** 隔离框架入口，保证一个 system_server 只尝试一次系统钩子安装。 */
object HookBootstrap {
  private val session = BootstrapSession<PowerHookCore>()

  @JvmStatic
  fun onSystemServerLoaded(systemContext: Context, classLoader: ClassLoader, facade: HookFacade): PowerHookCore? {
    val registrations = HookRegistrations()
    val core = session.start(
      create = { PowerHookCore(systemContext) },
      install = { core ->
        val result = HookPlan.install(TrackingHookFacade(facade, registrations), classLoader, core)
        result.installed
      },
      close = PowerHookCore::close,
      rollback = registrations::rollback,
    )
    runCatching { Log.i(TAG, "hook_startup=${if (core != null) "Installed" else "InstallationFailed"}") }
    return core
  }

  private const val TAG = "GazeKeepXposed"
}
