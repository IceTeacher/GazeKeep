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
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

/** 只缓存门控所需数值；不存相机帧，所有监听在会话结束时注销。 */
internal class EnvironmentMonitor(context: Context) :
  SensorEventListener,
  AutoCloseable {
  private data class Sample(val value: Float, val at: Long)
  private val sensors = context.getSystemService(SensorManager::class.java)
  private val cameras = context.getSystemService(CameraManager::class.java)
  private val samples = ConcurrentHashMap<Int, Sample>()
  private val available = ConcurrentHashMap<String, Boolean>()
  private val cameraIds = runCatching { cameras?.cameraIdList?.toList().orEmpty() }.getOrDefault(emptyList())
  private val frontIds = runCatching {
    cameras?.cameraIdList?.filter {
      cameras.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) ==
        CameraCharacteristics.LENS_FACING_FRONT
    }.orEmpty()
  }.getOrDefault(emptyList())

  @Volatile private var downSince: Long? = null

  @Volatile private var listening = false
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

  @Synchronized fun setInteractive(interactive: Boolean) {
    if (interactive == listening) return
    listening = interactive
    samples.clear()
    downSince = null
    sensors?.unregisterListener(this)
    if (interactive) {
      for (type in listOf(Sensor.TYPE_PROXIMITY, Sensor.TYPE_LIGHT, Sensor.TYPE_ACCELEROMETER)) {
        sensors?.getDefaultSensor(type)?.let { sensor ->
          runCatching { sensors.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL) }
        }
      }
    }
  }

  override fun onSensorChanged(event: SensorEvent) {
    if (!listening) return
    val now = SystemClock.uptimeMillis()
    val type = event.sensor.type
    val value = if (type == Sensor.TYPE_ACCELEROMETER) {
      val norm = sqrt(event.values.take(3).sumOf { (it * it).toDouble() }).toFloat()
      if (norm < 0.1f) return
      event.values[2] / norm
    } else {
      event.values[0]
    }
    samples[type] = Sample(value, now)
    if (type == Sensor.TYPE_ACCELEROMETER) {
      if (value < -0.8f) {
        if (downSince == null) downSince = now
      } else {
        downSince = null
      }
    }
  }

  override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

  private fun value(type: Int): Float? = samples[type]?.takeIf {
    // on-change 传感器静止时不重复上报，值在本次监听周期内仍有效。
    type != Sensor.TYPE_ACCELEROMETER || SystemClock.uptimeMillis() - it.at <= 5_000L
  }?.value

  fun proximityNear(): Boolean? = value(Sensor.TYPE_PROXIMITY)?.let { distance ->
    val range = sensors?.getDefaultSensor(Sensor.TYPE_PROXIMITY)?.maximumRange ?: return null
    distance < minOf(5f, range)
  }
  fun lux(): Float? = value(Sensor.TYPE_LIGHT)
  fun faceDown(): Boolean? = value(Sensor.TYPE_ACCELEROMETER)?.let {
    it < -0.8f && downSince?.let { at -> SystemClock.uptimeMillis() - at >= 300L } == true
  }
  fun frontCameraAvailable(): Boolean =
    frontIds.any { available[it] == true } && cameraIds.all { available[it] == true }

  override fun close() {
    setInteractive(false)
    runCatching { cameras?.unregisterAvailabilityCallback(cameraCallback) }
    available.clear()
  }
}
