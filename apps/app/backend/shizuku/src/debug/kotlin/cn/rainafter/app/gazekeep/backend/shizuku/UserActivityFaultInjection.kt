package cn.rainafter.app.gazekeep.backend.shizuku

/**
 * 仅 Debug 构建：系统属性为 1 时让 userActivity 抛出 SecurityException，用于在 shell 身份具备
 * DEVICE_POWER 的设备上实机验证临时超时退路与原值写回。读取失败按未启用处理。
 */
internal object UserActivityFaultInjection {
  const val PROPERTY = "debug.gazekeep.deny_user_activity"

  fun check(read: (String) -> String) {
    val value = runCatching { read(PROPERTY) }.getOrDefault("")
    if (value.trim() == "1") throw SecurityException("userActivity denied by $PROPERTY")
  }
}
