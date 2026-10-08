package cn.rainafter.app.gazekeep.engine.config

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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
      assertTrue(initial.config.whitelistEnabled)
      assertTrue(initial.config.packages.isEmpty())
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
      packages = setOf("example.reader"),
      maxHoldMinutes = 10,
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
      assertFailsWith<IllegalArgumentException> { store.update { it.copy(leadMillis = 6_000L) } }
      assertEquals(saved, store.settings.first())
    } finally {
      job.cancelAndJoin()
      directory.deleteRecursively()
    }
  }
}
