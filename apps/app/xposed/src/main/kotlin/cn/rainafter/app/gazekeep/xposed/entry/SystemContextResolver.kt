package cn.rainafter.app.gazekeep.xposed.entry

import android.app.ActivityThread
import android.content.Context

/** 编译存根不进入 APK；失配仍按未就绪处理。 */
internal object SystemContextResolver {
  fun resolve(classLoader: ClassLoader): Context? = runCatching {
    ActivityThread.currentActivityThread()?.systemContext
  }.getOrNull()
}
