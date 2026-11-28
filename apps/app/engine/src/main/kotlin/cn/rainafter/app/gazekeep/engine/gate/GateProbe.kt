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

  /** 按需补齐传感器读数；只在其余门控已放行时调用，关闭倒扣门控时不打开加速度计。 */
  suspend fun withEnvironment(input: GateInput, includeFaceDown: Boolean = true): GateInput = input
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

  companion object {
    internal fun readBatteryState(intent: Intent?): BatteryState {
      if (intent == null) return BatteryState(percent = null, charging = false)
      return parseBatteryState(
        level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
        scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1),
        plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0),
        status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1),
      )
    }

    fun currentChargingState(context: Context): Boolean =
      readBatteryState(context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))).charging

    internal fun parseBatteryState(level: Int, scale: Int, plugged: Int, status: Int): BatteryState {
      val percent = if (level >= 0 && scale > 0) ((level * 100) / scale).coerceIn(0, 100) else null
      // 不单独把 BATTERY_STATUS_FULL 判为充电中，避免 100% 拔线瞬态残留 FULL 导致误豁免。
      val charging = plugged != 0 || status == BatteryManager.BATTERY_STATUS_CHARGING
      return BatteryState(percent = percent, charging = charging)
    }
  }

  override suspend fun withEnvironment(input: GateInput, includeFaceDown: Boolean): GateInput {
    val reading = environment.sample(includeFaceDown = includeFaceDown)
    return input.copy(proximityNear = reading.proximityNear, lux = reading.lux, faceDown = reading.faceDown)
  }

  fun setInteractive(interactive: Boolean) = environment.setInteractive(interactive)
  override fun close() = environment.close()
}
