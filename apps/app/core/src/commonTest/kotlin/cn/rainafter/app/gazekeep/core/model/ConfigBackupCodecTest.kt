package cn.rainafter.app.gazekeep.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ConfigBackupCodecTest {
  @Test
  fun roundTripPreservesAllAllowedFieldsAndOmitsSensitiveOnes() {
    val source = GazeConfig(
      enabled = true,
      backendLock = BackendLock.Lsposed,
      leadMillis = 12_000L,
      sensitivity = Sensitivity.Loose,
      appFilterMode = AppFilterMode.Blacklist,
      whitelistPackages = setOf("com.zeta.reader", "com.alpha.notes"),
      blacklistPackages = setOf("com.video.player"),
      maxHoldMinutes = 45,
      pauseOnPowerSave = false,
      lowBatteryThreshold = 20,
      debugLogEnabled = true,
      hideCameraIndicator = true,
    )

    val json = assertIs<ConfigExportResult.Success>(ConfigBackupCodec.encode(source)).json
    assertFalse(json.contains("\"enabled\""), "backup must never export enabled")
    assertFalse(json.contains("self_test"), "backup must never export self_test_passed_at")
    assertFalse(json.contains("selfTest"), "backup must never export selfTestPassedAt")
    assertFalse(json.contains("pendingTimeoutRestore"), "backup must never export pendingTimeoutRestore")
    assertTrue(json.contains("\"schemaVersion\": 1"))

    val current = GazeConfig(enabled = false)
    val decoded = assertIs<ConfigImportResult.Success>(ConfigBackupCodec.decode(json, current))
    assertEquals(source.copy(enabled = false), decoded.config)
  }

  @Test
  fun decodeIgnoresSensitiveAndFutureUnknownFields() {
    val json = """
      {
        "schemaVersion": 1,
        "enabled": true,
        "self_test_passed_at": 1760000000000,
        "selfTestPassedAt": 1760000000000,
        "pendingTimeoutRestore": 60000,
        "unlimitedHoldWhileCharging": true,
        "faceDownGateEnabled": false,
        "futureNested": { "a": [1, true, null, "ok"] },
        "leadMillis": 5000,
        "sensitivity": "Loose"
      }
    """.trimIndent()

    val current = GazeConfig(enabled = false, maxHoldMinutes = 60, whitelistPackages = setOf("com.keep.me"))
    val decoded = assertIs<ConfigImportResult.Success>(ConfigBackupCodec.decode(json, current))
    assertFalse(decoded.config.enabled)
    assertEquals(5_000L, decoded.config.leadMillis)
    assertEquals(Sensitivity.Loose, decoded.config.sensitivity)
    assertEquals(60, decoded.config.maxHoldMinutes)
    assertEquals(setOf("com.keep.me"), decoded.config.whitelistPackages)
  }

  @Test
  fun decodeSanitizesPackageNamesAndCapsListSizeAtFiveHundred() {
    val validPackages = (1..520).map { index -> "com.example.app$index" }
    val invalidPackages = listOf(
      "",
      "singleword",
      "123.starts.digit",
      "com.bad space.app",
      "com.bad/slash.app",
      "com..empty.segment",
      "com.trailing.",
      "com." + "a".repeat(260),
    )
    val items = (invalidPackages + validPackages + listOf("com.example.app1"))
      .joinToString(",") { "\"$it\"" }
    val json = """
      {
        "schemaVersion": 1,
        "whitelistPackages": [$items],
        "blacklistPackages": ["com.ok.pkg", "not_valid"]
      }
    """.trimIndent()

    val decoded = assertIs<ConfigImportResult.Success>(ConfigBackupCodec.decode(json, GazeConfig()))
    assertEquals(ConfigBackupCodec.MAX_PACKAGE_LIST_SIZE, decoded.config.whitelistPackages.size)
    assertTrue("com.example.app1" in decoded.config.whitelistPackages)
    assertTrue("com.example.app500" in decoded.config.whitelistPackages)
    assertFalse("com.example.app501" in decoded.config.whitelistPackages)
    assertEquals(setOf("com.ok.pkg"), decoded.config.blacklistPackages)
  }

  @Test
  fun decodeRejectsMalformedSchemaValuesTypesAndOversizedInput() {
    val badInputs = listOf(
      "",
      "[]",
      "{",
      """{"schemaVersion": 1} trailing""",
      """{"leadMillis": 5000}""",
      """{"schemaVersion": 0}""",
      """{"schemaVersion": 2}""",
      """{"schemaVersion": 1.5}""",
      """{"schemaVersion": "1"}""",
      """{"schemaVersion": 1, "backendLock": "Unknown"}""",
      """{"schemaVersion": 1, "leadMillis": -1000}""",
      """{"schemaVersion": 1, "leadMillis": 61000}""",
      """{"schemaVersion": 1, "leadMillis": 5500}""",
      """{"schemaVersion": 1, "sensitivity": "Strict"}""",
      """{"schemaVersion": 1, "appFilterMode": "Custom"}""",
      """{"schemaVersion": 1, "whitelistPackages": "com.example.app"}""",
      """{"schemaVersion": 1, "whitelistPackages": [123]}""",
      """{"schemaVersion": 1, "maxHoldMinutes": -1}""",
      """{"schemaVersion": 1, "maxHoldMinutes": 1441}""",
      """{"schemaVersion": 1, "pauseOnPowerSave": "true"}""",
      """{"schemaVersion": 1, "lowBatteryThreshold": 12}""",
      """{"schemaVersion": 1, "debugLogEnabled": 1}""",
      """{"schemaVersion": 1, "hideCameraIndicator": null}""",
    )
    for (input in badInputs) {
      assertIs<ConfigImportResult.InvalidFormat>(
        ConfigBackupCodec.decode(input, GazeConfig()),
        "expected InvalidFormat for: $input",
      )
    }

    val oversized = """{"schemaVersion": 1, "padding": "${"a".repeat(ConfigBackupCodec.MAX_BACKUP_BYTES)}"}"""
    assertIs<ConfigImportResult.InvalidFormat>(ConfigBackupCodec.decode(oversized, GazeConfig()))
  }

  @Test
  fun encodeMaxCapacityListsStaysWithinImportByteLimitAndRoundTrips() {
    val maxSegment = "a".repeat(240)
    val maxWhitelist = (1..ConfigBackupCodec.MAX_PACKAGE_LIST_SIZE)
      .map { i -> "com.wl$i.$maxSegment".take(ConfigBackupCodec.MAX_PACKAGE_NAME_LENGTH) }
    val maxBlacklist = (1..ConfigBackupCodec.MAX_PACKAGE_LIST_SIZE)
      .map { i -> "com.bl$i.$maxSegment".take(ConfigBackupCodec.MAX_PACKAGE_NAME_LENGTH) }
    val source = GazeConfig(
      whitelistPackages = (maxWhitelist + listOf("invalid pkg")).toSet(),
      blacklistPackages = (maxBlacklist + listOf("singleword")).toSet(),
    )

    val json = assertIs<ConfigExportResult.Success>(ConfigBackupCodec.encode(source)).json
    assertTrue(
      json.encodeToByteArray().size <= ConfigBackupCodec.MAX_BACKUP_BYTES,
      "encoded backup (${json.encodeToByteArray().size} B) exceeded MAX_BACKUP_BYTES (${ConfigBackupCodec.MAX_BACKUP_BYTES} B)",
    )

    val decoded = assertIs<ConfigImportResult.Success>(ConfigBackupCodec.decode(json, GazeConfig()))
    assertEquals(ConfigBackupCodec.MAX_PACKAGE_LIST_SIZE, decoded.config.whitelistPackages.size)
    assertEquals(ConfigBackupCodec.MAX_PACKAGE_LIST_SIZE, decoded.config.blacklistPackages.size)
    assertEquals(maxWhitelist.toSet(), decoded.config.whitelistPackages)
    assertEquals(maxBlacklist.toSet(), decoded.config.blacklistPackages)
  }

  @Test
  fun encodeRefusesLossyExportWhenValidPackageListExceedsLimit() {
    val overLimit = (1..(ConfigBackupCodec.MAX_PACKAGE_LIST_SIZE + 1))
      .map { i -> "com.example.app$i" }
      .toSet()

    val wlResult = assertIs<ConfigExportResult.PackageListTooLarge>(
      ConfigBackupCodec.encode(GazeConfig(whitelistPackages = overLimit)),
    )
    assertEquals(ConfigBackupCodec.MAX_PACKAGE_LIST_SIZE, wlResult.limit)

    val blResult = assertIs<ConfigExportResult.PackageListTooLarge>(
      ConfigBackupCodec.encode(GazeConfig(blacklistPackages = overLimit)),
    )
    assertEquals(ConfigBackupCodec.MAX_PACKAGE_LIST_SIZE, blResult.limit)
  }
}
