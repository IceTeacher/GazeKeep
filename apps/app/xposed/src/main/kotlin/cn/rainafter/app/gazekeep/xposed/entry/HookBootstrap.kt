package cn.rainafter.app.gazekeep.xposed.entry

import android.content.Context
import cn.rainafter.app.gazekeep.xposed.hook.HookFacade
import cn.rainafter.app.gazekeep.xposed.hook.HookPlan
import cn.rainafter.app.gazekeep.xposed.power.PowerHookCore

/** 隔离框架入口，保证一个 system_server 只安装一次钩子。 */
object HookBootstrap {
    @Volatile private var activeCore: PowerHookCore? = null

    @JvmStatic
    fun onSystemServerLoaded(systemContext: Context, classLoader: ClassLoader, facade: HookFacade): PowerHookCore? {
        synchronized(this) {
            activeCore?.let { return it }
            val core = PowerHookCore(systemContext)
            val result = runCatching { HookPlan.install(facade, classLoader, core) }.getOrNull()
            if (result?.installed != true) {
                core.close()
                return null
            }
            activeCore = core
            return core
        }
    }
}
