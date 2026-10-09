package cn.rainafter.app.gazekeep.notification

import android.content.Context
import cn.rainafter.app.gazekeep.core.engine.DecisionRecord
import cn.rainafter.app.gazekeep.engine.config.ConfigStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 应用进程单例，配置、写入与清理共享串行队列；日志故障不传播到引擎。 */
class DecisionLogs private constructor(context: Context) {
  private val store = DecisionLogStore(context.noBackupFilesDir.resolve("decisions"))
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val queue = Channel<() -> Unit>(256)
  init {
    scope.launch {
      runCatching { store.cleanup() }
      for (operation in queue) runCatching(operation)
    }
    scope.launch {
      runCatching {
        ConfigStore(context).settings.collect { settings ->
          queue.send { store.setEnabled(settings.config.debugLogEnabled) }
        }
      }
    }
    scope.launch {
      while (true) {
        delay(60_000L)
        queue.send { store.cleanup() }
      }
    }
  }
  fun record(record: DecisionRecord) {
    queue.trySend { store.append(record) }
  }
  companion object {
    @Volatile private var instance: DecisionLogs? = null
    fun get(context: Context): DecisionLogs = instance ?: synchronized(this) {
      instance ?: DecisionLogs(context.applicationContext).also { instance = it }
    }
  }
}
