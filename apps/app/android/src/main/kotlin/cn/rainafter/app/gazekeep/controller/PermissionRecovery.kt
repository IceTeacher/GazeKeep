package cn.rainafter.app.gazekeep.controller

/**
 * 撤销相机权限会让服务停在不可运行状态；用户在系统设置重新授权后，
 * 只要配置仍处于开启且自检已通过，就应恢复运行，不要求手动关开总开关。
 */
internal fun shouldResumeAfterCameraGrant(
  wasGranted: Boolean,
  granted: Boolean,
  enabled: Boolean,
  selfTestPassed: Boolean,
): Boolean = !wasGranted && granted && enabled && selfTestPassed
