package cn.rainafter.app.gazekeep.notification

import cn.rainafter.app.gazekeep.core.engine.DecisionReason
import cn.rainafter.app.gazekeep.core.engine.DecisionRecord
import cn.rainafter.app.gazekeep.core.model.Attention
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
      store.setEnabled(true)
      store.append(record)
      assertEquals(1, dir.listFiles()!!.size)
      store.setEnabled(false)
      assertEquals(0, dir.listFiles()!!.size)
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
}
