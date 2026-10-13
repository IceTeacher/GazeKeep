package cn.rainafter.app.gazekeep.engine.oracle

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import cn.rainafter.app.gazekeep.core.backend.ExtendToken
import cn.rainafter.app.gazekeep.core.backend.TimeoutDeadline
import cn.rainafter.app.gazekeep.core.backend.TimeoutOracle
import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.core.policy.ScreenDimmingPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** 无障碍路径的估算超时；只在活动或系统超时设置变化时推送 deadline。 */
class IdleClockOracle internal constructor(
  private val clock: () -> Long,
  private val readTimeout: () -> Long,
  private val readDimDuration: (Long) -> Long?,
  private val observe: ((() -> Unit) -> AutoCloseable)? = null,
) : TimeoutOracle,
  AutoCloseable {
  private val _deadlines = MutableSharedFlow<TimeoutDeadline>(replay = 1, extraBufferCapacity = 1)
  constructor(context: Context, clock: () -> Long = { SystemClock.uptimeMillis() }) : this(
    clock,
    { readSystemTimeout(context) },
    { timeout -> readSystemDimDuration(context, timeout) },
    { changed -> observeSystemTimeout(context, changed) },
  )

  private var observation: AutoCloseable? = null
  private var lastActivity = clock()
  private var lastExternalActivity = lastActivity
  private var timeoutMs = readTimeout()
  private var started = false
  private var foregroundPackage: String? = null

  override val deadlines: Flow<TimeoutDeadline> = _deadlines.asSharedFlow()

  fun start() {
    if (started) return
    started = true
    observation = observe?.invoke(::emitDeadline)
    emitDeadline()
  }

  override fun close() {
    if (!started) return
    observation?.close()
    observation = null
    started = false
  }

  fun markActivity(uptime: Long = clock(), packageName: String? = foregroundPackage) {
    lastActivity = maxOf(lastActivity, uptime)
    lastExternalActivity = maxOf(lastExternalActivity, uptime)
    foregroundPackage = packageName
    emitDeadline()
  }

  fun markExtended(uptime: Long = clock()) {
    // 自身保亮只重置系统超时，不能伪装成触摸而清除累计上限。
    lastActivity = maxOf(lastActivity, uptime)
    emitDeadline()
  }

  fun updateForegroundPackage(packageName: String?) {
    foregroundPackage = packageName
    emitDeadline()
  }

  private fun emitDeadline() {
    timeoutMs = readTimeout()
    _deadlines.tryEmit(
      TimeoutDeadline(
        screenOffAtUptime = lastActivity + timeoutMs,
        screenDimAtUptime = ScreenDimmingPolicy.screenDimAt(lastActivity, timeoutMs, readDimDuration(timeoutMs)),
        token = ExtendToken.None,
        foregroundPackage = foregroundPackage,
        lastExternalActivityUptime = lastExternalActivity,
        backend = BackendId.Accessibility,
      ),
    )
  }
}

private fun observeSystemTimeout(context: Context, changed: () -> Unit): AutoCloseable {
  val resolver = context.contentResolver
  val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
    override fun onChange(selfChange: Boolean) = changed()
  }
  resolver.registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_OFF_TIMEOUT), false, observer)
  return AutoCloseable { resolver.unregisterContentObserver(observer) }
}

@Suppress("DiscouragedApi")
private fun readSystemDimDuration(context: Context, timeoutMs: Long): Long? = runCatching {
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

private fun readSystemTimeout(context: Context): Long = runCatching {
  Settings.System.getLong(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT)
}.getOrDefault(30_000L).coerceAtLeast(1_000L)
