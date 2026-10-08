package cn.rainafter.app.gazekeep.xposed.power

internal fun preTimeoutAt(screenOffAt: Long, screenDimAt: Long, leadMillis: Long, now: Long): Long {
    val checkAt = screenDimAt.takeIf { it > 0L && it <= screenOffAt } ?: screenOffAt
    return (checkAt - leadMillis).coerceAtLeast(now)
}
