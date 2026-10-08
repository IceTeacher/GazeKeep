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
}
