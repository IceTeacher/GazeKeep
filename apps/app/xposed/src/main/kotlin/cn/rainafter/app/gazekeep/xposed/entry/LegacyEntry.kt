package cn.rainafter.app.gazekeep.xposed.entry

import android.util.Log
import cn.rainafter.app.gazekeep.xposed.hook.HookBeforeParam
import cn.rainafter.app.gazekeep.xposed.hook.HookFacade
import cn.rainafter.app.gazekeep.xposed.hook.HookRegistration
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

/** 旧式 Xposed 入口；只处理 system_server，所有框架回调都隔离在 try/catch 内。 */
class LegacyEntry : IXposedHookLoadPackage {
  override fun handleLoadPackage(param: XC_LoadPackage.LoadPackageParam) {
    if (param.packageName != "android" || param.processName != "android") return
    runCatching {
      Log.i(TAG, "entry=legacy startup=Accepted")
      val context = SystemContextResolver.resolve(param.classLoader)
      val facade = XposedHookFacade()
      if (context != null) {
        HookBootstrap.onSystemServerLoaded(context, param.classLoader, facade)
      } else {
        Log.w(TAG, "system_context_null_waiting_for_system_main")
        val thread = Class.forName("android.app.ActivityThread", false, param.classLoader)
        facade.hookAfter(thread.getDeclaredMethod("systemMain")) { _, _ ->
          SystemContextResolver.resolve(param.classLoader)?.let {
            HookBootstrap.onSystemServerLoaded(it, param.classLoader, facade)
          }
        }
      }
    }.onFailure {
      runCatching { Log.e(TAG, "entry=legacy startup=InstallationFailed") }
    }
  }

  private companion object {
    const val TAG = "GazeKeepXposed"
  }
}

private class XposedHookFacade : HookFacade {
  override fun hookBefore(method: java.lang.reflect.Method, callback: (HookBeforeParam) -> Unit): HookRegistration {
    val handle = XposedBridge.hookMethod(
      method,
      object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
          runCatching {
            callback(HookBeforeParam(param.args) { value -> param.setResult(value) })
          }.onFailure { error -> runCatching { XposedBridge.log(error) } }
        }
      },
    )
    return HookRegistration { handle.unhook() }
  }

  override fun hookAfter(method: java.lang.reflect.Method, callback: (Array<Any?>, Any?) -> Unit): HookRegistration {
    val handle = XposedBridge.hookMethod(
      method,
      object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
          runCatching {
            callback(param.args, param.result)
          }.onFailure { error -> runCatching { XposedBridge.log(error) } }
        }
      },
    )
    return HookRegistration { handle.unhook() }
  }
}
