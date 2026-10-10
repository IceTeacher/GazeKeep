package cn.rainafter.app.gazekeep.notification

import cn.rainafter.app.gazekeep.core.engine.DecisionReason
import cn.rainafter.app.gazekeep.core.engine.DecisionRecord
import cn.rainafter.app.gazekeep.core.model.Attention
import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.ui.contract.LogLevel
import org.junit.Test
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DecisionLogStoreTest {
  @Test fun disabledByDefaultAndSwitchingOffDeletesRecords() {
    val dir = Files.createTempDirectory("gaze-log").toFile()
    try {
      val store = DecisionLogStore(dir) { 100L }
      val record = DecisionRecord(10L, "example.reader", Attention.Unknown, DecisionReason.Unknown, null, false, 20L)
      store.append(record)
      assertEquals(0, dir.listFiles()!!.size)
      assertTrue(store.readEntries().isEmpty())
      store.setEnabled(true)
      store.append(record)
      assertEquals(1, dir.listFiles()!!.size)
      assertEquals(1, store.readEntries().size)
      store.setEnabled(false)
      assertEquals(0, dir.listFiles()!!.size)
      assertTrue(store.readEntries().isEmpty())
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test fun retentionBoundaryAndUntrustedPackageCannotLeakExtraFields() {
    val dir = Files.createTempDirectory("gaze-log").toFile()
    var now = 100L
    try {
      val store = DecisionLogStore(dir) { now }
      store.setEnabled(true)
      store.append(DecisionRecord(1L, "bad\nprivate", Attention.Unknown, DecisionReason.Unknown, null, false, null))
      assertTrue(!dir.listFiles()!!.single().readText().contains("private"))
      now += DecisionLogStore.RETENTION_MS - 1
      store.cleanup()
      assertEquals(1, dir.listFiles()!!.size)
      now++
      store.cleanup()
      assertEquals(0, dir.listFiles()!!.size)
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test fun readsStructuredDecisionAndRuntimeLogsAndSupportsClear() {
    val dir = Files.createTempDirectory("gaze-log").toFile()
    var now = 1_000L
    try {
      val store = DecisionLogStore(dir) { now }
      store.setEnabled(true)
      store.appendLog(LogLevel.Info, "GazeEngine", "system", "state=Armed backend=Lsposed")
      now = 2_000L
      store.append(
        DecisionRecord(
          uptime = 500L,
          foregroundPackage = "example.reader",
          attention = Attention.Looking,
          reason = DecisionReason.Extended,
          backend = BackendId.Lsposed,
          extended = true,
          latencyMs = 120L,
        ),
      )
      val entries = store.readEntries()
      assertEquals(2, entries.size)
      assertEquals(LogLevel.Info, entries[0].level)
      assertEquals("GazeEngine", entries[0].tag)
      assertEquals("system", entries[0].target)
      assertTrue(entries[0].message.contains("state=Armed"))
      assertEquals(LogLevel.Info, entries[1].level)
      assertEquals("example.reader", entries[1].target)
      assertTrue(entries[1].message.contains("Extended"))
      assertTrue(entries[1].message.contains("Looking"))

      store.clear()
      assertTrue(store.readEntries().isEmpty())
    } finally {
      dir.deleteRecursively()
    }
  }
}
