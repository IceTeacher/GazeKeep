package cn.rainafter.app.gazekeep.platform

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val logTimestampFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss", Locale.ROOT)
  .withZone(ZoneOffset.ofHours(8))

internal fun formatLogTimestampUtc8(epochMillis: Long): String =
  logTimestampFormatter.format(Instant.ofEpochMilli(epochMillis))
