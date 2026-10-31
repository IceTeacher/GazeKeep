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
import cn.rainafter.app.gazekeep.core.model.AppFilterMode
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
    internal val APP_FILTER_MODE = stringPreferencesKey("app_filter_mode")
    private val LEGACY_WHITELIST = booleanPreferencesKey("whitelist_enabled")

    /** 键名沿用旧版的单一名单，升级时白名单无需搬迁。 */
    private val WHITELIST_PACKAGES = stringSetPreferencesKey("packages")
    private val BLACKLIST_PACKAGES = stringSetPreferencesKey("blacklist_packages")
    private val MAX_HOLD = intPreferencesKey("max_hold_minutes")
    private val SELF_TEST = longPreferencesKey("self_test_passed_at")
    internal val MIGRATED = booleanPreferencesKey("m4_migrated")

    private fun isValidLeadMillis(value: Long): Boolean = value in 0L..60_000L && value % 1_000L == 0L

    private fun isValidMaxHoldMinutes(value: Int): Boolean = value in 0..1_440

    /**
     * 本版写入时总会删除旧开关，旧开关仍在说明升级前或降级后由旧版写入，以它为准。
     * 两者都缺失或新值非法时回到默认的白名单，不扩大生效范围。
     */
    private fun decodeFilterMode(values: Preferences): AppFilterMode {
      values[LEGACY_WHITELIST]?.let { return if (it) AppFilterMode.Whitelist else AppFilterMode.All }
      val stored = values[APP_FILTER_MODE] ?: return AppFilterMode.Whitelist
      return runCatching { AppFilterMode.valueOf(stored) }.getOrDefault(AppFilterMode.Whitelist)
    }

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
        appFilterMode = decodeFilterMode(values),
        whitelistPackages = values[WHITELIST_PACKAGES].orEmpty(),
        blacklistPackages = values[BLACKLIST_PACKAGES].orEmpty(),
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
      values[APP_FILTER_MODE] = config.appFilterMode.name
      // 删除旧开关：降级回旧版时读到默认的白名单开启，而不是把黑名单当作全部应用。
      values.remove(LEGACY_WHITELIST)
      values[WHITELIST_PACKAGES] = config.whitelistPackages
      values[BLACKLIST_PACKAGES] = config.blacklistPackages
      values[MAX_HOLD] = config.maxHoldMinutes
    }
  }
}
