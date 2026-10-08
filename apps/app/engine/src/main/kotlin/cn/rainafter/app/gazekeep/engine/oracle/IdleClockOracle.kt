package cn.rainafter.app.gazekeep.engine.oracle

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import cn.rainafter.app.gazekeep.core.backend.ExtendToken
import cn.rainafter.app.gazekeep.core.backend.OracleSource
import cn.rainafter.app.gazekeep.core.backend.TimeoutDeadline
import cn.rainafter.app.gazekeep.core.backend.TimeoutOracle
import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.core.policy.ScreenDimmingPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.math.max

/** 无障碍路径的估算超时；只在活动或系统超时设置变化时推送 deadline。 */
class IdleClockOracle(private val context: Context, private val clock: () -> Long = { SystemClock.uptimeMillis() }) :
  TimeoutOracle,
  AutoCloseable {
  private val _deadlines = MutableSharedFlow<TimeoutDeadline>(replay = 1, extraBufferCapacity = 1)
  private val resolver = context.contentResolver
  private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
    override fun onChange(selfChange: Boolean) {
      emitDeadline()
    }
  }
  private var lastActivity = clock()
  private var timeoutMs = readTimeout()
  private var started = false
  private var foregroundPackage: String? = null

  override val source: OracleSource = OracleSource.IdleClock
  override val deadlines: Flow<TimeoutDeadline> = _deadlines.asSharedFlow()

  fun start() {
    if (started) return
    started = true
    resolver.registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_OFF_TIMEOUT), false, observer)
    emitDeadline()
  }

  override fun close() {
    if (!started) return
    resolver.unregisterContentObserver(observer)
    started = false
  }

  fun markActivity(uptime: Long = clock(), packageName: String? = foregroundPackage) {
    if (uptime >= lastActivity) lastActivity = uptime
    foregroundPackage = packageName
    emitDeadline()
  }

  fun markExtended(uptime: Long = clock()) = markActivity(uptime, foregroundPackage)

  fun updateForegroundPackage(packageName: String?) {
    foregroundPackage = packageName
    emitDeadline()
  }

  override fun millisUntilScreenOff(): Long = max(0L, lastActivity + timeoutMs - clock())

  private fun emitDeadline() {
    timeoutMs = readTimeout()
    _deadlines.tryEmit(
      TimeoutDeadline(
        screenOffAtUptime = lastActivity + timeoutMs,
        screenDimAtUptime = ScreenDimmingPolicy.screenDimAt(lastActivity, timeoutMs, readDimDuration()),
        token = ExtendToken.None,
        foregroundPackage = foregroundPackage,
        lastExternalActivityUptime = lastActivity,
        backend = BackendId.Accessibility,
      ),
    )
  }

  @Suppress("DiscouragedApi")
  private fun readDimDuration(): Long? = runCatching {
    // 读取 ROM 的实际资源覆盖值；缺失时不套用其他厂商的降亮比例。
    val resources = context.resources
    val durationId = resources.getIdentifier("config_maximumScreenDimDuration", "integer", "android")
    val ratioId = resources.getIdentifier("config_maximumScreenDimRatio", "fraction", "android")
    if (durationId == 0 || ratioId == 0) return@runCatching null
    ScreenDimmingPolicy.estimateDuration(
      timeoutMs,
      resources.getInteger(durationId).toLong(),
      resources.getFraction(ratioId, 1, 1),
    )
  }.getOrNull()

  private fun readTimeout(): Long = runCatching {
    Settings.System.getLong(resolver, Settings.System.SCREEN_OFF_TIMEOUT)
  }.getOrDefault(DEFAULT_TIMEOUT_MS).coerceAtLeast(1_000L)

  private companion object {
    const val DEFAULT_TIMEOUT_MS = 30_000L
  }
}
