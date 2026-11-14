package cn.rainafter.app.gazekeep.backend.shizuku.oracle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PowerDumpParserTest {
  @Test
  fun prefersEffectiveTimeoutWhenSystemOverridesUserSetting() {
    val snapshot = PowerDumpParser.parse(
      """
            mWakefulness=Awake
            mLastUserActivityTime=1000
            mScreenOffTimeoutSetting=60000
            Screen off timeout: 30000 ms
            Screen dim duration: 9000 ms
      """.trimIndent(),
    )
    assertEquals(30000, snapshot.screenOffTimeoutMs)
    assertEquals(9000, snapshot.dimDurationMs)
  }

  @Test
  fun parsesPowerGroupFieldsAndWakeLock() {
    val snapshot = PowerDumpParser.parse(
      """
            mWakefulness=Awake
            mInteractive=true
            mLastUserActivityTime=123456
            mScreenOffTimeoutSetting=60000
            Screen dim duration: 7000
            Wake Locks: SCREEN_BRIGHT_WAKE_LOCK='video'
      """.trimIndent(),
    )
    assertEquals(123456L, snapshot.lastUserActivityUptime)
    assertEquals(60000, snapshot.screenOffTimeoutMs)
    assertEquals(7000, snapshot.dimDurationMs)
    assertTrue(snapshot.hasScreenWakeLock)
    assertTrue(snapshot.interactive)
  }

  @Test
  fun missingFieldsRemainUnknownAndDoNotAssumeInteractive() {
    val snapshot = PowerDumpParser.parse("mWakefulness=Dozing")
    assertEquals(null, snapshot.lastUserActivityUptime)
    assertEquals(null, snapshot.screenOffTimeoutMs)
    assertFalse(snapshot.hasScreenWakeLock)
    assertFalse(snapshot.interactive)
  }

  @Test
  fun parsesHyperOsLastActivityWithAttentionSuffix() {
    val snapshot = PowerDumpParser.parse(
      """
            mWakefulness=Awake
            mScreenOffTimeoutSetting=15000
            mLastUserActivityTime(excludingAttention)=80253010
      """.trimIndent(),
    )

    assertEquals(80253010L, snapshot.lastUserActivityUptime)
    assertEquals(15000, snapshot.screenOffTimeoutMs)
    assertTrue(snapshot.interactive)
  }

  @Test
  fun parsesNoChangeLightsSeparatelyWithoutReplacingNormalUserActivity() {
    val snapshot = PowerDumpParser.parse(
      """
        mWakefulness=Awake
        mScreenOffTimeoutSetting=15000
        Screen off timeout: 15000 ms
        Screen dim duration: 3000 ms
         mLastUserActivityTime(excludingAttention)=35350
          lastUserActivityTime=27180 (1257054 ms ago)
          lastUserActivityTimeNoChangeLights=35350 (1248884 ms ago)
      """.trimIndent(),
    )

    assertEquals(27180L, snapshot.lastUserActivityUptime)
    assertEquals(35350L, snapshot.lastUserActivityNoChangeLightsUptime)
    assertEquals(35350L, snapshot.effectiveLastActivityUptime)
  }

  @Test
  fun parsesLegacyNoChangeLightsAlongsideGroupLastActivity() {
    val snapshot = PowerDumpParser.parse(
      """
        mWakefulness=Awake
        mScreenOffTimeoutSetting=30000
        mLastUserActivityTime=20000
        lastUserActivityTimeNoChangeLights=45000
      """.trimIndent(),
    )

    assertEquals(20000L, snapshot.lastUserActivityUptime)
    assertEquals(45000L, snapshot.lastUserActivityNoChangeLightsUptime)
    assertEquals(45000L, snapshot.effectiveLastActivityUptime)
  }
}
