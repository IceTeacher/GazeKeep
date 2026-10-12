package cn.rainafter.app.gazekeep.engine.config

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cn.rainafter.app.gazekeep.core.model.BackendLock
import cn.rainafter.app.gazekeep.core.model.GazeConfig
import cn.rainafter.app.gazekeep.core.model.Sensitivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.gazeDataStore by preferencesDataStore(
  name = "gazekeep_settings",
  produceMigrations = { context -> listOf(LegacyMigration(context)) },
)

data class StoredSettings(val config: GazeConfig = GazeConfig(), val selfTestPassedAt: Long? = null)

/** 委托确保每个进程只打开一次同一路径，设置页和服务不持有不同的缓存副本。 */
class ConfigStore internal constructor(private val store: DataStore<Preferences>) {
  constructor(context: Context) : this(context.applicationContext.gazeDataStore)
  val settings: Flow<StoredSettings> = store.data.map(::decodeSettings)

  suspend fun update(transform: (GazeConfig) -> GazeConfig) {
    store.edit { values -> writeConfig(values, transform(decodeSettings(values).config)) }
  }

  suspend fun completeSelfTest() {
    store.edit { it[SELF_TEST] = System.currentTimeMillis() }
  }

  companion object {
    private val ENABLED = booleanPreferencesKey("enabled")
    private val DEBUG_LOG = booleanPreferencesKey("debug_log_enabled")
    private val HIDE_CAMERA_INDICATOR = booleanPreferencesKey("hide_camera_indicator")
    private val BACKEND = stringPreferencesKey("backend_lock")
    private val LEAD = longPreferencesKey("lead_millis")
    private val SENSITIVITY = stringPreferencesKey("sensitivity")
    private val WHITELIST = booleanPreferencesKey("whitelist_enabled")
    private val PACKAGES = stringSetPreferencesKey("packages")
    private val MAX_HOLD = intPreferencesKey("max_hold_minutes")
    private val SELF_TEST = longPreferencesKey("self_test_passed_at")
    internal val MIGRATED = booleanPreferencesKey("m4_migrated")

    private fun isValidLeadMillis(value: Long): Boolean = value in 0L..60_000L && value % 1_000L == 0L

    private fun isValidMaxHoldMinutes(value: Int): Boolean = value in 0..1_440

    private fun decodeSettings(values: Preferences) = StoredSettings(
      config = GazeConfig(
        enabled = values[ENABLED] ?: false,
        debugLogEnabled = values[DEBUG_LOG] ?: false,
        hideCameraIndicator = values[HIDE_CAMERA_INDICATOR] ?: false,
        backendLock = runCatching {
          BackendLock.valueOf(values[BACKEND].orEmpty())
        }.getOrDefault(BackendLock.Auto),
        leadMillis = values[LEAD]?.takeIf(::isValidLeadMillis) ?: 8_000L,
        sensitivity = runCatching {
          Sensitivity.valueOf(values[SENSITIVITY].orEmpty())
        }.getOrDefault(Sensitivity.Standard),
        whitelistEnabled = values[WHITELIST] ?: true,
        packages = values[PACKAGES].orEmpty(),
        maxHoldMinutes = values[MAX_HOLD]?.takeIf(::isValidMaxHoldMinutes) ?: 30,
      ),
      selfTestPassedAt = values[SELF_TEST],
    )

    private fun writeConfig(values: MutablePreferences, config: GazeConfig) {
      require(isValidLeadMillis(config.leadMillis))
      require(isValidMaxHoldMinutes(config.maxHoldMinutes))
      values[ENABLED] = config.enabled
      values[DEBUG_LOG] = config.debugLogEnabled
      values[HIDE_CAMERA_INDICATOR] = config.hideCameraIndicator
      values[BACKEND] = config.backendLock.name
      values[LEAD] = config.leadMillis
      values[SENSITIVITY] = config.sensitivity.name
      values[WHITELIST] = config.whitelistEnabled
      values[PACKAGES] = config.packages
      values[MAX_HOLD] = config.maxHoldMinutes
    }
  }
}
