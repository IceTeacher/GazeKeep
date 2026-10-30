package cn.rainafter.app.gazekeep.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.rainafter.app.gazekeep.ui.showcase.ShowcaseKeys
import cn.rainafter.app.gazekeep.ui.showcase.showcaseAnchor
import gazekeep.shared.generated.resources.Res
import gazekeep.shared.generated.resources.settings_badge_experimental
import gazekeep.shared.generated.resources.settings_dialog_cancel
import gazekeep.shared.generated.resources.settings_experimental_dialog_confirm
import gazekeep.shared.generated.resources.settings_experimental_dialog_message
import gazekeep.shared.generated.resources.settings_experimental_dialog_title
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 通用高复用实验性功能开关组件。
 *
 * 在标题右侧展示蓝色「实验性」Badge，用户开启时弹出二次确认弹窗，
 * 用户确认后才触发开启回调；关闭时直接生效无需二次确认。
 */
@Composable
fun ExperimentalSwitchPreference(
  title: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  summary: String? = null,
  badgeText: String = stringResource(Res.string.settings_badge_experimental),
  dialogTitle: String = stringResource(Res.string.settings_experimental_dialog_title),
  dialogMessage: String = stringResource(Res.string.settings_experimental_dialog_message, title),
  dialogConfirmText: String = stringResource(Res.string.settings_experimental_dialog_confirm),
  dialogCancelText: String = stringResource(Res.string.settings_dialog_cancel),
  enabled: Boolean = true,
  startAction: @Composable (() -> Unit)? = null,
) {
  var showConfirmDialog by rememberSaveable { mutableStateOf(false) }
  LaunchedEffect(enabled, checked) {
    if (!enabled || checked) showConfirmDialog = false
  }

  val handleToggle: (Boolean) -> Unit = { targetChecked ->
    if (enabled) {
      if (targetChecked) {
        showConfirmDialog = true
      } else {
        onCheckedChange(false)
      }
    }
  }

  BasicComponent(
    modifier = modifier,
    enabled = enabled,
    startAction = startAction,
    endActions = {
      Switch(
        checked = checked,
        onCheckedChange = handleToggle,
        enabled = enabled,
      )
    },
    onClick = {
      handleToggle(!checked)
    },
    role = Role.Switch,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = title,
        modifier = Modifier.weight(1f, fill = false),
        style = MiuixTheme.textStyles.title4,
        color = if (enabled) MiuixTheme.colorScheme.onBackground else MiuixTheme.colorScheme.disabledOnSecondaryVariant,
      )
      ExperimentalBadge(text = badgeText, enabled = enabled)
    }
    if (!summary.isNullOrEmpty()) {
      Spacer(Modifier.height(2.dp))
      Text(
        text = summary,
        style = MiuixTheme.textStyles.body2,
        color = if (enabled) {
          MiuixTheme.colorScheme.onSurfaceVariantSummary
        } else {
          MiuixTheme.colorScheme.disabledOnSecondaryVariant
        },
      )
    }
  }

  OverlayDialog(
    show = showConfirmDialog && enabled && !checked,
    title = dialogTitle,
    summary = dialogMessage,
    onDismissRequest = { showConfirmDialog = false },
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Button(
        onClick = { showConfirmDialog = false },
        modifier = Modifier.weight(1f),
      ) {
        Text(dialogCancelText)
      }
      Button(
        onClick = {
          showConfirmDialog = false
          if (enabled && !checked) onCheckedChange(true)
        },
        enabled = enabled && !checked,
        colors = ButtonDefaults.buttonColorsPrimary(),
        modifier = Modifier.weight(1f).showcaseAnchor(ShowcaseKeys.EXPERIMENTAL_CONFIRM),
      ) {
        Text(dialogConfirmText)
      }
    }
  }
}

@Composable
private fun ExperimentalBadge(text: String, enabled: Boolean, modifier: Modifier = Modifier) {
  val isDark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
  val backgroundColor = if (!enabled) {
    MiuixTheme.colorScheme.surface
  } else if (isDark) {
    Color(0xFF152A42)
  } else {
    Color(0xFFE3F2FD)
  }
  val textColor = if (!enabled) {
    MiuixTheme.colorScheme.disabledOnSecondaryVariant
  } else if (isDark) {
    Color(0xFF90CAF9)
  } else {
    Color(0xFF1976D2)
  }
  Box(
    modifier = modifier
      .background(color = backgroundColor, shape = RoundedCornerShape(4.dp))
      .padding(horizontal = 6.dp, vertical = 2.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = text,
      fontSize = 11.sp,
      fontWeight = FontWeight.Medium,
      color = textColor,
      lineHeight = 13.sp,
      maxLines = 1,
      softWrap = false,
    )
  }
}
