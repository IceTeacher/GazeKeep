package cn.rainafter.app.gazekeep.engine.config

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import cn.rainafter.app.gazekeep.core.model.AppFilterMode
import cn.rainafter.app.gazekeep.core.model.BackendLock

internal class LegacyMigration(private val context: Context) : DataMigration<Preferences> {
  override suspend fun shouldMigrate(currentData: Preferences) = currentData[ConfigStore.MIGRATED] != true
  override suspend fun migrate(currentData: Preferences): Preferences {
    val legacy = context.getSharedPreferences("gazekeep_config", Context.MODE_PRIVATE)
    return migrateLegacy(currentData, legacy.getString("backend_lock", BackendLock.Auto.name))
  }
  override suspend fun cleanUp() = Unit
}

internal fun migrateLegacy(currentData: Preferences, backend: String?): Preferences =
  currentData.toMutablePreferences().apply {
    this[booleanPreferencesKey("enabled")] = false
    this[stringPreferencesKey("backend_lock")] =
      runCatching { BackendLock.valueOf(backend.orEmpty()) }.getOrDefault(BackendLock.Auto).name
    this[ConfigStore.APP_FILTER_MODE] = AppFilterMode.Whitelist.name
    this[ConfigStore.MIGRATED] = true
  }
