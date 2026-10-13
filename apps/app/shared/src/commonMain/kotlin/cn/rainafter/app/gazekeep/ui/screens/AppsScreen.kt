package cn.rainafter.app.gazekeep.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.rainafter.app.gazekeep.ui.components.AppScaffold
import cn.rainafter.app.gazekeep.ui.components.SearchTextField
import cn.rainafter.app.gazekeep.ui.components.SettingsGroupTitle
import cn.rainafter.app.gazekeep.ui.contract.AppEntry
import cn.rainafter.app.gazekeep.ui.contract.ControllerState
import cn.rainafter.app.gazekeep.ui.contract.GazeKeepController
import cn.rainafter.app.gazekeep.ui.contract.PlatformActions
import gazekeep.shared.generated.resources.Res
import gazekeep.shared.generated.resources.settings_apps
import gazekeep.shared.generated.resources.settings_apps_selected
import gazekeep.shared.generated.resources.settings_loading
import gazekeep.shared.generated.resources.settings_no_apps
import gazekeep.shared.generated.resources.settings_search_apps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
private fun AppListItem(
  app: AppEntry,
  isChecked: Boolean,
  actions: PlatformActions,
  onCheckedChange: (Boolean) -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    cornerRadius = 12.dp,
  ) {
    SwitchPreference(
      title = app.label,
      summary = app.packageName,
      checked = isChecked,
      startAction = {
        actions.AppIcon(
          packageName = app.packageName,
          modifier = Modifier.size(40.dp),
        )
      },
      onCheckedChange = onCheckedChange,
    )
  }
}

@Composable
internal fun AppsScreen(
  state: ControllerState,
  scope: CoroutineScope,
  controller: GazeKeepController,
  actions: PlatformActions,
  onBack: () -> Unit,
) {
  var search by rememberSaveable { mutableStateOf("") }
  val filteredApps = remember(state.apps, search) {
    if (search.isBlank()) {
      state.apps
    } else {
      state.apps.filter {
        it.label.contains(search, ignoreCase = true) || it.packageName.contains(search, ignoreCase = true)
      }
    }
  }

  AppScaffold(
    title = stringResource(Res.string.settings_apps),
    onBack = onBack,
  ) { padding, scrollBehavior ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
    ) {
      Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        SearchTextField(
          value = search,
          onValueChange = { search = it },
          placeholder = stringResource(Res.string.settings_search_apps),
        )
      }

      SettingsGroupTitle(
        text = stringResource(Res.string.settings_apps_selected, state.config.packages.size),
        insideMargin = PaddingValues(start = 32.dp, top = 4.dp, bottom = 6.dp),
      )

      if (filteredApps.isEmpty()) {
        Box(
          modifier = Modifier.fillMaxSize().padding(32.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = stringResource(
              if (state.apps.isEmpty()) Res.string.settings_loading else Res.string.settings_no_apps,
            ),
            style = MiuixTheme.textStyles.subtitle,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
          )
        }
      } else {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
          contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          items(
            items = filteredApps,
            key = { it.packageName },
            contentType = { "app" },
          ) { app ->
            val isChecked = app.packageName in state.config.packages
            AppListItem(
              app = app,
              isChecked = isChecked,
              actions = actions,
              onCheckedChange = { selected ->
                scope.launch {
                  controller.updateConfig { config ->
                    config.copy(
                      packages = if (selected) {
                        config.packages + app.packageName
                      } else {
                        config.packages -
                          app.packageName
                      },
                    )
                  }
                }
              },
            )
          }
        }
      }
    }
  }
}
