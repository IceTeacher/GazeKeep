package cn.rainafter.app.gazekeep.ui.text

import androidx.compose.runtime.Composable
import cn.rainafter.app.gazekeep.core.model.Attention
import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.core.model.BackendLock
import cn.rainafter.app.gazekeep.core.model.Readiness
import cn.rainafter.app.gazekeep.core.model.SetupStep
import cn.rainafter.app.gazekeep.ui.contract.UiNotice
import gazekeep.shared.generated.resources.Res
import gazekeep.shared.generated.resources.m0_result_away
import gazekeep.shared.generated.resources.m0_result_looking
import gazekeep.shared.generated.resources.m0_result_unknown
import gazekeep.shared.generated.resources.m2_accessibility_unavailable
import gazekeep.shared.generated.resources.m2_shizuku_needs_permission
import gazekeep.shared.generated.resources.m2_shizuku_needs_start
import gazekeep.shared.generated.resources.m3_lsposed_unavailable
import gazekeep.shared.generated.resources.settings_accessibility
import gazekeep.shared.generated.resources.settings_auto
import gazekeep.shared.generated.resources.settings_empty_scope
import gazekeep.shared.generated.resources.settings_lsposed
import gazekeep.shared.generated.resources.settings_need_camera
import gazekeep.shared.generated.resources.settings_need_self_test
import gazekeep.shared.generated.resources.settings_not_ready
import gazekeep.shared.generated.resources.settings_ready
import gazekeep.shared.generated.resources.settings_save_failed
import gazekeep.shared.generated.resources.settings_shizuku
import org.jetbrains.compose.resources.stringResource

internal fun noticeLabel(notice: UiNotice) = when (notice) {
  UiNotice.NeedsCamera -> Res.string.settings_need_camera
  UiNotice.NeedsSelfTest -> Res.string.settings_need_self_test
  UiNotice.EmptyScope -> Res.string.settings_empty_scope
  UiNotice.SaveFailed -> Res.string.settings_save_failed
}

@Composable
internal fun attentionLabel(attention: Attention) = stringResource(
  when (attention) {
    Attention.Looking -> Res.string.m0_result_looking
    Attention.Away -> Res.string.m0_result_away
    Attention.Unknown -> Res.string.m0_result_unknown
  },
)

@Composable
internal fun backendLabel(active: BackendId?, selected: BackendLock): String = stringResource(
  when {
    active == BackendId.Lsposed || (active == null && selected == BackendLock.Lsposed) -> Res.string.settings_lsposed

    active == BackendId.Shizuku || (active == null && selected == BackendLock.Shizuku) -> Res.string.settings_shizuku

    active == BackendId.Accessibility || (active == null && selected == BackendLock.Accessibility) ->
      Res.string.settings_accessibility

    else -> Res.string.settings_auto
  },
)

@Composable
internal fun backendName(id: BackendId): String = stringResource(
  when (id) {
    BackendId.Lsposed -> Res.string.settings_lsposed
    BackendId.Shizuku -> Res.string.settings_shizuku
    BackendId.Accessibility -> Res.string.settings_accessibility
  },
)

@Composable
internal fun readinessLabel(readiness: Readiness?) = stringResource(
  when (readiness) {
    Readiness.Ready -> Res.string.settings_ready

    is Readiness.NeedsSetup -> when (readiness.step) {
      SetupStep.StartShizuku -> Res.string.m2_shizuku_needs_start
      SetupStep.GrantShizukuPermission -> Res.string.m2_shizuku_needs_permission
      SetupStep.EnableAccessibilityService -> Res.string.m2_accessibility_unavailable
      else -> Res.string.m3_lsposed_unavailable
    }

    else -> Res.string.settings_not_ready
  },
)
