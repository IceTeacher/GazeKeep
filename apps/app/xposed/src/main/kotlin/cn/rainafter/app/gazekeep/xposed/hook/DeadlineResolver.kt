package cn.rainafter.app.gazekeep.xposed.hook

import java.lang.reflect.Method

enum class HookCandidate { A_ATTENTION, C_INACTIVITY }

data class HookResolution(val candidate: HookCandidate?, val method: Method?, val tried: List<String>)

/** 只解析目标 ROM 的方法签名；真正的 Hook 适配器负责安装回调。 */
class DeadlineResolver internal constructor(private val resolveClass: (String) -> Class<*>) {
  constructor(classLoader: ClassLoader) : this({ name -> Class.forName(name, false, classLoader) })

  /** A 是主路径，仅 A 缺失时回退 C；未验证的 Summary 路径不参与安装。 */
  fun resolve(): HookResolution {
    val tried = listOf(
      "A_ATTENTION:com.android.server.power.AttentionDetector#updateUserActivity",
      "C_INACTIVITY:com.android.server.power.PowerManagerService#scheduleUserInactivityTimeout",
    )
    val attention = find(
      "com.android.server.power.AttentionDetector",
      "updateUserActivity",
      arrayOf(Long::class.javaPrimitiveType!!, Long::class.javaPrimitiveType!!),
      Long::class.javaPrimitiveType!!,
    )
    if (attention != null) return HookResolution(HookCandidate.A_ATTENTION, attention, tried)
    val inactivity = find(
      "com.android.server.power.PowerManagerService",
      "scheduleUserInactivityTimeout",
      arrayOf(Long::class.javaPrimitiveType!!),
      Void.TYPE,
    )
    return HookResolution(inactivity?.let { HookCandidate.C_INACTIVITY }, inactivity, tried)
  }

  fun resolveBridge(): Method? = runCatching {
    val clazz = resolveClass("android.os.IPowerManager\$Stub")
    clazz.declaredMethods.firstOrNull { method ->
      matchesHookSignature(
        method,
        "onTransact",
        arrayOf<Class<*>>(
          Int::class.javaPrimitiveType!!,
          android.os.Parcel::class.java,
          android.os.Parcel::class.java,
          Int::class.javaPrimitiveType!!,
        ),
        Boolean::class.javaPrimitiveType!!,
      )
    }
  }.getOrNull()

  private fun find(
    className: String,
    methodName: String,
    parameterTypes: Array<Class<*>>,
    returnType: Class<*>,
  ): Method? = runCatching {
    val clazz = resolveClass(className)
    clazz.declaredMethods.firstOrNull { method ->
      matchesHookSignature(method, methodName, parameterTypes, returnType)
    }
  }.getOrNull()
}

internal fun matchesHookSignature(
  method: Method,
  name: String,
  parameters: Array<Class<*>>,
  returnType: Class<*>,
): Boolean = method.name == name && method.returnType == returnType &&
  method.parameterTypes.contentEquals(parameters)
