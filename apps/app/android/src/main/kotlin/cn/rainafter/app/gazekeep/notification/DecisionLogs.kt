package cn.rainafter.app.gazekeep.notification

import android.content.Context
import cn.rainafter.app.gazekeep.core.engine.DecisionRecord
import cn.rainafter.app.gazekeep.engine.config.ConfigStore
import cn.rainafter.app.gazekeep.ui.contract.LogEntry
import cn.rainafter.app.gazekeep.ui.contract.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 应用进程单例，配置、写入、读取与清理共享串行队列；日志故障不传播到引擎。 */
class DecisionLogs private constructor(context: Context) {
  private val store = DecisionLogStore(context.noBackupFilesDir.resolve("decisions"))
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val queue = Channel<() -> Unit>(256)
  private val mutableEntries = MutableStateFlow<List<LogEntry>>(emptyList())
  val entries = mutableEntries.asStateFlow()

  init {
    scope.launch {
      runCatching {
        store.cleanup()
        mutableEntries.value = store.readEntries()
      }
      for (operation in queue) runCatching(operation)
    }
    scope.launch {
      runCatching {
        ConfigStore(context).settings.collect { settings ->
          queue.send {
            store.setEnabled(settings.config.debugLogEnabled)
            mutableEntries.value = store.readEntries()
          }
        }
      }
    }
    scope.launch {
      while (true) {
        delay(60_000L)
        queue.send {
          store.cleanup()
          mutableEntries.value = store.readEntries()
        }
      }
    }
  }

  fun record(record: DecisionRecord) {
    queue.trySend {
      store.append(record)
      mutableEntries.value = store.readEntries()
    }
  }

  fun log(level: LogLevel, tag: String, target: String? = "system", message: String) {
    queue.trySend {
      store.appendLog(level = level, tag = tag, target = target, message = message)
      mutableEntries.value = store.readEntries()
    }
  }

  fun clear() {
    queue.trySend {
      store.clear()
      mutableEntries.value = emptyList()
    }
  }

  fun refresh() {
    queue.trySend {
      mutableEntries.value = store.readEntries()
    }
  }

  companion object {
    @Volatile private var instance: DecisionLogs? = null
    fun get(context: Context): DecisionLogs = instance ?: synchronized(this) {
      instance ?: DecisionLogs(context.applicationContext).also { instance = it }
    }
  }
}
