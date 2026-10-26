package cn.rainafter.app.gazekeep.backend.shizuku

import cn.rainafter.app.gazekeep.backend.shizuku.protocol.ShellCommandRunner

internal object ShellFaultInjection {
  fun beforeUserActivity() = UserActivityFaultInjection.check { ShellCommandRunner.run("getprop", it) }
}
