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

  // 无界队列：记录在调用时已带事件时间，排队只影响落盘时刻，不能因队列满而静默丢弃。
  private val queue = Channel<() -> Unit>(Channel.UNLIMITED)
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
    val atMs = System.currentTimeMillis()
    queue.trySend { prepend(store.append(record, atMs)) }
  }

  fun log(level: LogLevel, tag: String, target: String? = "system", message: String) {
    val atMs = System.currentTimeMillis()
    queue.trySend {
      prepend(store.appendLog(level = level, tag = tag, target = target, message = message, atMs = atMs))
    }
  }

  /** 新记录插到列表前端，避免每次写入都重读全部日志文件；保留期由定时清理后的整体重读收敛。 */
  private fun prepend(entry: LogEntry?) {
    if (entry != null) mutableEntries.value = listOf(entry) + mutableEntries.value
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
