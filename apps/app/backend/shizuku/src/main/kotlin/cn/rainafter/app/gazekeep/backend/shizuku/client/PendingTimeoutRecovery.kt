package cn.rainafter.app.gazekeep.backend.shizuku.client

import android.content.Context
import cn.rainafter.app.gazekeep.backend.shizuku.restore.PendingTimeoutRestoreStore
import cn.rainafter.app.gazekeep.core.model.Readiness
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 退路延长期间重启时，系统超时停留在延长值；开机时若存在恢复记录，单独连接 shell 写回原值。
 * Shizuku 未就绪或超时则保留记录，待下次连接恢复。
 */
object PendingTimeoutRecovery {
  suspend fun recover(context: Context, timeoutMs: Long = 20_000L): Boolean = recoverIfPending(
    hasPending = { PendingTimeoutRestoreStore.get(context).read() != null },
  ) {
    val backend = ShizukuKeepAwakeBackend(context)
    try {
      backend.attach()
      // 连接成功时先恢复再报告 Ready；detach 会完成被推迟的恢复。
      withTimeoutOrNull(timeoutMs) { backend.readiness.first { it == Readiness.Ready } } != null
    } finally {
      backend.detach()
    }
  }

  internal suspend fun recoverIfPending(hasPending: () -> Boolean, connectAndRestore: suspend () -> Boolean): Boolean =
    !hasPending() || connectAndRestore()
}
