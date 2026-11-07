package cn.rainafter.app.gazekeep.engine.gate

import java.util.concurrent.ConcurrentHashMap

/** 一次门控采样窗口内的读数；不依赖 Android，窗口之间不沿用旧值。 */
internal class SensorWindow(private val clock: () -> Long) {
  enum class Kind { Proximity, Light, Accel }

  private val samples = ConcurrentHashMap<Kind, Float>()

  @Volatile private var downSince: Long? = null

  @Synchronized fun reset() {
    samples.clear()
    downSince = null
  }

  /** 加速度传入归一化 Z；光照与接近传入原始值。 */
  @Synchronized fun offer(kind: Kind, value: Float) {
    samples[kind] = value
    if (kind == Kind.Accel) {
      downSince = if (value < FACE_DOWN_Z) downSince ?: clock() else null
    }
  }

  fun value(kind: Kind): Float? = samples[kind]

  fun faceDown(): Boolean? = value(Kind.Accel)?.let { z ->
    z < FACE_DOWN_Z && downSince?.let { clock() - it >= FACE_DOWN_HOLD_MS } == true
  }

  /** 每个已注册传感器都已上报，且朝下判定可以下结论。 */
  fun settled(expected: Set<Kind>): Boolean {
    if (!expected.all { samples.containsKey(it) }) return false
    if (Kind.Accel !in expected) return true
    val z = value(Kind.Accel) ?: return false
    return z >= FACE_DOWN_Z || faceDown() == true
  }

  companion object {
    const val FACE_DOWN_Z = -0.8f
    const val FACE_DOWN_HOLD_MS = 300L
  }
}
