package cn.rainafter.app.gazekeep.engine.config

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import cn.rainafter.app.gazekeep.core.model.AppFilterMode
import cn.rainafter.app.gazekeep.core.model.BackendLock
import cn.rainafter.app.gazekeep.core.model.GazeConfig
import cn.rainafter.app.gazekeep.core.model.Sensitivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigStoreTest {
  @Test fun defaultConfigurationIsClosedAndWhitelisted() = runBlocking {
    val directory = Files.createTempDirectory("gazekeep-config-test").toFile()
    val job = SupervisorJob()
    val store =
      ConfigStore(
        PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) {
          directory.resolve("settings.preferences_pb")
        },
      )
    try {
      val initial = store.settings.first()
      assertFalse(initial.config.enabled)
      assertFalse(initial.config.hideCameraIndicator)
      assertEquals(AppFilterMode.Whitelist, initial.config.appFilterMode)
      assertTrue(initial.config.whitelistPackages.isEmpty())
      assertTrue(initial.config.blacklistPackages.isEmpty())
      assertNull(initial.selfTestPassedAt)
    } finally {
      job.cancelAndJoin()
      directory.deleteRecursively()
    }
  }

  @Test fun settingsAndSelfTestSurviveReopeningTheFile(): Unit = runBlocking {
    val directory = Files.createTempDirectory("gazekeep-config-test").toFile()
    val file = directory.resolve("settings.preferences_pb")
    val firstJob = SupervisorJob()
    val first =
      ConfigStore(PreferenceDataStoreFactory.create(scope = CoroutineScope(firstJob + Dispatchers.IO)) { file })
    val expected = GazeConfig(
      backendLock = BackendLock.Lsposed,
      leadMillis = 5_000L,
      sensitivity = Sensitivity.Loose,
      appFilterMode = AppFilterMode.Blacklist,
      whitelistPackages = setOf("example.reader"),
      blacklistPackages = setOf("example.video"),
      maxHoldMinutes = 10,
      debugLogEnabled = true,
      hideCameraIndicator = true,
    )
    first.update { expected }
    first.completeSelfTest()
    val saved = first.settings.first()
    firstJob.cancelAndJoin()
    val secondJob = SupervisorJob()
    try {
      val reopened =
        ConfigStore(
          PreferenceDataStoreFactory.create(scope = CoroutineScope(secondJob + Dispatchers.IO)) {
            file
          },
        )
      assertEquals(expected, reopened.settings.first().config)
      assertEquals(saved.selfTestPassedAt, reopened.settings.first().selfTestPassedAt)
      assertNotNull(saved.selfTestPassedAt)
    } finally {
      secondJob.cancelAndJoin()
      directory.deleteRecursively()
    }
  }

  @Test fun rejectedAndConcurrentChangesDoNotLoseStoredSettings() = runBlocking {
    val directory = Files.createTempDirectory("gazekeep-config-test").toFile()
    val job = SupervisorJob()
    val store =
      ConfigStore(
        PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) {
          directory.resolve("settings.preferences_pb")
        },
      )
    try {
      coroutineScope {
        launch { store.update { it.copy(leadMillis = 5_000L) } }
        launch { store.update { it.copy(maxHoldMinutes = 10) } }
      }
      val saved = store.settings.first()
      assertEquals(5_000L, saved.config.leadMillis)
      assertEquals(10, saved.config.maxHoldMinutes)
      assertFailsWith<IllegalArgumentException> { store.update { it.copy(leadMillis = 60_001L) } }
      assertFailsWith<IllegalArgumentException> { store.update { it.copy(leadMillis = 5_500L) } }
      assertFailsWith<IllegalArgumentException> { store.update { it.copy(maxHoldMinutes = 1_441) } }
      assertEquals(saved, store.settings.first())

      store.update { it.copy(leadMillis = 0L, maxHoldMinutes = 0) }
      assertEquals(0L, store.settings.first().config.leadMillis)
      assertEquals(0, store.settings.first().config.maxHoldMinutes)

      store.update { it.copy(leadMillis = 12_000L, maxHoldMinutes = 45) }
      assertEquals(12_000L, store.settings.first().config.leadMillis)
      assertEquals(45, store.settings.first().config.maxHoldMinutes)
    } finally {
      job.cancelAndJoin()
      directory.deleteRecursively()
    }
  }

  @Test fun legacyWhitelistSettingMapsToFilterModeAndKeepsPackages() = runBlocking {
    val cases = mapOf(true to AppFilterMode.Whitelist, false to AppFilterMode.All, null to AppFilterMode.Whitelist)
    for ((legacy, expected) in cases) {
      val directory = Files.createTempDirectory("gazekeep-config-test").toFile()
      val job = SupervisorJob()
      val dataStore = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) {
        directory.resolve("settings.preferences_pb")
      }
      try {
        dataStore.edit { values ->
          legacy?.let { values[LEGACY_WHITELIST] = it }
          values[PACKAGES] = setOf("example.reader", "example.news")
        }
        val config = ConfigStore(dataStore).settings.first().config
        assertEquals(expected, config.appFilterMode, "whitelist_enabled=$legacy")
        assertEquals(setOf("example.reader", "example.news"), config.whitelistPackages)
        assertTrue(config.blacklistPackages.isEmpty())
      } finally {
        job.cancelAndJoin()
        directory.deleteRecursively()
      }
    }
  }

  @Test fun writingFilterModeDropsLegacyKeyAndKeepsListsSeparate() = runBlocking {
    val directory = Files.createTempDirectory("gazekeep-config-test").toFile()
    val job = SupervisorJob()
    val dataStore = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) {
      directory.resolve("settings.preferences_pb")
    }
    try {
      dataStore.edit { it[LEGACY_WHITELIST] = false }
      val store = ConfigStore(dataStore)
      store.update { it.copy(appFilterMode = AppFilterMode.Whitelist, whitelistPackages = setOf("example.reader")) }
      store.update { it.copy(appFilterMode = AppFilterMode.Blacklist, blacklistPackages = setOf("example.video")) }
      val values = dataStore.data.first()
      // 降级回旧版时读不到旧键，回到默认的白名单开启，不会扩大生效范围。
      assertNull(values[LEGACY_WHITELIST])
      assertEquals(AppFilterMode.Blacklist.name, values[FILTER_MODE])
      val config = store.settings.first().config
      assertEquals(setOf("example.reader"), config.whitelistPackages)
      assertEquals(setOf("example.video"), config.blacklistPackages)
    } finally {
      job.cancelAndJoin()
      directory.deleteRecursively()
    }
  }

  @Test fun invalidFilterModeFallsBackToWhitelist() = runBlocking {
    val directory = Files.createTempDirectory("gazekeep-config-test").toFile()
    val job = SupervisorJob()
    val dataStore = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) {
      directory.resolve("settings.preferences_pb")
    }
    try {
      dataStore.edit { it[FILTER_MODE] = "Everything" }
      assertEquals(AppFilterMode.Whitelist, ConfigStore(dataStore).settings.first().config.appFilterMode)
    } finally {
      job.cancelAndJoin()
      directory.deleteRecursively()
    }
  }

  @Test fun legacyKeyWrittenAfterDowngradeOverridesStaleFilterMode() = runBlocking {
    val directory = Files.createTempDirectory("gazekeep-config-test").toFile()
    val job = SupervisorJob()
    val dataStore = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) {
      directory.resolve("settings.preferences_pb")
    }
    try {
      // 降级后旧版改过开关会重新写入旧键，但不会删除新键；重新升级时以用户在旧版的最后选择为准。
      dataStore.edit {
        it[FILTER_MODE] = AppFilterMode.Blacklist.name
        it[LEGACY_WHITELIST] = false
      }
      assertEquals(AppFilterMode.All, ConfigStore(dataStore).settings.first().config.appFilterMode)
      dataStore.edit { it[LEGACY_WHITELIST] = true }
      assertEquals(AppFilterMode.Whitelist, ConfigStore(dataStore).settings.first().config.appFilterMode)
    } finally {
      job.cancelAndJoin()
      directory.deleteRecursively()
    }
  }

  @Test fun legacyMigrationClosesServiceAndSelectsWhitelistScope() {
    val migrated = migrateLegacy(emptyPreferences(), "Shizuku")
    assertEquals(false, migrated[booleanPreferencesKey("enabled")])
    assertEquals(BackendLock.Shizuku.name, migrated[stringPreferencesKey("backend_lock")])
    assertEquals(AppFilterMode.Whitelist.name, migrated[FILTER_MODE])
    assertEquals(true, migrated[ConfigStore.MIGRATED])
    assertEquals(
      BackendLock.Auto.name,
      migrateLegacy(emptyPreferences(), "Unknown")[stringPreferencesKey("backend_lock")],
    )
  }

  private companion object {
    val LEGACY_WHITELIST = booleanPreferencesKey("whitelist_enabled")
    val FILTER_MODE = stringPreferencesKey("app_filter_mode")
    val PACKAGES = stringSetPreferencesKey("packages")
  }
}
