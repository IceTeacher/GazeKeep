package cn.rainafter.app.gazekeep.core.model

sealed interface ConfigExportResult {
  data class Success(val json: String) : ConfigExportResult
  data class PackageListTooLarge(val limit: Int) : ConfigExportResult
}

sealed interface ConfigImportResult {
  data class Success(val config: GazeConfig) : ConfigImportResult
  data class InvalidFormat(val reason: String) : ConfigImportResult
}

/**
 * 纯 Kotlin 的配置备份 JSON 编解码器：零外部依赖，兼容全 KMP 目标。
 * 绝不导出或恢复 enabled、self_test_passed_at 与 pendingTimeoutRestore 等运行时/安全状态。
 */
object ConfigBackupCodec {
  const val CURRENT_SCHEMA_VERSION = 1

  /** 涵盖双名单各 500 项、单条 255 字节的最坏合法备份体积（~257 KB）并防止大文件 OOM。 */
  const val MAX_BACKUP_BYTES = 512 * 1024
  const val MAX_PACKAGE_LIST_SIZE = 500
  const val MAX_PACKAGE_NAME_LENGTH = 255

  private const val MAX_JSON_DEPTH = 16
  private const val MAX_LEAD_MILLIS = 60_000L
  private const val MAX_HOLD_MINUTES = 1_440

  fun encode(config: GazeConfig): ConfigExportResult {
    val whitelist = sanitizePackagesForExport(config.whitelistPackages)
      ?: return ConfigExportResult.PackageListTooLarge(MAX_PACKAGE_LIST_SIZE)
    val blacklist = sanitizePackagesForExport(config.blacklistPackages)
      ?: return ConfigExportResult.PackageListTooLarge(MAX_PACKAGE_LIST_SIZE)
    val json = buildString {
      append("{\n")
      appendField("schemaVersion", CURRENT_SCHEMA_VERSION.toString(), trailingComma = true)
      appendField("backendLock", encodeString(config.backendLock.name), trailingComma = true)
      appendField("leadMillis", config.leadMillis.toString(), trailingComma = true)
      appendField("sensitivity", encodeString(config.sensitivity.name), trailingComma = true)
      appendField("appFilterMode", encodeString(config.appFilterMode.name), trailingComma = true)
      appendField("whitelistPackages", encodeStringArray(whitelist), trailingComma = true)
      appendField("blacklistPackages", encodeStringArray(blacklist), trailingComma = true)
      appendField("maxHoldMinutes", config.maxHoldMinutes.toString(), trailingComma = true)
      appendField("pauseOnPowerSave", config.pauseOnPowerSave.toString(), trailingComma = true)
      appendField("lowBatteryThreshold", config.lowBatteryThreshold.toString(), trailingComma = true)
      appendField("debugLogEnabled", config.debugLogEnabled.toString(), trailingComma = true)
      appendField("hideCameraIndicator", config.hideCameraIndicator.toString(), trailingComma = false)
      append("}\n")
    }
    return ConfigExportResult.Success(json)
  }

  private fun sanitizePackagesForExport(packages: Set<String>): List<String>? {
    val valid = packages.asSequence().filter(::isValidAndroidPackageName).sorted().toList()
    return valid.takeIf { it.size <= MAX_PACKAGE_LIST_SIZE }
  }

  fun decode(json: String, current: GazeConfig = GazeConfig()): ConfigImportResult {
    if (json.encodeToByteArray().size > MAX_BACKUP_BYTES) {
      return ConfigImportResult.InvalidFormat("backup exceeds $MAX_BACKUP_BYTES bytes")
    }
    val parsed = try {
      JsonParser(json).parseRootObject()
    } catch (error: JsonSyntaxException) {
      return ConfigImportResult.InvalidFormat(error.message ?: "invalid json")
    }

    val schemaVersion = parsed["schemaVersion"] as? JsonValue.IntNumber
      ?: return ConfigImportResult.InvalidFormat("missing or invalid schemaVersion")
    if (schemaVersion.value != CURRENT_SCHEMA_VERSION.toLong()) {
      return ConfigImportResult.InvalidFormat("unsupported schemaVersion: ${schemaVersion.value}")
    }

    val backendLock = parseEnum(parsed, "backendLock", current.backendLock, BackendLock.entries)
      ?: return ConfigImportResult.InvalidFormat("invalid backendLock")
    val leadMillis = parseLongInRange(parsed, "leadMillis", current.leadMillis) {
      it in 0L..MAX_LEAD_MILLIS && it % 1_000L == 0L
    } ?: return ConfigImportResult.InvalidFormat("invalid leadMillis")
    val sensitivity = parseEnum(parsed, "sensitivity", current.sensitivity, Sensitivity.entries)
      ?: return ConfigImportResult.InvalidFormat("invalid sensitivity")
    val appFilterMode = parseEnum(parsed, "appFilterMode", current.appFilterMode, AppFilterMode.entries)
      ?: return ConfigImportResult.InvalidFormat("invalid appFilterMode")
    val whitelistPackages = parsePackageSet(parsed, "whitelistPackages", current.whitelistPackages)
      ?: return ConfigImportResult.InvalidFormat("invalid whitelistPackages")
    val blacklistPackages = parsePackageSet(parsed, "blacklistPackages", current.blacklistPackages)
      ?: return ConfigImportResult.InvalidFormat("invalid blacklistPackages")
    val maxHoldMinutes = parseLongInRange(parsed, "maxHoldMinutes", current.maxHoldMinutes.toLong()) {
      it in 0L..MAX_HOLD_MINUTES.toLong()
    }?.toInt() ?: return ConfigImportResult.InvalidFormat("invalid maxHoldMinutes")
    val pauseOnPowerSave = parseBoolean(parsed, "pauseOnPowerSave", current.pauseOnPowerSave)
      ?: return ConfigImportResult.InvalidFormat("invalid pauseOnPowerSave")
    val lowBatteryThreshold = parseLongInRange(parsed, "lowBatteryThreshold", current.lowBatteryThreshold.toLong()) {
      it in 0L..100L && it.toInt() in GazeConfig.LOW_BATTERY_LEVELS
    }?.toInt() ?: return ConfigImportResult.InvalidFormat("invalid lowBatteryThreshold")
    val debugLogEnabled = parseBoolean(parsed, "debugLogEnabled", current.debugLogEnabled)
      ?: return ConfigImportResult.InvalidFormat("invalid debugLogEnabled")
    val hideCameraIndicator = parseBoolean(parsed, "hideCameraIndicator", current.hideCameraIndicator)
      ?: return ConfigImportResult.InvalidFormat("invalid hideCameraIndicator")

    // 显式保留当前设备的 enabled 状态，外部 JSON 严禁越权开启服务。
    return ConfigImportResult.Success(
      GazeConfig(
        enabled = current.enabled,
        backendLock = backendLock,
        leadMillis = leadMillis,
        sensitivity = sensitivity,
        appFilterMode = appFilterMode,
        whitelistPackages = whitelistPackages,
        blacklistPackages = blacklistPackages,
        maxHoldMinutes = maxHoldMinutes,
        pauseOnPowerSave = pauseOnPowerSave,
        lowBatteryThreshold = lowBatteryThreshold,
        debugLogEnabled = debugLogEnabled,
        hideCameraIndicator = hideCameraIndicator,
      ),
    )
  }

  private fun <T : Enum<T>> parseEnum(map: Map<String, JsonValue>, key: String, fallback: T, entries: List<T>): T? {
    if (key !in map) return fallback
    val value = (map[key] as? JsonValue.Str)?.value ?: return null
    return entries.firstOrNull { it.name == value }
  }

  private fun parseLongInRange(
    map: Map<String, JsonValue>,
    key: String,
    fallback: Long,
    predicate: (Long) -> Boolean,
  ): Long? {
    if (key !in map) return fallback
    val value = (map[key] as? JsonValue.IntNumber)?.value ?: return null
    return value.takeIf(predicate)
  }

  private fun parseBoolean(map: Map<String, JsonValue>, key: String, fallback: Boolean): Boolean? {
    if (key !in map) return fallback
    return (map[key] as? JsonValue.Bool)?.value
  }

  private fun parsePackageSet(map: Map<String, JsonValue>, key: String, fallback: Set<String>): Set<String>? {
    if (key !in map) return fallback
    val array = map[key] as? JsonValue.Arr ?: return null
    val result = LinkedHashSet<String>()
    for (element in array.items) {
      val name = (element as? JsonValue.Str)?.value ?: return null
      if (result.size < MAX_PACKAGE_LIST_SIZE && isValidAndroidPackageName(name)) {
        result += name
      }
    }
    return result
  }

  internal fun isValidAndroidPackageName(name: String): Boolean {
    if (name.isEmpty() || name.length > MAX_PACKAGE_NAME_LENGTH) return false
    var segments = 0
    var index = 0
    while (index < name.length) {
      val dot = name.indexOf('.', index)
      val end = if (dot == -1) name.length else dot
      if (end == index) return false
      if (segments == 0 && !isAsciiLetter(name[index])) return false
      for (pos in index until end) {
        val ch = name[pos]
        if (!isAsciiLetter(ch) && ch !in '0'..'9' && ch != '_') return false
      }
      segments++
      if (dot == -1) break
      if (dot == name.lastIndex) return false
      index = dot + 1
    }
    return segments >= 2
  }

  private fun isAsciiLetter(ch: Char): Boolean = ch in 'a'..'z' || ch in 'A'..'Z'

  private fun StringBuilder.appendField(name: String, rawValue: String, trailingComma: Boolean) {
    append("  ")
    append(encodeString(name))
    append(": ")
    append(rawValue)
    if (trailingComma) append(',')
    append('\n')
  }

  private fun encodeStringArray(values: List<String>): String {
    if (values.isEmpty()) return "[]"
    return buildString {
      append("[\n")
      values.forEachIndexed { index, item ->
        append("    ")
        append(encodeString(item))
        if (index != values.lastIndex) append(',')
        append('\n')
      }
      append("  ]")
    }
  }

  private fun encodeString(value: String): String = buildString(value.length + 2) {
    append('"')
    for (ch in value) {
      when (ch) {
        '"' -> append("\\\"")

        '\\' -> append("\\\\")

        '\b' -> append("\\b")

        '\u000C' -> append("\\f")

        '\n' -> append("\\n")

        '\r' -> append("\\r")

        '\t' -> append("\\t")

        else -> if (ch.code < 0x20) {
          append("\\u")
          append(ch.code.toString(16).padStart(4, '0'))
        } else {
          append(ch)
        }
      }
    }
    append('"')
  }

  private sealed interface JsonValue {
    data class Obj(val entries: Map<String, JsonValue>) : JsonValue
    data class Arr(val items: List<JsonValue>) : JsonValue
    data class Str(val value: String) : JsonValue
    data class Bool(val value: Boolean) : JsonValue
    data class IntNumber(val value: Long) : JsonValue
    data object FloatNumber : JsonValue
    data object Null : JsonValue
  }

  private class JsonSyntaxException(message: String) : IllegalArgumentException(message)

  private class JsonParser(private val input: String) {
    private var pos = 0

    fun parseRootObject(): Map<String, JsonValue> {
      skipWhitespace()
      val value = parseValue(depth = 0)
      skipWhitespace()
      if (pos != input.length) throw JsonSyntaxException("unexpected trailing content")
      return (value as? JsonValue.Obj)?.entries ?: throw JsonSyntaxException("root must be a json object")
    }

    private fun parseValue(depth: Int): JsonValue {
      if (depth > MAX_JSON_DEPTH) throw JsonSyntaxException("json nesting too deep")
      skipWhitespace()
      if (pos >= input.length) throw JsonSyntaxException("unexpected end of json")
      return when (input[pos]) {
        '{' -> parseObject(depth + 1)
        '[' -> parseArray(depth + 1)
        '"' -> JsonValue.Str(parseString())
        't' -> parseLiteral("true", JsonValue.Bool(true))
        'f' -> parseLiteral("false", JsonValue.Bool(false))
        'n' -> parseLiteral("null", JsonValue.Null)
        '-', in '0'..'9' -> parseNumber()
        else -> throw JsonSyntaxException("unexpected character at $pos")
      }
    }

    private fun parseObject(depth: Int): JsonValue.Obj {
      pos++ // '{'
      skipWhitespace()
      val entries = LinkedHashMap<String, JsonValue>()
      if (peek() == '}') {
        pos++
        return JsonValue.Obj(entries)
      }
      while (true) {
        skipWhitespace()
        if (peek() != '"') throw JsonSyntaxException("expected string key at $pos")
        val key = parseString()
        skipWhitespace()
        if (peek() != ':') throw JsonSyntaxException("expected ':' at $pos")
        pos++
        entries[key] = parseValue(depth)
        skipWhitespace()
        when (peek()) {
          ',' -> pos++

          '}' -> {
            pos++
            return JsonValue.Obj(entries)
          }

          else -> throw JsonSyntaxException("expected ',' or '}' at $pos")
        }
      }
    }

    private fun parseArray(depth: Int): JsonValue.Arr {
      pos++ // '['
      skipWhitespace()
      val items = ArrayList<JsonValue>()
      if (peek() == ']') {
        pos++
        return JsonValue.Arr(items)
      }
      while (true) {
        items += parseValue(depth)
        skipWhitespace()
        when (peek()) {
          ',' -> pos++

          ']' -> {
            pos++
            return JsonValue.Arr(items)
          }

          else -> throw JsonSyntaxException("expected ',' or ']' at $pos")
        }
      }
    }

    private fun parseString(): String {
      pos++ // opening '"'
      val out = StringBuilder()
      while (pos < input.length) {
        val ch = input[pos++]
        when {
          ch == '"' -> return out.toString()

          ch == '\\' -> {
            if (pos >= input.length) throw JsonSyntaxException("unterminated escape")
            when (val esc = input[pos++]) {
              '"' -> out.append('"')

              '\\' -> out.append('\\')

              '/' -> out.append('/')

              'b' -> out.append('\b')

              'f' -> out.append('\u000C')

              'n' -> out.append('\n')

              'r' -> out.append('\r')

              't' -> out.append('\t')

              'u' -> {
                if (pos + 4 > input.length) throw JsonSyntaxException("invalid unicode escape")
                val hex = input.substring(pos, pos + 4)
                val code = hex.toIntOrNull(16) ?: throw JsonSyntaxException("invalid unicode escape")
                out.append(code.toChar())
                pos += 4
              }

              else -> throw JsonSyntaxException("invalid escape: \\$esc")
            }
          }

          ch.code < 0x20 -> throw JsonSyntaxException("unescaped control character")

          else -> out.append(ch)
        }
      }
      throw JsonSyntaxException("unterminated string")
    }

    private fun parseLiteral(literal: String, value: JsonValue): JsonValue {
      if (!input.startsWith(literal, pos)) throw JsonSyntaxException("invalid token at $pos")
      pos += literal.length
      return value
    }

    private fun parseNumber(): JsonValue {
      val start = pos
      if (peek() == '-') pos++
      if (pos >= input.length) throw JsonSyntaxException("invalid number")
      if (input[pos] == '0') {
        pos++
      } else if (input[pos] in '1'..'9') {
        while (pos < input.length && input[pos] in '0'..'9') pos++
      } else {
        throw JsonSyntaxException("invalid number at $pos")
      }
      var isFloat = false
      if (peek() == '.') {
        isFloat = true
        pos++
        if (pos >= input.length || input[pos] !in '0'..'9') throw JsonSyntaxException("invalid fraction")
        while (pos < input.length && input[pos] in '0'..'9') pos++
      }
      if (peek() == 'e' || peek() == 'E') {
        isFloat = true
        pos++
        if (peek() == '+' || peek() == '-') pos++
        if (pos >= input.length || input[pos] !in '0'..'9') throw JsonSyntaxException("invalid exponent")
        while (pos < input.length && input[pos] in '0'..'9') pos++
      }
      if (isFloat) return JsonValue.FloatNumber
      val parsed = input.substring(start, pos).toLongOrNull()
        ?: throw JsonSyntaxException("integer overflow")
      return JsonValue.IntNumber(parsed)
    }

    private fun skipWhitespace() {
      while (pos < input.length) {
        when (input[pos]) {
          ' ', '\n', '\r', '\t' -> pos++
          else -> return
        }
      }
    }

    private fun peek(): Char? = if (pos < input.length) input[pos] else null
  }
}
