package cn.rainafter.app.gazekeep.backend.shizuku.oracle

import android.os.SystemClock
import cn.rainafter.app.gazekeep.backend.shizuku.protocol.PowerSnapshot
import cn.rainafter.app.gazekeep.core.backend.ExtendToken
import cn.rainafter.app.gazekeep.core.backend.OracleSource
import cn.rainafter.app.gazekeep.core.backend.TimeoutDeadline
import cn.rainafter.app.gazekeep.core.backend.TimeoutOracle
import cn.rainafter.app.gazekeep.core.backend.captureWakeAtUptime
import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.core.policy.ScreenDimmingPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

/** 通过 shell 侧解析后的 power 状态追踪系统熄屏 deadline。 */
class PowerDumpOracle(
  private val readPower: suspend () -> PowerSnapshot?,
  private val scope: CoroutineScope,
  private val clock: () -> Long = { SystemClock.uptimeMillis() },
  private val leadMs: Long = 2_000L,
  private val retryMs: Long = 1_000L,
) : TimeoutOracle,
  AutoCloseable {
  private val _deadlines = MutableSharedFlow<TimeoutDeadline>(replay = 1, extraBufferCapacity = 1)
  private var pollJob: Job? = null
  private var lastDeadline: TimeoutDeadline? = null

  override val source: OracleSource = OracleSource.PowerDump
  override val deadlines: Flow<TimeoutDeadline> = _deadlines.asSharedFlow()

  fun start() {
    if (pollJob?.isActive == true) return
    pollJob = scope.launch(Dispatchers.Default) {
      while (isActive) {
        val snapshot = runCatching { readPower() }.getOrNull()
        val waitMs = publish(snapshot)
        delay(waitMs)
      }
    }
  }

  /** 外部活动或触摸发生后立即重新读取一次，避免等完本轮轮询。 */
  fun requestRead() {
    if (pollJob?.isActive != true) return
    pollJob?.cancel()
    start()
  }

  override fun millisUntilScreenOff(): Long? = lastDeadline?.let { deadline ->
    (deadline.screenOffAtUptime - clock()).coerceAtLeast(0L)
  }

  override fun close() {
    pollJob?.cancel()
    pollJob = null
    lastDeadline = null
  }

  private fun publish(snapshot: PowerSnapshot?): Long {
    if (snapshot == null || !snapshot.interactive || snapshot.hasScreenWakeLock) {
      lastDeadline = null
      return retryMs
    }
    val lastActivity = snapshot.lastUserActivityUptime ?: run {
      lastDeadline = null
      return retryMs
    }
    val timeout = snapshot.screenOffTimeoutMs?.toLong()?.takeIf { it > 0L } ?: run {
      lastDeadline = null
      return retryMs
    }
    val deadline = TimeoutDeadline(
      screenOffAtUptime = lastActivity + timeout,
      screenDimAtUptime = ScreenDimmingPolicy.screenDimAt(
        lastActivity,
        timeout,
        snapshot.dimDurationMs?.toLong(),
      ),
      token = ExtendToken.Seq(lastActivity),
      lastExternalActivityUptime = lastActivity,
      backend = BackendId.Shizuku,
    )
    // 同一个系统活动 token 在进入采集窗口后会被每秒轮询到；重复发射会让
    // reducer 重新开始采帧并取消上一轮，导致 Shizuku 后端永远来不及 extend。
    if (lastDeadline != deadline) {
      lastDeadline = deadline
      _deadlines.tryEmit(deadline)
    }
    val untilCheck = deadline.captureWakeAtUptime(leadMs) - clock() - 1_000L
    return max(retryMs, untilCheck)
  }
}
