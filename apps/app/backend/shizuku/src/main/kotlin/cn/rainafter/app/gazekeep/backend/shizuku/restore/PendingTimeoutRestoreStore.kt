package cn.rainafter.app.gazekeep.backend.shizuku.restore

import android.content.Context

/** 退路改超时前先落盘原值，避免服务重启后留下用户设置。 */
class PendingTimeoutRestoreStore private constructor(context: Context) {
  private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

  private var pending: Int? = if (preferences.contains(KEY)) preferences.getInt(KEY, -1).takeIf { it > 0 } else null

  @Synchronized fun read(): Int? = pending

  @Synchronized fun write(value: Int): Boolean {
    require(value > 0)
    val saved = preferences.edit().putInt(KEY, value).commit()
    if (saved) pending = value
    return saved
  }

  @Synchronized fun clear(): Boolean {
    val cleared = preferences.edit().remove(KEY).commit()
    if (cleared) pending = null
    return cleared
  }

  companion object {
    @Volatile private var instance: PendingTimeoutRestoreStore? = null
    fun get(context: Context): PendingTimeoutRestoreStore = instance ?: synchronized(this) {
      instance ?: PendingTimeoutRestoreStore(context.applicationContext).also { instance = it }
    }
    const val PREFERENCES = "gazekeep_timeout_restore"
    const val KEY = "pendingTimeoutRestore"
  }
}
