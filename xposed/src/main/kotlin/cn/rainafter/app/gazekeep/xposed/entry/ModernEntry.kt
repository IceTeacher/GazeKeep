package cn.rainafter.app.gazekeep.xposed.entry

import android.content.Context
import cn.rainafter.app.gazekeep.xposed.core.PowerHookCore
import cn.rainafter.app.gazekeep.xposed.hook.HookFacade
import cn.rainafter.app.gazekeep.xposed.hook.HookPlan

/** 现代入口的框架无关适配点；真正的 libxposed 入口只负责转发到这里。 */
object ModernEntry {
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
