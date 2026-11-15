package cn.rainafter.app.gazekeep.engine.gate

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import android.os.PowerManager
import cn.rainafter.app.gazekeep.core.policy.GateInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface GateProbe {
  /** 不打开传感器的门控输入；接近、光照与朝下保持 null。 */
  suspend fun collect(): GateInput

  /** 补齐传感器读数；只在其余门控已放行时调用，避免必然跳过的轮次也打开传感器。 */
  suspend fun withEnvironment(input: GateInput): GateInput = input
}

fun interface ForegroundAppSource {
  suspend fun currentPackage(): String?
}

/** 门控探测归运行会话所有，不读取窗口内容。 */
class AndroidGateProbe(private val context: Context, private val foreground: ForegroundAppSource) :
  GateProbe,
  AutoCloseable {
  private val environment = EnvironmentMonitor(context)
  private val power = context.getSystemService(PowerManager::class.java)
  private val keyguard = context.getSystemService(KeyguardManager::class.java)
  private val audio = context.getSystemService(AudioManager::class.java)

  override suspend fun collect(): GateInput = withContext(Dispatchers.Default) {
    val battery = readBatteryState(context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))
    GateInput(
      interactive = power?.isInteractive == true,
      keyguardLocked = keyguard?.isKeyguardLocked == true,
      foregroundPackage = foreground.currentPackage(),
      audioMode = audio?.mode ?: AudioManager.MODE_NORMAL,
      frontCameraAvailable = environment.frontCameraAvailable(),
      proximityNear = null,
      lux = null,
      faceDown = null,
      powerSaveMode = power?.isPowerSaveMode == true,
      batteryPercent = battery.percent,
      isCharging = battery.charging,
    )
  }

  internal data class BatteryState(val percent: Int?, val charging: Boolean)

  internal companion object {
    fun readBatteryState(intent: Intent?): BatteryState {
      if (intent == null) return BatteryState(percent = null, charging = false)
      val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
      val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
      val percent = if (level >= 0 && scale > 0) ((level * 100) / scale).coerceIn(0, 100) else null
      val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
      val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
      val charging =
        plugged != 0 ||
          status == BatteryManager.BATTERY_STATUS_CHARGING ||
          status == BatteryManager.BATTERY_STATUS_FULL
      return BatteryState(percent = percent, charging = charging)
    }
  }

  override suspend fun withEnvironment(input: GateInput): GateInput {
    val reading = environment.sample()
    return input.copy(proximityNear = reading.proximityNear, lux = reading.lux, faceDown = reading.faceDown)
  }

  fun setInteractive(interactive: Boolean) = environment.setInteractive(interactive)
  override fun close() = environment.close()
}
