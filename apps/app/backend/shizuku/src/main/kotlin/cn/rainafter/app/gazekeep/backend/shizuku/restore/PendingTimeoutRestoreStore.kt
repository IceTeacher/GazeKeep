package cn.rainafter.app.gazekeep.backend.shizuku.restore

import android.content.Context

/** 退路改超时前先落盘原值与本次写入值，避免服务重启后留下用户设置。 */
class PendingTimeoutRestoreStore private constructor(context: Context) {
  private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

  private var pending: PendingRestore? = if (preferences.contains(KEY)) {
    preferences.getInt(KEY, -1).takeIf { it > 0 }?.let { original ->
      PendingRestore(original, preferences.positive(KEY_EXTENDED), preferences.positive(KEY_PREVIOUS))
    }
  } else {
    null
  }

  @Synchronized fun read(): PendingRestore? = pending

  @Synchronized fun write(value: PendingRestore): Boolean {
    require(value.original > 0)
    val editor = preferences.edit().putInt(KEY, value.original)
    value.extended?.let { editor.putInt(KEY_EXTENDED, it) } ?: editor.remove(KEY_EXTENDED)
    value.previous?.let { editor.putInt(KEY_PREVIOUS, it) } ?: editor.remove(KEY_PREVIOUS)
    val saved = editor.commit()
    if (saved) pending = value
    return saved
  }

  @Synchronized fun clear(): Boolean {
    val cleared = preferences.edit().remove(KEY).remove(KEY_EXTENDED).remove(KEY_PREVIOUS).commit()
    if (cleared) pending = null
    return cleared
  }

  private fun android.content.SharedPreferences.positive(key: String): Int? =
    if (contains(key)) getInt(key, -1).takeIf { it > 0 } else null

  companion object {
    @Volatile private var instance: PendingTimeoutRestoreStore? = null
    fun get(context: Context): PendingTimeoutRestoreStore = instance ?: synchronized(this) {
      instance ?: PendingTimeoutRestoreStore(context.applicationContext).also { instance = it }
    }
    const val PREFERENCES = "gazekeep_timeout_restore"
    const val KEY = "pendingTimeoutRestore"
    private const val KEY_EXTENDED = "pendingTimeoutExtended"
    private const val KEY_PREVIOUS = "pendingTimeoutPrevious"
  }
}
