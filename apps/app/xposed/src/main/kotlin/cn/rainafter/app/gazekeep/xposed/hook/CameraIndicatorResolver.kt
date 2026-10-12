package cn.rainafter.app.gazekeep.xposed.hook

import java.lang.reflect.Method

enum class IndicatorCandidate {
  SCHEDULE_ACTIVE_V34,
  SCHEDULE_ACTIVE_V31,
  SCHEDULE_ACTIVE_LEGACY,
  NOTIFY_ACTIVE_V34,
  NOTIFY_ACTIVE_V31,
  NOTIFY_ACTIVE_LEGACY,
}

data class ActiveChangedTarget(val candidate: IndicatorCandidate, val method: Method, val activeArgIndex: Int)

data class CameraIndicatorHookPlan(
  val activeChanged: ActiveChangedTarget?,
  val operationActiveMethod: Method?,
  val tried: List<String>,
)

/** 独立解析 AppOpsService 相机活跃通知签名；未命中时不阻断电源超时主链路。 */
class CameraIndicatorResolver(private val classLoader: ClassLoader) {
  fun resolve(): CameraIndicatorHookPlan {
    val clazz = runCatching {
      Class.forName(APP_OPS_SERVICE_CLASS, false, classLoader)
    }.getOrNull() ?: return CameraIndicatorHookPlan(null, null, triedCandidates())
    return resolveFromClass(clazz)
  }

  companion object {
    const val OP_CAMERA = 26
    private const val APP_OPS_SERVICE_CLASS = "com.android.server.appop.AppOpsService"

    fun resolveFromClass(clazz: Class<*>): CameraIndicatorHookPlan {
      val intType = Int::class.javaPrimitiveType!!
      val boolType = Boolean::class.javaPrimitiveType!!
      val stringType = String::class.java
      val arraySetType = android.util.ArraySet::class.java
      val v34Params = arrayOf<Class<*>>(
        intType,
        intType,
        stringType,
        stringType,
        intType,
        boolType,
        intType,
        intType,
      )
      val v31Params = arrayOf<Class<*>>(
        intType,
        intType,
        stringType,
        stringType,
        boolType,
        intType,
        intType,
      )
      val legacyParams = arrayOf<Class<*>>(
        intType,
        intType,
        stringType,
        boolType,
      )
      val scheduleCandidates = listOf(
        Triple(
          IndicatorCandidate.SCHEDULE_ACTIVE_V34,
          "scheduleOpActiveChangedIfNeededLocked",
          v34Params to 5,
        ),
        Triple(
          IndicatorCandidate.SCHEDULE_ACTIVE_V31,
          "scheduleOpActiveChangedIfNeededLocked",
          v31Params to 4,
        ),
        Triple(
          IndicatorCandidate.SCHEDULE_ACTIVE_LEGACY,
          "scheduleOpActiveChangedIfNeededLocked",
          legacyParams to 3,
        ),
      )
      val notifyCandidates = listOf(
        Triple(
          IndicatorCandidate.NOTIFY_ACTIVE_V34,
          "notifyOpActiveChanged",
          arrayOf(arraySetType, *v34Params) to 6,
        ),
        Triple(
          IndicatorCandidate.NOTIFY_ACTIVE_V31,
          "notifyOpActiveChanged",
          arrayOf(arraySetType, *v31Params) to 5,
        ),
        Triple(
          IndicatorCandidate.NOTIFY_ACTIVE_LEGACY,
          "notifyOpActiveChanged",
          arrayOf(arraySetType, *legacyParams) to 4,
        ),
      )
      val activeChanged = (scheduleCandidates + notifyCandidates)
        .firstNotNullOfOrNull { (candidate, methodName, spec) ->
          val (params, activeIndex) = spec
          findMethod(clazz, methodName, params, Void.TYPE)?.let { method ->
            ActiveChangedTarget(candidate, method, activeIndex)
          }
        }
      val operationActiveMethod = findMethod(
        clazz,
        "isOperationActive",
        arrayOf(intType, intType, stringType),
        boolType,
      )
      return CameraIndicatorHookPlan(activeChanged, operationActiveMethod, triedCandidates())
    }

    private fun findMethod(
      clazz: Class<*>,
      methodName: String,
      parameterTypes: Array<Class<*>>,
      returnType: Class<*>,
    ): Method? = runCatching {
      clazz.declaredMethods.firstOrNull { method ->
        matchesHookSignature(method, methodName, parameterTypes, returnType)
      }
    }.getOrNull()

    private fun triedCandidates(): List<String> = listOf(
      "SCHEDULE_ACTIVE_V34:$APP_OPS_SERVICE_CLASS#scheduleOpActiveChangedIfNeededLocked(int,int,String,String,int,boolean,int,int)",
      "SCHEDULE_ACTIVE_V31:$APP_OPS_SERVICE_CLASS#scheduleOpActiveChangedIfNeededLocked(int,int,String,String,boolean,int,int)",
      "SCHEDULE_ACTIVE_LEGACY:$APP_OPS_SERVICE_CLASS#scheduleOpActiveChangedIfNeededLocked(int,int,String,boolean)",
      "NOTIFY_ACTIVE_V34:$APP_OPS_SERVICE_CLASS#notifyOpActiveChanged(int,int,String,String,int,boolean,int,int)",
      "NOTIFY_ACTIVE_V31:$APP_OPS_SERVICE_CLASS#notifyOpActiveChanged(int,int,String,String,boolean,int,int)",
      "NOTIFY_ACTIVE_LEGACY:$APP_OPS_SERVICE_CLASS#notifyOpActiveChanged(int,int,String,boolean)",
      "IS_OPERATION_ACTIVE:$APP_OPS_SERVICE_CLASS#isOperationActive(int,int,String)",
    )
  }
}

/** 仅在开关启用、操作码为 OP_CAMERA、active 为 true 且目标属于 GazeKeep 时拦截通知。 */
internal fun shouldSuppressCameraActiveNotification(
  args: Array<Any?>,
  activeArgIndex: Int,
  hideEnabled: Boolean,
  isTargetApp: (Int, String?) -> Boolean,
): Boolean {
  if (!hideEnabled) return false
  val code = args.getOrNull(0) as? Int ?: return false
  if (code != CameraIndicatorResolver.OP_CAMERA) return false
  val uid = args.getOrNull(1) as? Int ?: return false
  val packageName = args.getOrNull(2) as? String
  val active = args.getOrNull(activeArgIndex) as? Boolean ?: return false
  if (!active) return false
  return isTargetApp(uid, packageName)
}

/** 仅在开关启用、操作码为 OP_CAMERA 且目标属于 GazeKeep 时将活跃查询覆盖为 false。 */
internal fun shouldOverrideCameraOperationActive(
  args: Array<Any?>,
  hideEnabled: Boolean,
  isTargetApp: (Int, String?) -> Boolean,
): Boolean {
  if (!hideEnabled) return false
  val code = args.getOrNull(0) as? Int ?: return false
  if (code != CameraIndicatorResolver.OP_CAMERA) return false
  val uid = args.getOrNull(1) as? Int ?: return false
  val packageName = args.getOrNull(2) as? String
  return isTargetApp(uid, packageName)
}
