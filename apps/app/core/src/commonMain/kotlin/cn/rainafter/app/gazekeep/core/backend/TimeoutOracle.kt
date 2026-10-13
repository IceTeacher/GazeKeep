package cn.rainafter.app.gazekeep.core.backend

import cn.rainafter.app.gazekeep.core.model.BackendId
import kotlinx.coroutines.flow.Flow

interface TimeoutOracle {
  val deadlines: Flow<TimeoutDeadline>
}

data class TimeoutDeadline(
  val screenOffAtUptime: Long,
  val token: ExtendToken = ExtendToken.None,
  val foregroundPackage: String? = null,
  val lastExternalActivityUptime: Long? = null,
  val backend: BackendId? = null,
  val screenDimAtUptime: Long? = null,
)

const val MIN_CAPTURE_WINDOW_MS = 2_500L

fun TimeoutDeadline.captureWakeAtUptime(leadMillis: Long): Long {
  val checkAt = screenDimAtUptime?.takeIf { it > 0L && it <= screenOffAtUptime }
    ?: screenOffAtUptime
  return minOf(checkAt - leadMillis, screenOffAtUptime - MIN_CAPTURE_WINDOW_MS)
}
