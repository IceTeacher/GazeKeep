package cn.rainafter.app.gazekeep.xposed.power

private const val MIN_CAPTURE_WINDOW_MS = 2_500L

internal fun preTimeoutAt(screenOffAt: Long, screenDimAt: Long, leadMillis: Long, now: Long): Long {
  val checkAt = screenDimAt.takeIf { it > 0L && it <= screenOffAt } ?: screenOffAt
  val target = minOf(checkAt - leadMillis, screenOffAt - MIN_CAPTURE_WINDOW_MS)
  return target.coerceAtLeast(now)
}
