package cn.rainafter.app.gazekeep.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.rainafter.app.gazekeep.core.model.Attention
import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.core.model.Readiness
import cn.rainafter.app.gazekeep.ui.components.AppScaffold
import cn.rainafter.app.gazekeep.ui.components.NoticeCard
import cn.rainafter.app.gazekeep.ui.components.StatusChip
import cn.rainafter.app.gazekeep.ui.contract.ControllerState
import cn.rainafter.app.gazekeep.ui.contract.GazeKeepController
import cn.rainafter.app.gazekeep.ui.contract.UiNotice
import cn.rainafter.app.gazekeep.ui.navigation.SettingsScreen
import cn.rainafter.app.gazekeep.ui.showcase.ShowcaseKeys
import cn.rainafter.app.gazekeep.ui.showcase.ShowcaseScrollTarget
import cn.rainafter.app.gazekeep.ui.text.backendLabel
import cn.rainafter.app.gazekeep.ui.text.backendName
import gazekeep.shared.generated.resources.Res
import gazekeep.shared.generated.resources.app_title
import gazekeep.shared.generated.resources.home_chip_camera
import gazekeep.shared.generated.resources.home_chip_granted
import gazekeep.shared.generated.resources.home_chip_missing
import gazekeep.shared.generated.resources.home_chip_not_ready
import gazekeep.shared.generated.resources.home_chip_passed
import gazekeep.shared.generated.resources.home_chip_pending
import gazekeep.shared.generated.resources.home_chip_ready
import gazekeep.shared.generated.resources.home_chip_selftest
import gazekeep.shared.generated.resources.home_decision_looking
import gazekeep.shared.generated.resources.home_decision_not_looking
import gazekeep.shared.generated.resources.home_decision_pending
import gazekeep.shared.generated.resources.home_info_android_version
import gazekeep.shared.generated.resources.home_info_app_version
import gazekeep.shared.generated.resources.home_info_backend_status
import gazekeep.shared.generated.resources.home_info_camera_selftest
import gazekeep.shared.generated.resources.home_info_device_model
import gazekeep.shared.generated.resources.home_info_fingerprint
import gazekeep.shared.generated.resources.home_privacy_card_summary
import gazekeep.shared.generated.resources.home_privacy_card_title
import gazekeep.shared.generated.resources.home_status_decision
import gazekeep.shared.generated.resources.home_status_stopped
import gazekeep.shared.generated.resources.home_status_tap_to_start
import gazekeep.shared.generated.resources.home_status_waiting
import gazekeep.shared.generated.resources.home_status_waiting_camera
import gazekeep.shared.generated.resources.home_status_waiting_camera_hint
import gazekeep.shared.generated.resources.home_status_waiting_hint
import gazekeep.shared.generated.resources.home_status_working
import gazekeep.shared.generated.resources.m0_result_unknown
import gazekeep.shared.generated.resources.settings_loading
import gazekeep.shared.generated.resources.settings_off
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

private enum class HomeRunStatus { Working, Waiting, Stopped }

@Composable
internal fun HomeScreen(
  state: ControllerState,
  scope: CoroutineScope,
  controller: GazeKeepController,
  onNavigate: (SettingsScreen) -> Unit,
  bottomBar: @Composable () -> Unit = {},
  statusSubtitleOverride: String? = null,
) {
  AppScaffold(
    title = stringResource(Res.string.app_title),
    onBack = null,
    bottomBar = bottomBar,
  ) { padding, scrollBehavior ->
    val scroll = rememberScrollState()
    ShowcaseScrollTarget(ShowcaseKeys.HOME, scroll, scrollBehavior)
    Column(
      modifier = Modifier
        .fillMaxSize()
        .nestedScroll(scrollBehavior.nestedScrollConnection)
        .verticalScroll(scroll)
        .padding(padding)
        .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (!state.loaded) {
        Card(modifier = Modifier.fillMaxWidth()) {
          BasicComponent(title = stringResource(Res.string.settings_loading))
        }
      } else {
        state.notice?.let { notice ->
          NoticeCard(
            notice = notice,
            onClick = when (notice) {
              UiNotice.NeedsCamera, UiNotice.NeedsSelfTest -> {
                { onNavigate(SettingsScreen.SelfTest) }
              }

              UiNotice.EmptyScope -> {
                { onNavigate(SettingsScreen.Apps) }
              }

              UiNotice.SaveFailed -> null
            },
          )
        }

        StatusBannerCard(
          state = state,
          statusSubtitleOverride = statusSubtitleOverride,
          onToggle = {
            scope.launch {
              val target = !state.config.enabled
              controller.setEnabled(target)
              if (target &&
                controller.state.value.notice in listOf(UiNotice.NeedsCamera, UiNotice.NeedsSelfTest)
              ) {
                onNavigate(SettingsScreen.SelfTest)
              }
            }
          },
        )

        SystemInfoCard(state = state)

        Card(modifier = Modifier.fillMaxWidth()) {
          BasicComponent(
            title = stringResource(Res.string.home_privacy_card_title),
            summary = stringResource(Res.string.home_privacy_card_summary),
            endActions = {
              Icon(
                imageVector = MiuixIcons.Info,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp),
              )
            },
            onClick = { onNavigate(SettingsScreen.Privacy) },
          )
        }

        Spacer(Modifier.height(16.dp))
      }
    }
  }
}

@Composable
private fun StatusBannerCard(state: ControllerState, statusSubtitleOverride: String?, onToggle: () -> Unit) {
  val status = when {
    !state.config.enabled -> HomeRunStatus.Stopped

    // 服务进程存活不代表引擎连着后端；掉线或撤权时引擎处于 Disabled，activeBackend 为空。
    state.serviceRunning && state.activeBackend != null -> HomeRunStatus.Working

    else -> HomeRunStatus.Waiting
  }
  val isDark = MiuixTheme.colorScheme.surface.luminance() < 0.5f

  val containerColor = when (status) {
    HomeRunStatus.Working -> if (isDark) Color(0xFF193623) else Color(0xFFDFF6E4)
    HomeRunStatus.Waiting -> if (isDark) Color(0xFF3B2B11) else Color(0xFFFFF3D6)
    HomeRunStatus.Stopped -> MiuixTheme.colorScheme.surfaceContainerHigh
  }
  val titleColor = when (status) {
    HomeRunStatus.Working -> if (isDark) Color(0xFFE6FAEB) else Color(0xFF102A17)
    HomeRunStatus.Waiting -> if (isDark) Color(0xFFFFEFD2) else Color(0xFF332100)
    HomeRunStatus.Stopped -> MiuixTheme.colorScheme.onSurface
  }
  val subtitleColor = titleColor.copy(alpha = 0.78f)
  val badgeColor = when (status) {
    HomeRunStatus.Working -> if (isDark) Color(0xFF32D74B) else Color(0xFF34C759)
    HomeRunStatus.Waiting -> if (isDark) Color(0xFFFF9F0A) else Color(0xFFFF9500)
    HomeRunStatus.Stopped -> MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.35f)
  }

  val waitingForCamera = status == HomeRunStatus.Waiting && !state.cameraGranted
  val statusTitle = when (status) {
    HomeRunStatus.Working -> stringResource(Res.string.home_status_working)

    HomeRunStatus.Waiting -> stringResource(
      if (waitingForCamera) Res.string.home_status_waiting_camera else Res.string.home_status_waiting,
    )

    HomeRunStatus.Stopped -> stringResource(Res.string.home_status_stopped)
  }
  val statusSubtitle = when (status) {
    HomeRunStatus.Working -> {
      val decisionLabel = when (state.lastDecision) {
        Attention.Looking -> stringResource(Res.string.home_decision_looking)
        Attention.Away -> stringResource(Res.string.home_decision_not_looking)
        Attention.Unknown -> stringResource(Res.string.m0_result_unknown)
        null -> stringResource(Res.string.home_decision_pending)
      }
      statusSubtitleOverride ?: stringResource(Res.string.home_status_decision, decisionLabel)
    }

    HomeRunStatus.Waiting -> stringResource(
      if (waitingForCamera) Res.string.home_status_waiting_camera_hint else Res.string.home_status_waiting_hint,
    )

    HomeRunStatus.Stopped -> stringResource(Res.string.home_status_tap_to_start)
  }
  val bottomTag = when (status) {
    HomeRunStatus.Working, HomeRunStatus.Waiting -> backendLabel(state.activeBackend, state.config.backendLock)
    HomeRunStatus.Stopped -> stringResource(Res.string.settings_off)
  }

  Card(
    modifier = Modifier.fillMaxWidth(),
    insideMargin = PaddingValues(0.dp),
    colors = CardDefaults.defaultColors(
      color = containerColor,
      contentColor = titleColor,
    ),
    pressFeedbackType = PressFeedbackType.Sink,
    showIndication = true,
    onClick = onToggle,
  ) {
    Box(modifier = Modifier.fillMaxWidth()) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 18.dp)
          .padding(end = 76.dp),
      ) {
        Text(
          text = statusTitle,
          style = MiuixTheme.textStyles.title3,
          fontWeight = FontWeight.Bold,
          color = titleColor,
        )
        Spacer(Modifier.height(4.dp))
        Text(
          text = statusSubtitle,
          style = MiuixTheme.textStyles.body2,
          color = subtitleColor,
        )
        Spacer(Modifier.height(18.dp))
        Text(
          text = bottomTag,
          style = MiuixTheme.textStyles.body1,
          fontWeight = FontWeight.Medium,
          color = titleColor,
        )
      }

      Canvas(
        modifier = Modifier
          .size(92.dp)
          .align(Alignment.BottomEnd)
          .offset(x = 12.dp, y = 14.dp),
      ) {
        val strokeWidth = 8.dp.toPx()
        val radius = (size.minDimension - strokeWidth) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
          color = badgeColor,
          radius = radius,
          center = center,
          style = Stroke(width = strokeWidth),
        )
        when (status) {
          HomeRunStatus.Working -> {
            drawLine(
              color = badgeColor,
              start = Offset(size.width * 0.29f, size.height * 0.52f),
              end = Offset(size.width * 0.45f, size.height * 0.67f),
              strokeWidth = strokeWidth,
              cap = StrokeCap.Round,
            )
            drawLine(
              color = badgeColor,
              start = Offset(size.width * 0.45f, size.height * 0.67f),
              end = Offset(size.width * 0.72f, size.height * 0.36f),
              strokeWidth = strokeWidth,
              cap = StrokeCap.Round,
            )
          }

          HomeRunStatus.Waiting -> {
            drawLine(
              color = badgeColor,
              start = Offset(center.x, size.height * 0.30f),
              end = Offset(center.x, size.height * 0.54f),
              strokeWidth = strokeWidth,
              cap = StrokeCap.Round,
            )
            drawCircle(
              color = badgeColor,
              radius = strokeWidth * 0.55f,
              center = Offset(center.x, size.height * 0.69f),
            )
          }

          HomeRunStatus.Stopped -> {
            drawLine(
              color = badgeColor,
              start = Offset(size.width * 0.34f, size.height * 0.34f),
              end = Offset(size.width * 0.66f, size.height * 0.66f),
              strokeWidth = strokeWidth,
              cap = StrokeCap.Round,
            )
            drawLine(
              color = badgeColor,
              start = Offset(size.width * 0.66f, size.height * 0.34f),
              end = Offset(size.width * 0.34f, size.height * 0.66f),
              strokeWidth = strokeWidth,
              cap = StrokeCap.Round,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun SystemInfoCard(state: ControllerState) {
  Card(modifier = Modifier.fillMaxWidth()) {
    BasicComponent(
      title = stringResource(Res.string.home_info_app_version),
      summary = state.systemInfo.appVersion,
    )
    BasicComponent(
      title = stringResource(Res.string.home_info_backend_status),
      bottomAction = {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          listOf(BackendId.Lsposed, BackendId.Shizuku, BackendId.Accessibility).forEach { id ->
            val isReady = state.backends[id] == Readiness.Ready
            StatusChip(
              label = backendName(id),
              statusText = if (isReady) {
                stringResource(Res.string.home_chip_ready)
              } else {
                stringResource(Res.string.home_chip_not_ready)
              },
              isPositive = isReady,
            )
          }
        }
      },
    )
    BasicComponent(
      title = stringResource(Res.string.home_info_camera_selftest),
      bottomAction = {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          StatusChip(
            label = stringResource(Res.string.home_chip_camera),
            statusText = if (state.cameraGranted) {
              stringResource(Res.string.home_chip_granted)
            } else {
              stringResource(Res.string.home_chip_missing)
            },
            isPositive = state.cameraGranted,
          )
          StatusChip(
            label = stringResource(Res.string.home_chip_selftest),
            statusText = if (state.selfTestPassed) {
              stringResource(Res.string.home_chip_passed)
            } else {
              stringResource(Res.string.home_chip_pending)
            },
            isPositive = state.selfTestPassed,
          )
        }
      },
    )
    BasicComponent(
      title = stringResource(Res.string.home_info_android_version),
      summary = state.systemInfo.androidVersion,
    )
    BasicComponent(
      title = stringResource(Res.string.home_info_device_model),
      summary = state.systemInfo.deviceModel,
    )
    BasicComponent(
      title = stringResource(Res.string.home_info_fingerprint),
      summary = state.systemInfo.systemFingerprint,
    )
  }
}
