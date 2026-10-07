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
import androidx.compose.ui.unit.dp
import cn.rainafter.app.gazekeep.ui.contract.GazeKeepController
import cn.rainafter.app.gazekeep.ui.contract.PlatformActions
import cn.rainafter.app.gazekeep.ui.contract.ControllerState
import cn.rainafter.app.gazekeep.ui.contract.SelfTestState
import cn.rainafter.app.gazekeep.ui.contract.SelfTestStep
import gazekeep.shared.generated.resources.Res
import gazekeep.shared.generated.resources.m0_capturing
import gazekeep.shared.generated.resources.self_test_camera_permission
import gazekeep.shared.generated.resources.self_test_capture
import gazekeep.shared.generated.resources.self_test_complete
import gazekeep.shared.generated.resources.self_test_description
import gazekeep.shared.generated.resources.self_test_enable
import gazekeep.shared.generated.resources.self_test_looking
import gazekeep.shared.generated.resources.self_test_away
import gazekeep.shared.generated.resources.self_test_notification_permission
import gazekeep.shared.generated.resources.self_test_preview_hint
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

import cn.rainafter.app.gazekeep.ui.components.AppScaffold
import cn.rainafter.app.gazekeep.ui.text.attentionLabel

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
                    title = stringResource(when (test.step) {
                        SelfTestStep.Looking -> Res.string.self_test_looking
                        SelfTestStep.Away -> Res.string.self_test_away
                        SelfTestStep.Complete -> Res.string.self_test_complete
                    }),
                    summary = test.attention?.let { attentionLabel(it) }
                )
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
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(Res.string.self_test_camera_permission))
                }
            } else if (test.step == SelfTestStep.Complete) {
                Button(
                    onClick = {
                        scope.launch {
                            controller.setEnabled(true)
                            onComplete()
                        }
                    },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(Res.string.self_test_enable))
                }
            } else {
                Button(
                    onClick = { scope.launch { controller.captureSelfTest() } },
                    enabled = !test.running,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(if (test.running) Res.string.m0_capturing else Res.string.self_test_capture))
                }
            }

            Button(
                onClick = actions::requestNotificationPermission,
                colors = ButtonDefaults.buttonColors(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(Res.string.self_test_notification_permission))
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
