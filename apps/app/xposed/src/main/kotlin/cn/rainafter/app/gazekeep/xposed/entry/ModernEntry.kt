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
    val handle = module.hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
      .intercept(BeforeHooker(callback))
    return HookRegistration { handle.unhook() }
  }

  override fun hookAfter(method: Method, callback: (Array<Any?>, Any?) -> Unit): HookRegistration {
    val handle = module.hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
      .intercept(AfterHooker(callback))
    return HookRegistration { handle.unhook() }
  }

  // 框架从 APK 外调用；具名类受 entry 保留规则保护，避免 SAM 合成回调被 R8 裁剪。
  private class BeforeHooker(private val callback: (HookBeforeParam) -> Unit) : XposedInterface.Hooker {
    override fun intercept(chain: XposedInterface.Chain): Any? {
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
      return when {
        !succeeded -> chain.proceed()
        replaced -> result
        else -> chain.proceed(args)
      }
    }
  }

  private class AfterHooker(private val callback: (Array<Any?>, Any?) -> Unit) : XposedInterface.Hooker {
    override fun intercept(chain: XposedInterface.Chain): Any? {
      val result = chain.proceed()
      runCatching { callback(chain.args.toTypedArray(), result) }
      return result
    }
  }
}
