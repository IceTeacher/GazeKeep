package cn.rainafter.app.gazekeep.engine.gate

import android.app.KeyguardManager
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.PowerManager
import cn.rainafter.app.gazekeep.core.policy.GateContext
import cn.rainafter.app.gazekeep.core.policy.GateInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface GateProbe {
  suspend fun collect(context: GateContext): GateInput
}

fun interface ForegroundAppSource {
  fun currentPackage(): String?
}

/** 只采集门控所需的枚举和系统状态，不读取窗口内容，也不持有传感器。 */
class AndroidGateProbe(private val context: Context, private val foreground: ForegroundAppSource) : GateProbe {
  private val power = context.getSystemService(PowerManager::class.java)
  private val keyguard = context.getSystemService(KeyguardManager::class.java)
  private val audio = context.getSystemService(AudioManager::class.java)
  private val battery = context.getSystemService(BatteryManager::class.java)
  private val camera = context.getSystemService(CameraManager::class.java)

  override suspend fun collect(context: GateContext): GateInput = withContext(Dispatchers.Default) {
    GateInput(
      interactive = power?.isInteractive == true,
      keyguardLocked = keyguard?.isKeyguardLocked == true,
      foregroundPackage = foreground.currentPackage(),
      audioMode = audio?.mode ?: AudioManager.MODE_NORMAL,
      frontCameraAvailable = hasFrontCamera(),
      proximityNear = null,
      lux = null,
      faceDown = null,
      batteryPercent = batteryPercent(),
      powerSave = power?.isPowerSaveMode == true,
    )
  }

  private fun hasFrontCamera(): Boolean = runCatching {
    camera?.cameraIdList?.any { id ->
      camera.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) ==
        CameraCharacteristics.LENS_FACING_FRONT
    } == true
  }.getOrDefault(false)

  private fun batteryPercent(): Int {
    val value = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
    return value.takeIf { it in 0..100 } ?: 100
  }
}
