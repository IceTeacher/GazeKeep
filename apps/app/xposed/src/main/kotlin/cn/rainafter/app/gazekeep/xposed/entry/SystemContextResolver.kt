package cn.rainafter.app.gazekeep.xposed.entry

import android.app.ActivityThread
import android.content.Context
import android.util.Log

/** 编译存根不进入 APK；失配仍按未就绪处理。 */
internal object SystemContextResolver {
  fun resolve(classLoader: ClassLoader): Context? {
    runCatching {
      val thread = ActivityThread.currentActivityThread()
      val context = thread?.systemContext
      if (context != null) {
        Log.i(TAG, "system_context_resolved path=activity_thread")
        return context
      }
    }.onFailure {
      Log.w(TAG, "system_context_resolve_failed path=activity_thread")
    }

    runCatching {
      val atClass = Class.forName("android.app.ActivityThread", false, classLoader)
      val currentThread = atClass.getDeclaredMethod("currentActivityThread").invoke(null)
      if (currentThread != null) {
        val getSystemContext = atClass.getDeclaredMethod("getSystemContext")
        val context = getSystemContext.invoke(currentThread) as? Context
        if (context != null) {
          Log.i(TAG, "system_context_resolved path=reflection")
          return context
        }
      }
    }.onFailure {
      Log.w(TAG, "system_context_resolve_failed path=reflection")
    }

    runCatching {
      val atClass = Class.forName("android.app.ActivityThread", false, classLoader)
      val currentApp = atClass.getDeclaredMethod("currentApplication").invoke(null) as? Context
      if (currentApp != null) {
        Log.i(TAG, "system_context_resolved path=current_application")
        return currentApp
      }
    }.onFailure {
      Log.w(TAG, "system_context_resolve_failed path=current_application")
    }

    return null
  }

  private const val TAG = "GazeKeepXposed"
}
