package cn.rainafter.app.gazekeep.engine.gate

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import cn.rainafter.app.gazekeep.engine.gate.SensorWindow.Kind
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

internal data class EnvironmentReading(
  val proximityNear: Boolean? = null,
  val lux: Float? = null,
  val faceDown: Boolean? = null,
)

/**
 * 传感器只在门控采样窗口内注册，读完即注销；亮屏期间不常驻监听。
 * 只缓存门控所需数值，不存相机帧；相机可用性回调常驻，会话结束时注销。
 */
internal class EnvironmentMonitor(context: Context) :
  SensorEventListener,
  AutoCloseable {
  private val sensors = context.getSystemService(SensorManager::class.java)
  private val cameras = context.getSystemService(CameraManager::class.java)
  private val window = SensorWindow(SystemClock::uptimeMillis)
  private val available = ConcurrentHashMap<String, Boolean>()
  private val cameraIds = runCatching { cameras?.cameraIdList?.toList().orEmpty() }.getOrDefault(emptyList())
  private val frontIds = runCatching {
    cameras?.cameraIdList?.filter {
      cameras.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) ==
        CameraCharacteristics.LENS_FACING_FRONT
    }.orEmpty()
  }.getOrDefault(emptyList())
  private val proximityRange = sensors?.getDefaultSensor(Sensor.TYPE_PROXIMITY)?.maximumRange

  @Volatile private var interactive = false

  // 以下字段由 this 监视器保护；generation 让息屏后迟到的窗口关闭不误注销新窗口。
  private var generation = 0
  private var windowUsers = 0
  private var registered = emptySet<Kind>()

  private val cameraCallback = object : CameraManager.AvailabilityCallback() {
    override fun onCameraAvailable(cameraId: String) {
      available[cameraId] = true
    }
    override fun onCameraUnavailable(cameraId: String) {
      available[cameraId] = false
    }
  }

  init {
    runCatching { cameras?.registerAvailabilityCallback(cameraCallback, Handler(Looper.getMainLooper())) }
    setInteractive(context.getSystemService(android.os.PowerManager::class.java)?.isInteractive == true)
  }

  @Synchronized fun setInteractive(value: Boolean) {
    interactive = value
    if (!value) {
      generation++
      windowUsers = 0
      stopListening()
    }
  }

  /** 打开一个短采样窗口：等每个传感器上报一次（朝下需持续 300 ms），超时则缺失值保持未知。 */
  suspend fun sample(timeoutMs: Long = SAMPLE_WINDOW_MS): EnvironmentReading {
    val (opened, expected) = openWindow() ?: return EnvironmentReading()
    try {
      withTimeoutOrNull(timeoutMs) {
        while (!window.settled(expected)) delay(POLL_MS)
      }
      val near = window.value(Kind.Proximity)?.let { distance ->
        proximityRange?.let { distance < minOf(5f, it) }
      }
      return EnvironmentReading(near, window.value(Kind.Light), window.faceDown())
    } finally {
      closeWindow(opened)
    }
  }

  @Synchronized private fun openWindow(): Pair<Int, Set<Kind>>? {
    if (!interactive) return null
    if (windowUsers++ == 0) {
      window.reset()
      val kinds = mutableSetOf<Kind>()
      for ((type, kind) in SENSOR_KINDS) {
        val sensor = sensors?.getDefaultSensor(type) ?: continue
        val ok = runCatching { sensors.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI) }
          .getOrDefault(false)
        if (ok) kinds += kind
      }
      registered = kinds
    }
    return generation to registered
  }

  @Synchronized private fun closeWindow(opened: Int) {
    if (opened != generation || windowUsers == 0) return
    if (--windowUsers == 0) stopListening()
  }

  private fun stopListening() {
    runCatching { sensors?.unregisterListener(this) }
    registered = emptySet()
    window.reset()
  }

  override fun onSensorChanged(event: SensorEvent) {
    if (!interactive) return
    when (event.sensor.type) {
      Sensor.TYPE_ACCELEROMETER -> {
        val norm = sqrt(event.values.take(3).sumOf { (it * it).toDouble() }).toFloat()
        if (norm >= 0.1f) window.offer(Kind.Accel, event.values[2] / norm)
      }

      Sensor.TYPE_LIGHT -> window.offer(Kind.Light, event.values[0])

      Sensor.TYPE_PROXIMITY -> window.offer(Kind.Proximity, event.values[0])
    }
  }

  override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

  fun frontCameraAvailable(): Boolean =
    frontIds.any { available[it] == true } && cameraIds.all { available[it] == true }

  override fun close() {
    setInteractive(false)
    runCatching { cameras?.unregisterAvailabilityCallback(cameraCallback) }
    available.clear()
  }

  private companion object {
    // 覆盖朝下 300 ms 与首个样本延迟：Xiaomi 12 Pro 的超声波接近传感器注册后约 0.56 s 才首报，
    // 三个传感器约 0.58 s 齐全。唤醒点距熄屏至少 2.5 s，窗口超时后采集仍有约 1.3 s 预算。
    const val SAMPLE_WINDOW_MS = 1_000L
    const val POLL_MS = 20L
    val SENSOR_KINDS = listOf(
      Sensor.TYPE_PROXIMITY to Kind.Proximity,
      Sensor.TYPE_LIGHT to Kind.Light,
      Sensor.TYPE_ACCELEROMETER to Kind.Accel,
    )
  }
}
