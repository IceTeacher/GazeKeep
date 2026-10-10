package cn.rainafter.app.gazekeep.xposed.entry

import android.content.Context
import android.util.Log
import cn.rainafter.app.gazekeep.xposed.hook.HookBeforeParam
import cn.rainafter.app.gazekeep.xposed.hook.HookFacade
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

/** 旧式 Xposed 入口；只处理 system_server，所有框架回调都隔离在 try/catch 内。 */
class LegacyEntry : IXposedHookLoadPackage {
  override fun handleLoadPackage(param: XC_LoadPackage.LoadPackageParam) {
    if (param.packageName != "android") return
    Log.i(TAG, "legacy_system_server_starting")
    runCatching {
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
      Log.e(TAG, "legacy_initialization_failed")
    }
  }

  private companion object {
    const val TAG = "GazeKeepXposed"
  }
}

private class XposedHookFacade : HookFacade {
  override fun hookBefore(method: java.lang.reflect.Method, callback: (HookBeforeParam) -> Unit) {
    XposedBridge.hookMethod(
      method,
      object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
          runCatching {
            callback(HookBeforeParam(param.args) { value -> param.setResult(value) })
          }.onFailure { XposedBridge.log(it) }
        }
      },
    )
  }

  override fun hookAfter(method: java.lang.reflect.Method, callback: (Array<Any?>, Any?) -> Unit) {
    XposedBridge.hookMethod(
      method,
      object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
          runCatching { callback(param.args, param.result) }.onFailure { XposedBridge.log(it) }
        }
      },
    )
  }
}
