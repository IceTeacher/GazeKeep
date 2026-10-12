package cn.rainafter.app.gazekeep.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import cn.rainafter.app.gazekeep.ui.components.AppScaffold
import cn.rainafter.app.gazekeep.ui.components.SettingsGroupTitle
import gazekeep.shared.generated.resources.Res
import gazekeep.shared.generated.resources.privacy_indicator_summary
import gazekeep.shared.generated.resources.privacy_indicator_title
import gazekeep.shared.generated.resources.privacy_line_1
import gazekeep.shared.generated.resources.privacy_line_2
import gazekeep.shared.generated.resources.privacy_line_3
import gazekeep.shared.generated.resources.privacy_title_1
import gazekeep.shared.generated.resources.privacy_title_2
import gazekeep.shared.generated.resources.privacy_title_3
import gazekeep.shared.generated.resources.settings_camera_pause
import gazekeep.shared.generated.resources.settings_camera_pause_summary
import gazekeep.shared.generated.resources.settings_dark_pause
import gazekeep.shared.generated.resources.settings_dark_pause_summary
import gazekeep.shared.generated.resources.settings_failure_policy
import gazekeep.shared.generated.resources.settings_failure_summary
import gazekeep.shared.generated.resources.settings_group_security
import gazekeep.shared.generated.resources.settings_privacy
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card

@Composable
internal fun PrivacyScreen(onBack: () -> Unit) {
  AppScaffold(
    title = stringResource(Res.string.settings_privacy),
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
      SettingsGroupTitle(text = stringResource(Res.string.settings_group_security))
      Card(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
          title = stringResource(Res.string.privacy_title_1),
          summary = stringResource(Res.string.privacy_line_1),
        )
        BasicComponent(
          title = stringResource(Res.string.privacy_title_2),
          summary = stringResource(Res.string.privacy_line_2),
        )
        BasicComponent(
          title = stringResource(Res.string.privacy_title_3),
          summary = stringResource(Res.string.privacy_line_3),
        )
        BasicComponent(
          title = stringResource(Res.string.settings_failure_policy),
          summary = stringResource(Res.string.settings_failure_summary),
        )
        BasicComponent(
          title = stringResource(Res.string.settings_camera_pause),
          summary = stringResource(Res.string.settings_camera_pause_summary),
        )
        BasicComponent(
          title = stringResource(Res.string.settings_dark_pause),
          summary = stringResource(Res.string.settings_dark_pause_summary),
        )
        BasicComponent(
          title = stringResource(Res.string.privacy_indicator_title),
          summary = stringResource(Res.string.privacy_indicator_summary),
        )
      }

      Spacer(Modifier.height(16.dp))
    }
  }
}
