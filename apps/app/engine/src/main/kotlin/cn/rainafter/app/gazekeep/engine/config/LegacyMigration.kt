package cn.rainafter.app.gazekeep.engine.config

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import cn.rainafter.app.gazekeep.core.model.BackendLock

internal class LegacyMigration(private val context: Context) : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences) = currentData[ConfigStore.MIGRATED] != true
    override suspend fun migrate(currentData: Preferences): Preferences {
        val legacy = context.getSharedPreferences("gazekeep_config", Context.MODE_PRIVATE)
        val backend = legacy.getString("backend_lock", BackendLock.Auto.name)
        return currentData.toMutablePreferences().apply {
            this[booleanPreferencesKey("enabled")] = false
            this[stringPreferencesKey("backend_lock")] = runCatching { BackendLock.valueOf(backend.orEmpty()) }.getOrDefault(BackendLock.Auto).name
            this[booleanPreferencesKey("whitelist_enabled")] = true
            this[ConfigStore.MIGRATED] = true
        }
    }
    override suspend fun cleanUp() = Unit
}
