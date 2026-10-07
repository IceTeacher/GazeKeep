package cn.rainafter.app.gazekeep.xposed.hook

import java.lang.reflect.Method

enum class HookCandidate { A_ATTENTION, B_SUMMARY, C_INACTIVITY }

data class HookResolution(
    val candidate: HookCandidate?,
    val method: Method?,
    val tried: List<String>,
)

/** 只解析目标 ROM 的方法签名；真正的 Hook 适配器负责安装回调。 */
class DeadlineResolver(private val classLoader: ClassLoader) {
    fun resolve(): HookResolution {
        val first = resolveAll().firstOrNull()
        return HookResolution(first?.first, first?.second, triedCandidates())
    }

    /** 同一 ROM 可能只在其中一个路径重算超时，因此把可用候选全部交给安装器。 */
    fun resolveAll(): List<Pair<HookCandidate, Method>> {
        val resolved = ArrayList<Pair<HookCandidate, Method>>(3)
        find(
            "com.android.server.power.AttentionDetector",
            "updateUserActivity",
            arrayOf(Long::class.javaPrimitiveType!!, Long::class.javaPrimitiveType!!),
            Long::class.javaPrimitiveType!!,
        )?.let { resolved.add(HookCandidate.A_ATTENTION to it) }
        find(
            "com.android.server.power.PowerManagerService",
            "updateUserActivitySummaryLocked",
            arrayOf(Long::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!),
            Void.TYPE,
        )?.let { resolved.add(HookCandidate.B_SUMMARY to it) }
        find(
            "com.android.server.power.PowerManagerService",
            "scheduleUserInactivityTimeout",
            arrayOf(Long::class.javaPrimitiveType!!),
            Void.TYPE,
        )?.let { resolved.add(HookCandidate.C_INACTIVITY to it) }
        return resolved
    }

    fun resolveBridge(): Method? = runCatching {
        val clazz = Class.forName("android.os.IPowerManager\$Stub", false, classLoader)
        clazz.declaredMethods.firstOrNull { method ->
            method.name == "onTransact" &&
                method.returnType == Boolean::class.javaPrimitiveType &&
                method.parameterTypes.contentEquals(
                    arrayOf(
                        Int::class.javaPrimitiveType,
                        android.os.Parcel::class.java,
                        android.os.Parcel::class.java,
                        Int::class.javaPrimitiveType,
                    ),
                )
        }
    }.getOrNull()

    private fun triedCandidates(): List<String> = listOf(
        "A_ATTENTION:com.android.server.power.AttentionDetector#updateUserActivity",
        "B_SUMMARY:com.android.server.power.PowerManagerService#updateUserActivitySummaryLocked",
        "C_INACTIVITY:com.android.server.power.PowerManagerService#scheduleUserInactivityTimeout",
    )

    private fun find(
        className: String,
        methodName: String,
        parameterTypes: Array<Class<*>>,
        returnType: Class<*>,
    ): Method? = runCatching {
        val clazz = Class.forName(className, false, classLoader)
        clazz.declaredMethods.firstOrNull { method ->
            method.name == methodName &&
                method.returnType == returnType &&
                method.parameterTypes.contentEquals(parameterTypes)
        }
    }.getOrNull()
}
