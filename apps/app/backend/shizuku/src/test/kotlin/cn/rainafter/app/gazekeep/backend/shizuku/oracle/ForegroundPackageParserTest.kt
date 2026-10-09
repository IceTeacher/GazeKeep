package cn.rainafter.app.gazekeep.backend.shizuku.oracle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ForegroundPackageParserTest {
  @Test fun onlyResumedActivitiesIdentifyForeground() {
    assertEquals(
      "example.reader",
      ForegroundPackageParser.parse("mResumedActivity: ActivityRecord{abc u0 example.reader/.Main t7}"),
    )
    assertNull(ForegroundPackageParser.parse("Hist #0: ActivityRecord{abc u0 example.reader/.Main t7}"))
  }

  @Test fun conflictingDisplaysAndEmptyOutputRemainUnknown() {
    assertNull(ForegroundPackageParser.parse(""))
    assertNull(
      ForegroundPackageParser.parse(
        "mResumedActivity: ActivityRecord{abc u0 example.reader/.Main t7}\n mResumedActivity: ActivityRecord{def u0 other.app/.Main t8}",
      ),
    )
  }
}
