package cn.rainafter.app.gazekeep.core.backend

import cn.rainafter.app.gazekeep.core.model.BackendId
import kotlinx.coroutines.flow.Flow

interface TimeoutOracle {
  val source: OracleSource
  val deadlines: Flow<TimeoutDeadline>
  fun millisUntilScreenOff(): Long?
}

enum class OracleSource { System, PowerDump, IdleClock }

data class TimeoutDeadline(
  val screenOffAtUptime: Long,
  val token: ExtendToken = ExtendToken.None,
  val foregroundPackage: String? = null,
  val lastExternalActivityUptime: Long? = null,
  val backend: BackendId? = null,
  val screenDimAtUptime: Long? = null,
)

fun TimeoutDeadline.captureWakeAtUptime(leadMillis: Long): Long {
  val checkAt = screenDimAtUptime?.takeIf { it > 0L && it <= screenOffAtUptime }
    ?: screenOffAtUptime
  return checkAt - leadMillis
}
