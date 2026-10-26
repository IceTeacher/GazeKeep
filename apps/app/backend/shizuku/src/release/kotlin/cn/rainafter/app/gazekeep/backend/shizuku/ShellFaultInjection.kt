package cn.rainafter.app.gazekeep.backend.shizuku

/** Release 构建不包含故障注入；APK 门禁拒绝不可调试包中出现 UserActivityFaultInjection。 */
internal object ShellFaultInjection {
  fun beforeUserActivity() = Unit
}
