package cn.rainafter.app.gazekeep.backend.shizuku.client

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * UserService 进程退出而 Shizuku 仍在线时，系统不会再回调 binderReceived；由此按退避重新绑定。
 * Shizuku 自身离线时不重试，等待 binderReceived 驱动的正常刷新。[bind] 返回 false 表示绑定调用失败
 *（不会再有 onServiceDisconnected），此时继续按退避安排下一次。
 */
internal class UserServiceReconnector(
  private val scope: CoroutineScope,
  private val binderAlive: () -> Boolean,
  private val bind: () -> Boolean,
  private val delaysMs: List<Long> = listOf(1_000L, 2_000L, 5_000L, 10_000L, 30_000L),
) {
  private var attempts = 0
  private var pending: Job? = null

  @Synchronized fun onLost() {
    if (pending?.isActive == true) return
    schedule()
  }

  private fun schedule() {
    val wait = delaysMs[minOf(attempts, delaysMs.lastIndex)]
    attempts++
    pending = scope.launch {
      delay(wait)
      if (binderAlive() && !bind()) synchronized(this@UserServiceReconnector) { schedule() }
    }
  }

  @Synchronized fun onConnected() {
    attempts = 0
    pending?.cancel()
    pending = null
  }

  @Synchronized fun cancel() {
    pending?.cancel()
    pending = null
  }
}
