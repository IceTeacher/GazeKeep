package cn.rainafter.app.gazekeep.backend.shizuku

import android.content.Context

/** 退路改超时前先落盘原值，避免服务重启后留下用户设置。 */
class PendingTimeoutRestoreStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun read(): Int? {
        if (!preferences.contains(KEY)) return null
        return preferences.getInt(KEY, -1).takeIf { it > 0 }
    }

    fun write(value: Int) {
        preferences.edit().putInt(KEY, value).apply()
    }

    fun clear() {
        preferences.edit().remove(KEY).apply()
    }

    private companion object {
        const val PREFERENCES = "gazekeep_timeout_restore"
        const val KEY = "pendingTimeoutRestore"
    }
}
