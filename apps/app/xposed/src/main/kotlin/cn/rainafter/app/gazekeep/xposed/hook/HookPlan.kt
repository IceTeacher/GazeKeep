package cn.rainafter.app.gazekeep.xposed.hook

import android.os.Binder
import android.os.Parcel
import android.util.Log
import cn.rainafter.app.gazekeep.xposed.power.PowerHookCore

data class HookInstallResult(
    val resolution: HookResolution,
    val installed: Boolean,
)

/** Hook 框架适配层的窄接口；签名解析失败时不安装任何候选。 */
interface HookFacade {
    fun hookBefore(method: java.lang.reflect.Method, callback: (HookBeforeParam) -> Unit)
    fun hookAfter(method: java.lang.reflect.Method, callback: (Array<Any?>, Any?) -> Unit)
}

class HookBeforeParam(
    val args: Array<Any?>,
    private val resultWriter: (Any?) -> Unit,
) {
    fun setResult(value: Any?) = resultWriter(value)
}

object HookPlan {
    fun install(facade: HookFacade, classLoader: ClassLoader, core: PowerHookCore): HookInstallResult {
        val resolver = DeadlineResolver(classLoader)
        val resolutions = resolver.resolveAll()
        val resolution = resolutions.firstOrNull()?.let { (candidate, method) ->
            HookResolution(candidate, method, triedCandidates())
        } ?: HookResolution(null, null, triedCandidates())
        if (resolutions.none { it.first == HookCandidate.A_ATTENTION || it.first == HookCandidate.C_INACTIVITY }) {
            return HookInstallResult(resolution, installed = false)
        }
        val bridgeMethod = resolver.resolveBridge()
            ?: return HookInstallResult(resolution, installed = false)

        val deadlineGuard = HookCallbackGuard(
            onFailure = { Log.w("GazeKeepXposed", "deadline hook failed", it) },
            onDisabled = core::disable,
        )
        val bridgeGuard = HookCallbackGuard(
            onFailure = { Log.w("GazeKeepXposed", "bridge hook failed", it) },
            onDisabled = core::disable,
        )
        // A 是目标 ROM 的主路径；只有 A 不存在时才启用 C，避免两个候选同时刷新 seq。
        val selected = resolutions.firstOrNull { it.first == HookCandidate.A_ATTENTION }
            ?: resolutions.firstOrNull { it.first == HookCandidate.C_INACTIVITY }
        if (selected != null) {
            when (selected.first) {
                HookCandidate.A_ATTENTION -> facade.hookAfter(selected.second) { args, _ ->
                    deadlineGuard.run {
                        val nextDimming = args.firstOrNull() as? Long ?: return@run
                        val dimDuration = args.getOrNull(1) as? Long ?: 0L
                        core.onDeadlineComputed(nextDimming + dimDuration, -1L, nextDimming)
                    }
                }

                HookCandidate.C_INACTIVITY -> facade.hookBefore(selected.second) { param ->
                    deadlineGuard.run {
                        val timeout = param.args.firstOrNull() as? Long ?: return@run
                        core.onDeadlineComputed(timeout, -1L)
                    }
                }

                HookCandidate.B_SUMMARY -> Unit
            }
        }
        facade.hookBefore(bridgeMethod) { param ->
            bridgeGuard.run {
                val code = param.args.getOrNull(0) as? Int ?: return@run
                val data = param.args.getOrNull(1) as? Parcel ?: return@run
                val reply = param.args.getOrNull(2) as? Parcel ?: return@run
                if (core.handleTransaction(code, data, reply, Binder.getCallingUid())) {
                    param.setResult(true)
                }
            }
        }
        return HookInstallResult(resolution, installed = true)
    }

    private fun triedCandidates(): List<String> = listOf(
        "A_ATTENTION:com.android.server.power.AttentionDetector#updateUserActivity",
        "B_SUMMARY:com.android.server.power.PowerManagerService#updateUserActivitySummaryLocked",
        "C_INACTIVITY:com.android.server.power.PowerManagerService#scheduleUserInactivityTimeout",
    )
}
