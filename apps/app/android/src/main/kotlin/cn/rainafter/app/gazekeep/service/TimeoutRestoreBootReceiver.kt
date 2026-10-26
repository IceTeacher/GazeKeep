package cn.rainafter.app.gazekeep.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cn.rainafter.app.gazekeep.backend.shizuku.client.PendingTimeoutRecovery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 开机只处理 Shizuku 临时超时的待恢复记录，不启动检测服务或相机。 */
class TimeoutRestoreBootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
    val result = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
      try {
        PendingTimeoutRecovery.recover(context.applicationContext)
      } finally {
        result.finish()
      }
    }
  }
}
