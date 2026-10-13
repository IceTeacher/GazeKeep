package cn.rainafter.app.gazekeep.engine.gate

import android.app.KeyguardManager
import android.content.Context
import android.media.AudioManager
import android.os.PowerManager
import cn.rainafter.app.gazekeep.core.policy.GateInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface GateProbe {
  suspend fun collect(): GateInput
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
    GateInput(
      interactive = power?.isInteractive == true,
      keyguardLocked = keyguard?.isKeyguardLocked == true,
      foregroundPackage = foreground.currentPackage(),
      audioMode = audio?.mode ?: AudioManager.MODE_NORMAL,
      frontCameraAvailable = environment.frontCameraAvailable(),
      proximityNear = environment.proximityNear(),
      lux = environment.lux(),
      faceDown = environment.faceDown(),
    )
  }

  fun setInteractive(interactive: Boolean) = environment.setInteractive(interactive)
  override fun close() = environment.close()
}
