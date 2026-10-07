package cn.rainafter.app.gazekeep.xposed.hook

import java.util.concurrent.atomic.AtomicInteger

internal class HookCallbackGuard(
    private val onFailure: (Throwable) -> Unit,
    private val onDisabled: () -> Unit,
) {
    private val failures = AtomicInteger(0)

    fun run(callback: () -> Unit) {
        if (failures.get() >= FAILURE_LIMIT) return
        try {
            callback()
        } catch (error: Throwable) {
            val count = failures.incrementAndGet()
            // 日志或清理自身失败也不能穿透 system_server 回调。
            runCatching { onFailure(error) }
            if (count == FAILURE_LIMIT) runCatching { onDisabled() }
        }
    }

    private companion object {
        const val FAILURE_LIMIT = 5
    }
}
