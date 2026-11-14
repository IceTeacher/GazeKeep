package cn.rainafter.app.gazekeep.service

import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.core.policy.EnableRequirement
import cn.rainafter.app.gazekeep.core.policy.enableRequirement

internal enum class QuickTileVisualState { Inactive, Active }

internal enum class QuickTileAction { OpenMainActivity, Disable, Enable }

internal sealed interface QuickTileSubtitle {
  data object NeedsCamera : QuickTileSubtitle
  data object NeedsSelfTest : QuickTileSubtitle
  data object Off : QuickTileSubtitle
  data object WaitingBackend : QuickTileSubtitle
  data class Running(val backend: BackendId) : QuickTileSubtitle
}

internal data class QuickTileDecision(
  val visualState: QuickTileVisualState,
  val action: QuickTileAction,
  val subtitle: QuickTileSubtitle,
)

/** 快捷磁贴与主开关共用相同的前置门控：缺少相机权限或自检未通过时只跳转应用，不绕过开启。 */
internal object QuickTileStateMapper {
  fun resolve(
    cameraGranted: Boolean,
    selfTestPassed: Boolean,
    enabled: Boolean,
    serviceRunning: Boolean,
    activeBackend: BackendId?,
  ): QuickTileDecision = when (enableRequirement(cameraGranted, selfTestPassed)) {
    EnableRequirement.Camera -> QuickTileDecision(
      visualState = QuickTileVisualState.Inactive,
      action = QuickTileAction.OpenMainActivity,
      subtitle = QuickTileSubtitle.NeedsCamera,
    )

    EnableRequirement.SelfTest -> QuickTileDecision(
      visualState = QuickTileVisualState.Inactive,
      action = QuickTileAction.OpenMainActivity,
      subtitle = QuickTileSubtitle.NeedsSelfTest,
    )

    EnableRequirement.Ready -> if (!enabled) {
      QuickTileDecision(
        visualState = QuickTileVisualState.Inactive,
        action = QuickTileAction.Enable,
        subtitle = QuickTileSubtitle.Off,
      )
    } else {
      QuickTileDecision(
        visualState = QuickTileVisualState.Active,
        action = QuickTileAction.Disable,
        subtitle = if (serviceRunning && activeBackend != null) {
          QuickTileSubtitle.Running(activeBackend)
        } else {
          QuickTileSubtitle.WaitingBackend
        },
      )
    }
  }

  /** Android 14+ 禁止从仅持磁贴临时豁免的后台直接启动 `camera` 类型前台服务，需先拉起前台界面。 */
  fun requiresForegroundActivityToEnable(sdkInt: Int): Boolean = sdkInt >= 34
}
