package cn.rainafter.app.gazekeep.xposed.entry

import cn.rainafter.app.gazekeep.xposed.hook.HookBeforeParam
import cn.rainafter.app.gazekeep.xposed.hook.HookFacade
import cn.rainafter.app.gazekeep.xposed.hook.HookRegistration
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Method

/** 现代入口与旧入口共享薄钩子和一次安装保护。 */
class ModernEntry : XposedModule() {
  private val startup = ModernStartup()

  override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
    runCatching {
      report(startup.load(apiVersion, frameworkProperties, param.isSystemServer))
    }.onFailure { report(ModernStartupResult.NotAuthorized) }
  }

  override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
    runCatching {
      report(
        startup.start(
          resolve = {
            val loader = param.classLoader
            SystemContextResolver.resolve(loader)?.let { it to loader }
          },
          install = { (context, loader) ->
            HookBootstrap.onSystemServerLoaded(context, loader, ModernHookFacade(this)) != null
          },
        ),
      )
    }.onFailure { report(ModernStartupResult.InstallationFailed) }
  }

  override fun onHotReloading(param: XposedModuleInterface.HotReloadingParam): Boolean = false

  private fun report(result: ModernStartupResult) {
    runCatching { log(android.util.Log.INFO, TAG, "entry=modern_102 startup=${result.name}") }
  }

  private companion object {
    const val TAG = "GazeKeepXposed"
  }
}

internal class ModernHookFacade(private val module: XposedModule) : HookFacade {
  override fun hookBefore(method: Method, callback: (HookBeforeParam) -> Unit): HookRegistration {
    val handle = module.hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept { chain ->
      var replaced = false
      var result: Any? = null
      var args: Array<Any?> = emptyArray()
      val succeeded = runCatching {
        args = chain.args.toTypedArray()
        callback(
          HookBeforeParam(args) {
            replaced = true
            result = it
          },
        )
      }.isSuccess
      when {
        !succeeded -> chain.proceed()
        replaced -> result
        else -> chain.proceed(args)
      }
    }
    return HookRegistration { handle.unhook() }
  }

  override fun hookAfter(method: Method, callback: (Array<Any?>, Any?) -> Unit): HookRegistration {
    val handle = module.hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept { chain ->
      val result = chain.proceed()
      runCatching { callback(chain.args.toTypedArray(), result) }
      result
    }
    return HookRegistration { handle.unhook() }
  }
}
