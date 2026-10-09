package cn.rainafter.app.gazekeep.backend.shizuku.protocol

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** 限制 shell 命令寿命，Binder 查询失败不能无限阻塞释放或恢复。 */
internal object ShellCommandRunner {
  fun run(vararg command: String, timeoutMs: Long = 3_000L): String {
    val process = ProcessBuilder(*command).redirectErrorStream(true).start()
    val reader = Executors.newSingleThreadExecutor { task ->
      Thread(task, "GazeKeepShellRead").apply { isDaemon = true }
    }
    val output = reader.submit<String> { process.inputStream.bufferedReader().use { it.readText() } }
    return try {
      val text = output.get(timeoutMs, TimeUnit.MILLISECONDS)
      check(process.waitFor(timeoutMs, TimeUnit.MILLISECONDS) && process.exitValue() == 0) { "shell command failed" }
      text
    } finally {
      process.destroyForcibly()
      output.cancel(true)
      reader.shutdownNow()
    }
  }
}
