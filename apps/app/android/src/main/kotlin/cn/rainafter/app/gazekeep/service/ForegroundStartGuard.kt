package cn.rainafter.app.gazekeep.service

import cn.rainafter.app.gazekeep.core.engine.Alert

/** 授权与实际前台提升之间仍可能失去资格；失败只停止本实例，不能反复粘性重启。 */
internal class ForegroundStartGuard(
  private val cameraGranted: () -> Boolean,
  private val promote: () -> Unit,
  private val release: () -> Unit,
  private val notify: (Alert) -> Unit,
  private val stop: () -> Unit,
) {
  private var failed = false

  fun ensureForeground(): Boolean {
    if (failed) return false
    if (!cameraGranted()) return fail(Alert.CameraPermissionLost)
    return try {
      promote()
      true
    } catch (_: Exception) {
      fail(if (cameraGranted()) Alert.BackendUnavailable else Alert.CameraPermissionLost)
    }
  }

  private fun fail(alert: Alert): Boolean {
    failed = true
    // 通知可能被权限拒绝，或旧会话释放失败；仍须兑现停止并避免保亮残留。
    runCatching(release)
    runCatching { notify(alert) }
    runCatching(stop)
    return false
  }
}
