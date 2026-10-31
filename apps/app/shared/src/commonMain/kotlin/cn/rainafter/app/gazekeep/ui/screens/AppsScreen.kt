package cn.rainafter.app.gazekeep.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.rainafter.app.gazekeep.core.model.AppFilterMode
import cn.rainafter.app.gazekeep.core.model.scopedPackages
import cn.rainafter.app.gazekeep.core.model.withScopedPackages
import cn.rainafter.app.gazekeep.ui.components.AppScaffold
import cn.rainafter.app.gazekeep.ui.components.SearchTextField
import cn.rainafter.app.gazekeep.ui.components.SettingsGroupTitle
import cn.rainafter.app.gazekeep.ui.contract.AppEntry
import cn.rainafter.app.gazekeep.ui.contract.AppListLoadState
import cn.rainafter.app.gazekeep.ui.contract.ControllerState
import cn.rainafter.app.gazekeep.ui.contract.GazeKeepController
import cn.rainafter.app.gazekeep.ui.contract.PlatformActions
import cn.rainafter.app.gazekeep.ui.showcase.ShowcaseInput
import cn.rainafter.app.gazekeep.ui.showcase.ShowcaseKeys
import cn.rainafter.app.gazekeep.ui.showcase.showcaseAnchor
import gazekeep.shared.generated.resources.Res
import gazekeep.shared.generated.resources.settings_apps_blacklist_title
import gazekeep.shared.generated.resources.settings_apps_clear
import gazekeep.shared.generated.resources.settings_apps_clear_blacklist_message
import gazekeep.shared.generated.resources.settings_apps_clear_confirm
import gazekeep.shared.generated.resources.settings_apps_clear_title
import gazekeep.shared.generated.resources.settings_apps_clear_whitelist_message
import gazekeep.shared.generated.resources.settings_apps_hidden_system
import gazekeep.shared.generated.resources.settings_apps_load_failed
import gazekeep.shared.generated.resources.settings_apps_more
import gazekeep.shared.generated.resources.settings_apps_select_matches
import gazekeep.shared.generated.resources.settings_apps_selected
import gazekeep.shared.generated.resources.settings_apps_show_system
import gazekeep.shared.generated.resources.settings_apps_whitelist_title
import gazekeep.shared.generated.resources.settings_dialog_cancel
import gazekeep.shared.generated.resources.settings_loading
import gazekeep.shared.generated.resources.settings_no_apps
import gazekeep.shared.generated.resources.settings_retry
import gazekeep.shared.generated.resources.settings_search_apps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.MoreCircle
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
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
    modifier = Modifier.fillMaxWidth().showcaseAnchor(ShowcaseKeys.app(app.packageName)),
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
  ShowcaseInput(ShowcaseKeys.APPS_SEARCH) { search = it }
  var showSystem by remember { mutableStateOf(actions.showSystemApps()) }
  var showMenu by remember { mutableStateOf(false) }
  var showClearDialog by remember { mutableStateOf(false) }
  // 打开时记下数量：确认后名单立即变空，退场动画期间文案不应跳成 0。
  var clearCount by remember { mutableStateOf(0) }
  val focusManager = LocalFocusManager.current
  val filterMode = state.config.appFilterMode
  val scoped = state.config.scopedPackages
  // 本页出现过的已选应用保持可见：在隐藏系统应用时取消勾选，条目不会立刻从列表消失。
  var pinned by remember(filterMode) { mutableStateOf(scoped) }
  val listView = remember(state.apps, search, showSystem, pinned, scoped) {
    filterApps(state.apps, search, showSystem, pinned + scoped)
  }
  val filteredApps = listView.visible
  val updateShowSystem: (Boolean) -> Unit = { show ->
    showSystem = show
    actions.setShowSystemApps(show)
  }
  val selectPackages: (Set<String>) -> Unit = { packages ->
    pinned = pinned + packages
    scope.launch { controller.updateConfig { config -> config.withScopedPackages(config.scopedPackages + packages) } }
  }

  AppScaffold(
    title = stringResource(
      if (filterMode == AppFilterMode.Blacklist) {
        Res.string.settings_apps_blacklist_title
      } else {
        Res.string.settings_apps_whitelist_title
      },
    ),
    onBack = onBack,
    actions = {
      Box {
        IconButton(
          onClick = {
            // 收起搜索键盘，否则输入法候选栏会盖住菜单。
            focusManager.clearFocus()
            showMenu = true
          },
        ) {
          Icon(
            imageVector = MiuixIcons.MoreCircle,
            contentDescription = stringResource(Res.string.settings_apps_more),
          )
        }
        AppsMenu(
          show = showMenu,
          showSystem = showSystem,
          canSelectMatches = search.isNotBlank() && filteredApps.isNotEmpty(),
          canClear = scoped.isNotEmpty(),
          onDismiss = { showMenu = false },
          onToggleSystem = { updateShowSystem(!showSystem) },
          onSelectMatches = { selectPackages(filteredApps.mapTo(mutableSetOf()) { it.packageName }) },
          onClear = {
            clearCount = scoped.size
            showClearDialog = true
          },
        )
      }
    },
  ) { padding, scrollBehavior ->
    // 对话框需位于 AppScaffold 内，才能挂到 miuix 的对话框宿主上。
    ClearScopeDialog(
      show = showClearDialog,
      filterMode = filterMode,
      count = clearCount,
      onDismiss = { showClearDialog = false },
      onConfirm = {
        showClearDialog = false
        scope.launch { controller.updateConfig { it.withScopedPackages(emptySet()) } }
      },
    )
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
        text = stringResource(Res.string.settings_apps_selected, scoped.size),
        insideMargin = PaddingValues(start = 32.dp, top = 4.dp, bottom = 6.dp),
      )

      if (filteredApps.isEmpty()) {
        Box(
          modifier = Modifier.fillMaxSize().padding(32.dp),
          contentAlignment = Alignment.Center,
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            val hiddenSystem = state.appsLoadState == AppListLoadState.Loaded && listView.hiddenSystemMatches > 0
            Text(
              text = when {
                hiddenSystem -> stringResource(Res.string.settings_apps_hidden_system, listView.hiddenSystemMatches)

                else -> stringResource(
                  when (state.appsLoadState) {
                    AppListLoadState.NotLoaded, AppListLoadState.Loading -> Res.string.settings_loading
                    AppListLoadState.Loaded -> Res.string.settings_no_apps
                    AppListLoadState.Failed -> Res.string.settings_apps_load_failed
                  },
                )
              },
              style = MiuixTheme.textStyles.subtitle,
              color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
              textAlign = TextAlign.Center,
            )
            if (hiddenSystem) {
              Button(onClick = { updateShowSystem(true) }) {
                Text(stringResource(Res.string.settings_apps_show_system))
              }
            }
            if (state.appsLoadState == AppListLoadState.Failed) {
              Button(onClick = { scope.launch { controller.loadApps() } }) {
                Text(stringResource(Res.string.settings_retry))
              }
            }
          }
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
            val isChecked = app.packageName in scoped
            AppListItem(
              app = app,
              isChecked = isChecked,
              actions = actions,
              onCheckedChange = { selected ->
                pinned = pinned + app.packageName
                scope.launch {
                  controller.updateConfig { config ->
                    config.withScopedPackages(
                      if (selected) {
                        config.scopedPackages + app.packageName
                      } else {
                        config.scopedPackages - app.packageName
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

@Composable
private fun AppsMenu(
  show: Boolean,
  showSystem: Boolean,
  canSelectMatches: Boolean,
  canClear: Boolean,
  onDismiss: () -> Unit,
  onToggleSystem: () -> Unit,
  onSelectMatches: () -> Unit,
  onClear: () -> Unit,
) {
  val showSystemLabel = stringResource(Res.string.settings_apps_show_system)
  val selectMatchesLabel = stringResource(Res.string.settings_apps_select_matches)
  val clearLabel = stringResource(Res.string.settings_apps_clear)
  OverlayListPopup(
    show = show,
    alignment = PopupPositionProvider.Align.End,
    onDismissRequest = onDismiss,
  ) {
    // 选项按当前状态增减；系统应用开关始终在首位，批量操作只在可执行时出现。
    val bulkActions = buildList {
      if (canSelectMatches) add(selectMatchesLabel to onSelectMatches)
      if (canClear) add(clearLabel to onClear)
    }
    val optionSize = 1 + bulkActions.size
    ListPopupColumn {
      DropdownImpl(
        text = showSystemLabel,
        optionSize = optionSize,
        isSelected = showSystem,
        index = 0,
        onSelectedIndexChange = {
          onDismiss()
          onToggleSystem()
        },
      )
      if (bulkActions.isNotEmpty()) {
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
      }
      bulkActions.forEachIndexed { offset, (label, action) ->
        DropdownImpl(
          text = label,
          optionSize = optionSize,
          isSelected = false,
          index = offset + 1,
          onSelectedIndexChange = {
            onDismiss()
            action()
          },
        )
      }
    }
  }
}

@Composable
private fun ClearScopeDialog(
  show: Boolean,
  filterMode: AppFilterMode,
  count: Int,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
) {
  OverlayDialog(
    show = show,
    title = stringResource(Res.string.settings_apps_clear_title),
    summary = stringResource(
      if (filterMode == AppFilterMode.Blacklist) {
        Res.string.settings_apps_clear_blacklist_message
      } else {
        Res.string.settings_apps_clear_whitelist_message
      },
      count,
    ),
    onDismissRequest = onDismiss,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Button(onClick = onDismiss, modifier = Modifier.weight(1f)) {
        Text(stringResource(Res.string.settings_dialog_cancel))
      }
      Button(
        onClick = onConfirm,
        colors = ButtonDefaults.buttonColorsPrimary(),
        modifier = Modifier.weight(1f),
      ) {
        Text(stringResource(Res.string.settings_apps_clear_confirm))
      }
    }
  }
}
