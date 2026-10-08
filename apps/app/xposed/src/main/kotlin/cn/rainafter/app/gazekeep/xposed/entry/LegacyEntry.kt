package cn.rainafter.app.gazekeep.xposed.entry

import android.content.Context
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
    runCatching {
      val context = SystemContextResolver.resolve(param.classLoader)
      if (context == null) {
        return
      }
      HookBootstrap.onSystemServerLoaded(context, param.classLoader, XposedHookFacade())
    }.onFailure {
      XposedBridge.log(it)
    }
  }
}

private object SystemContextResolver {
  fun resolve(classLoader: ClassLoader): Context? = runCatching {
    val activityThread = Class.forName("android.app.ActivityThread", false, classLoader)
    val thread = activityThread.getDeclaredMethod("currentActivityThread").invoke(null) ?: return null
    activityThread.getDeclaredMethod("getSystemContext").invoke(thread) as? Context
  }.getOrNull()
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
