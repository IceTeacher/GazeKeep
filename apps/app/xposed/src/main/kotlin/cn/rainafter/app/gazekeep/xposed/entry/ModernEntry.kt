package cn.rainafter.app.gazekeep.xposed.entry

import cn.rainafter.app.gazekeep.xposed.hook.HookBeforeParam
import cn.rainafter.app.gazekeep.xposed.hook.HookFacade
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Method

/** 现代入口与旧入口共享薄钩子和一次安装保护。 */
class ModernEntry() : XposedModule() {
  constructor(
    base: XposedInterface,
    param: XposedModuleInterface.ModuleLoadedParam,
  ) : this()

  override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
    log(android.util.Log.INFO, TAG, "system_server_starting")
    runCatching {
      val loader = param.classLoader
      val facade = ModernHookFacade(this)
      val context = SystemContextResolver.resolve(loader)
      if (context != null) {
        HookBootstrap.onSystemServerLoaded(context, loader, facade)
      } else {
        // systemMain 之前 Context 尚未创建，等 framework 初始化完成再安装。
        val thread = Class.forName("android.app.ActivityThread", false, loader)
        facade.hookAfter(thread.getDeclaredMethod("systemMain")) { _, _ ->
          SystemContextResolver.resolve(loader)?.let { HookBootstrap.onSystemServerLoaded(it, loader, facade) }
        }
      }
    }.onFailure { log(android.util.Log.WARN, TAG, "modern_initialization_failed") }
  }

  private companion object {
    const val TAG = "GazeKeepXposed"
  }
}

private class ModernHookFacade(private val module: XposedModule) : HookFacade {
  override fun hookBefore(method: Method, callback: (HookBeforeParam) -> Unit) {
    module.hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept { chain ->
      var replaced = false
      var result: Any? = null
      val succeeded = runCatching {
        callback(
          HookBeforeParam(chain.args.toTypedArray()) {
            replaced = true
            result = it
          },
        )
      }.isSuccess
      if (succeeded && replaced) result else chain.proceed()
    }
  }

  override fun hookAfter(method: Method, callback: (Array<Any?>, Any?) -> Unit) {
    module.hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept { chain ->
      val result = chain.proceed()
      runCatching { callback(chain.args.toTypedArray(), result) }
      result
    }
  }
}
