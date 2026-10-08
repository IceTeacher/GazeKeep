package cn.rainafter.app.gazekeep.backend.shizuku

import android.os.IBinder
import cn.rainafter.app.gazekeep.backend.shizuku.oracle.PowerDumpParser
import cn.rainafter.app.gazekeep.backend.shizuku.protocol.IGazeKeepShell
import cn.rainafter.app.gazekeep.backend.shizuku.protocol.PowerSnapshot
import java.lang.reflect.InvocationTargetException

/** 运行在 Shizuku shell uid 的 UserService；这里只做 power binder 调用。 */
class ShellService : IGazeKeepShell.Stub() {
  private val power = PowerManagerBridge()

  override fun version(): Int = IGazeKeepShell.Stub.PROTOCOL_VERSION

  override fun userActivity(uptime: Long) {
    power.userActivity(uptime)
  }

  override fun readPower(): PowerSnapshot = power.readPower()

  override fun getScreenOffTimeout(): Int = power.getScreenOffTimeout()

  override fun setScreenOffTimeout(millis: Int) {
    power.setScreenOffTimeout(millis)
  }

  private class PowerManagerBridge {
    private val service: Any by lazy {
      val manager = Class.forName("android.os.ServiceManager")
      manager.getMethod("getService", String::class.java).invoke(null, "power")
        ?: error("power service unavailable")
    }
    private val powerManager: Any by lazy {
      val stub = Class.forName("android.os.IPowerManager\$Stub")
      stub.getMethod("asInterface", IBinder::class.java).invoke(null, service)
        ?: error("IPowerManager unavailable")
    }

    fun userActivity(uptime: Long) {
      val candidate = powerManager.javaClass.methods
        .filter { it.name == "userActivity" }
        .sortedBy { it.parameterTypes.size }
        .firstOrNull()
        ?: error("userActivity signature unavailable")
      val args = when (candidate.parameterTypes.size) {
        3 -> arrayOf<Any>(uptime, USER_ACTIVITY_EVENT_OTHER, 0)
        4 -> arrayOf<Any>(0, uptime, USER_ACTIVITY_EVENT_OTHER, 0)
        else -> error("unsupported userActivity signature")
      }
      try {
        candidate.invoke(powerManager, *args)
      } catch (error: InvocationTargetException) {
        throw (error.targetException as? RuntimeException ?: error)
      }
    }

    fun readPower(): PowerSnapshot {
      val process = ProcessBuilder("dumpsys", "power")
        .redirectErrorStream(true)
        .start()
      val dump = process.inputStream.bufferedReader().use { it.readText() }
      if (process.waitFor() != 0) error("power dump failed")
      return PowerDumpParser.parse(dump)
    }

    fun getScreenOffTimeout(): Int = runCommand("cmd", "settings", "get", "system", "screen_off_timeout")
      .trim()
      .toIntOrNull()
      ?: error("screen_off_timeout unavailable")

    fun setScreenOffTimeout(millis: Int) {
      runCommand("cmd", "settings", "put", "system", "screen_off_timeout", millis.toString())
    }

    private fun runCommand(vararg command: String): String {
      val process = ProcessBuilder(*command).redirectErrorStream(true).start()
      val output = process.inputStream.bufferedReader().use { it.readText() }
      if (process.waitFor() != 0) error("settings command failed")
      return output
    }

    private companion object {
      const val USER_ACTIVITY_EVENT_OTHER = 2
    }
  }
}
