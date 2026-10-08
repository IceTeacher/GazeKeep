package cn.rainafter.app.gazekeep.core.policy

object ScreenDimmingPolicy {
    fun screenDimAt(lastActivity: Long, timeoutMs: Long, dimDurationMs: Long?): Long? {
        if (timeoutMs <= 0L || dimDurationMs == null || dimDurationMs !in 0L..timeoutMs) return null
        return lastActivity + timeoutMs - dimDurationMs
    }

    fun estimateDuration(timeoutMs: Long, maxDurationMs: Long?, maxRatio: Float?): Long? {
        if (timeoutMs <= 0L || maxDurationMs == null || maxDurationMs < 0L ||
            maxRatio == null || !maxRatio.isFinite() || maxRatio !in 0f..1f
        ) {
            return null
        }
        return minOf(maxDurationMs, (timeoutMs * maxRatio).toLong())
    }
}
