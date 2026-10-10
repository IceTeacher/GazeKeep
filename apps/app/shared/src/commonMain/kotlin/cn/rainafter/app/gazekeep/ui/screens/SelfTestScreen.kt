package cn.rainafter.app.gazekeep.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.rainafter.app.gazekeep.ui.components.AppScaffold
import cn.rainafter.app.gazekeep.ui.contract.ControllerState
import cn.rainafter.app.gazekeep.ui.contract.GazeKeepController
import cn.rainafter.app.gazekeep.ui.contract.PlatformActions
import cn.rainafter.app.gazekeep.ui.contract.SelfTestState
import cn.rainafter.app.gazekeep.ui.contract.SelfTestStep
import cn.rainafter.app.gazekeep.ui.text.attentionLabel
import gazekeep.shared.generated.resources.Res
import gazekeep.shared.generated.resources.m0_capturing
import gazekeep.shared.generated.resources.self_test_away
import gazekeep.shared.generated.resources.self_test_away_expect
import gazekeep.shared.generated.resources.self_test_camera_permission
import gazekeep.shared.generated.resources.self_test_capture_step_1
import gazekeep.shared.generated.resources.self_test_capture_step_2
import gazekeep.shared.generated.resources.self_test_complete
import gazekeep.shared.generated.resources.self_test_complete_summary
import gazekeep.shared.generated.resources.self_test_description
import gazekeep.shared.generated.resources.self_test_enable
import gazekeep.shared.generated.resources.self_test_finish
import gazekeep.shared.generated.resources.self_test_last_result
import gazekeep.shared.generated.resources.self_test_looking
import gazekeep.shared.generated.resources.self_test_looking_expect
import gazekeep.shared.generated.resources.self_test_notification_permission
import gazekeep.shared.generated.resources.self_test_preview_hint
import gazekeep.shared.generated.resources.self_test_retry_away_hint
import gazekeep.shared.generated.resources.self_test_retry_looking_hint
import gazekeep.shared.generated.resources.self_test_status_current
import gazekeep.shared.generated.resources.self_test_status_done
import gazekeep.shared.generated.resources.self_test_status_pending
import gazekeep.shared.generated.resources.settings_self_test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SelfTestScreen(
  state: ControllerState,
  test: SelfTestState,
  scope: CoroutineScope,
  controller: GazeKeepController,
  actions: PlatformActions,
  onBack: () -> Unit,
  onComplete: () -> Unit,
) {
  val step1Done = test.step != SelfTestStep.Looking
  val step2Done = test.step == SelfTestStep.Complete
  val step2Current = test.step == SelfTestStep.Away

  AppScaffold(
    title = stringResource(Res.string.settings_self_test),
    onBack = onBack,
  ) { padding, scrollBehavior ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .nestedScroll(scrollBehavior.nestedScrollConnection)
        .verticalScroll(rememberScrollState())
        .padding(padding)
        .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      SmallTitle(
        text = stringResource(Res.string.self_test_description),
        textColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        insideMargin = PaddingValues(start = 16.dp, top = 4.dp, bottom = 4.dp),
      )

      Card(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
          title = stringResource(Res.string.self_test_looking),
          summary = stringResource(Res.string.self_test_looking_expect),
          endActions = {
            Text(
              text = stringResource(
                if (step1Done) Res.string.self_test_status_done else Res.string.self_test_status_current,
              ),
              style = MiuixTheme.textStyles.body2,
              fontWeight = FontWeight.Medium,
              color = if (step1Done) {
                MiuixTheme.colorScheme.primary
              } else {
                MiuixTheme.colorScheme.onSurface
              },
            )
          },
        )
        BasicComponent(
          title = stringResource(Res.string.self_test_away),
          summary = stringResource(Res.string.self_test_away_expect),
          endActions = {
            Text(
              text = stringResource(
                when {
                  step2Done -> Res.string.self_test_status_done
                  step2Current -> Res.string.self_test_status_current
                  else -> Res.string.self_test_status_pending
                },
              ),
              style = MiuixTheme.textStyles.body2,
              fontWeight = FontWeight.Medium,
              color = when {
                step2Done -> MiuixTheme.colorScheme.primary
                step2Current -> MiuixTheme.colorScheme.onSurface
                else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
              },
            )
          },
        )
      }

      if (test.step == SelfTestStep.Complete) {
        Card(modifier = Modifier.fillMaxWidth()) {
          BasicComponent(
            title = stringResource(Res.string.self_test_complete),
            summary = stringResource(Res.string.self_test_complete_summary),
          )
        }
      } else {
        test.attention?.let { attention ->
          Card(modifier = Modifier.fillMaxWidth()) {
            BasicComponent(
              title = stringResource(Res.string.self_test_last_result, attentionLabel(attention)),
              summary = if (test.lastStepFailed) {
                stringResource(
                  if (test.step == SelfTestStep.Looking) {
                    Res.string.self_test_retry_looking_hint
                  } else {
                    Res.string.self_test_retry_away_hint
                  },
                )
              } else {
                null
              },
            )
          }
        }
      }

      if (state.cameraGranted && test.step != SelfTestStep.Complete) {
        Card(
          modifier = Modifier.fillMaxWidth().height(240.dp),
          cornerRadius = 16.dp,
        ) {
          actions.SelfTestPreview(Modifier.fillMaxSize())
        }
        SmallTitle(
          text = stringResource(Res.string.self_test_preview_hint),
          textColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
          insideMargin = PaddingValues(start = 16.dp, top = 4.dp, bottom = 4.dp),
        )
      }

      Spacer(Modifier.height(8.dp))
      if (!state.cameraGranted) {
        Button(
          onClick = actions::requestCameraPermission,
          colors = ButtonDefaults.buttonColorsPrimary(),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(stringResource(Res.string.self_test_camera_permission))
        }
      } else if (test.step == SelfTestStep.Complete) {
        val alreadyEnabled = state.config.enabled
        Button(
          onClick = {
            scope.launch {
              if (!alreadyEnabled) {
                controller.setEnabled(true)
              }
              onComplete()
            }
          },
          colors = ButtonDefaults.buttonColorsPrimary(),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            stringResource(
              if (alreadyEnabled) Res.string.self_test_finish else Res.string.self_test_enable,
            ),
          )
        }
      } else {
        val captureLabel = when {
          test.running -> Res.string.m0_capturing
          test.step == SelfTestStep.Looking -> Res.string.self_test_capture_step_1
          else -> Res.string.self_test_capture_step_2
        }
        Button(
          onClick = { scope.launch { controller.captureSelfTest() } },
          enabled = !test.running,
          colors = ButtonDefaults.buttonColorsPrimary(),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(stringResource(captureLabel))
        }
      }

      Button(
        onClick = actions::requestNotificationPermission,
        colors = ButtonDefaults.buttonColors(),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(stringResource(Res.string.self_test_notification_permission))
      }

      Spacer(Modifier.height(16.dp))
    }
  }
}
