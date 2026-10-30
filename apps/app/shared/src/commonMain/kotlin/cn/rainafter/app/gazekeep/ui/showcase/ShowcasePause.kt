package cn.rainafter.app.gazekeep.ui.showcase

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * 官网演示的可见闸门：手机离开视口或标签页隐藏时演示停在当前一步，回来后从原处继续。
 * 等待只计算可见期间的时长，不因离开而提前结束或重新计时。
 */
internal class ShowcasePause(private val clock: TimeSource = TimeSource.Monotonic) {
  private val active = MutableStateFlow(true)

  fun set(visible: Boolean) {
    active.value = visible
  }

  /** 挂起到可见；返回是否确实等待过，供逐帧动画重置帧间隔。 */
  suspend fun awaitActive(): Boolean {
    if (active.value) return false
    active.first { it }
    return true
  }

  suspend fun delay(millis: Long) {
    var left = millis.milliseconds
    while (left > Duration.ZERO) {
      awaitActive()
      val mark = clock.markNow()
      withTimeoutOrNull(left) { active.first { !it } }
      left -= mark.elapsedNow()
    }
  }
}
