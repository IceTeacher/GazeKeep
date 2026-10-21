package cn.rainafter.app.gazekeep.platform

import java.time.Instant
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class LogTimestampTest {
  @Test fun formatsUtc8AcrossLeapDayAndYearRegardlessOfDeviceTimezone() {
    val original = TimeZone.getDefault()
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
      assertEquals("02-29 00:00:00", formatLogTimestampUtc8(Instant.parse("2024-02-28T16:00:00Z").toEpochMilli()))
      assertEquals("01-01 00:00:00", formatLogTimestampUtc8(Instant.parse("2025-12-31T16:00:00Z").toEpochMilli()))
      assertEquals("01-01 08:00:00", formatLogTimestampUtc8(0L))
    } finally {
      TimeZone.setDefault(original)
    }
  }
}
