package cn.rainafter.app.gazekeep.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.rainafter.app.gazekeep.ui.components.AppScaffold
import cn.rainafter.app.gazekeep.ui.components.SearchTextField
import cn.rainafter.app.gazekeep.ui.contract.ControllerState
import cn.rainafter.app.gazekeep.ui.contract.GazeKeepController
import cn.rainafter.app.gazekeep.ui.contract.LogEntry
import cn.rainafter.app.gazekeep.ui.contract.LogLevel
import cn.rainafter.app.gazekeep.ui.contract.PlatformActions
import gazekeep.shared.generated.resources.Res
import gazekeep.shared.generated.resources.logs_action_export
import gazekeep.shared.generated.resources.logs_action_filter
import gazekeep.shared.generated.resources.logs_action_more
import gazekeep.shared.generated.resources.logs_action_share
import gazekeep.shared.generated.resources.logs_clear_dialog_confirm
import gazekeep.shared.generated.resources.logs_clear_dialog_message
import gazekeep.shared.generated.resources.logs_clear_dialog_title
import gazekeep.shared.generated.resources.logs_copied
import gazekeep.shared.generated.resources.logs_empty
import gazekeep.shared.generated.resources.logs_level_all
import gazekeep.shared.generated.resources.logs_level_debug
import gazekeep.shared.generated.resources.logs_level_error
import gazekeep.shared.generated.resources.logs_level_fatal
import gazekeep.shared.generated.resources.logs_level_info
import gazekeep.shared.generated.resources.logs_level_verbose
import gazekeep.shared.generated.resources.logs_level_warning
import gazekeep.shared.generated.resources.logs_menu_card_view
import gazekeep.shared.generated.resources.logs_menu_clear
import gazekeep.shared.generated.resources.logs_menu_live
import gazekeep.shared.generated.resources.logs_menu_raw_view
import gazekeep.shared.generated.resources.logs_menu_scroll_bottom
import gazekeep.shared.generated.resources.logs_menu_scroll_top
import gazekeep.shared.generated.resources.logs_new_entries
import gazekeep.shared.generated.resources.logs_search_placeholder
import gazekeep.shared.generated.resources.logs_title
import gazekeep.shared.generated.resources.settings_dialog_cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
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
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.MoreCircle
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun LogScreen(
  state: ControllerState,
  scope: CoroutineScope,
  controller: GazeKeepController,
  actions: PlatformActions,
  onBack: () -> Unit,
) {
  var search by rememberSaveable { mutableStateOf("") }
  var selectedLevelOrdinal by rememberSaveable { mutableStateOf(-1) }
  var rawView by rememberSaveable { mutableStateOf(false) }
  var showFilterPopup by remember { mutableStateOf(false) }
  var showMorePopup by remember { mutableStateOf(false) }
  var showClearDialog by remember { mutableStateOf(false) }
  var clearCount by remember { mutableStateOf(0) }
  var copySequence by remember { mutableStateOf(0) }
  var copiedBannerVisible by remember { mutableStateOf(false) }
  val focusManager = LocalFocusManager.current
  var live by rememberSaveable { mutableStateOf(true) }
  var feed by remember { mutableStateOf(startLogFeed(state.logs)) }
  val listState = rememberLazyListState()

  LaunchedEffect(copySequence) {
    if (copySequence > 0) {
      copiedBannerVisible = true
      delay(1_800L)
      copiedBannerVisible = false
    }
  }
  val atTop by remember { derivedStateOf { !listState.canScrollBackward } }
  val latestLogs by rememberUpdatedState(state.logs)

  LaunchedEffect(Unit) {
    controller.refreshLogs()
  }

  // 只在本页存活期间合并日志，离开页面随组合取消；节流避免密集写入时逐条重排列表。
  LaunchedEffect(listState) {
    snapshotFlow { Triple(latestLogs, live, atTop) }
      .conflate()
      .collect { (latest, isLive, isAtTop) ->
        // 列表按 key 锚定视口，新条目插到前端时会停在旧条目上；停在顶部时与数据同帧请求回到首项以跟随最新。
        if (isLive && isAtTop) listState.requestScrollToItem(0)
        feed = feed.receive(latest, isLive, isAtTop)
        delay(LOG_REFRESH_THROTTLE_MS)
      }
  }

  val selectedLevel = LogLevel.entries.getOrNull(selectedLevelOrdinal)
  val matches = remember(search, selectedLevel) {
    { entry: LogEntry ->
      val levelMatched = selectedLevel == null || entry.level.priority >= selectedLevel.priority
      val searchMatched = search.isBlank() ||
        entry.message.contains(search, ignoreCase = true) ||
        entry.tag.contains(search, ignoreCase = true) ||
        entry.source.contains(search, ignoreCase = true) ||
        entry.target.contains(search, ignoreCase = true)
      levelMatched && searchMatched
    }
  }
  val filteredLogs = remember(feed.shown, matches) { feed.shown.filter(matches) }
  val unseen = remember(feed, state.logs, matches) { feed.unseenCount(state.logs, matches) }

  /** 回到最新并恢复实时；与数据同帧请求首项，避免按旧条目 key 锚定。 */
  fun jumpToLatest() {
    live = true
    listState.requestScrollToItem(0)
    feed = feed.catchUp(state.logs)
  }

  // 分享与导出始终取最新全量日志（仍按当前筛选），不受暂停时定格的列表影响。
  fun exportText() = formatLogsAsRawText(state.logs.filter(matches), actions)

  val filterLabels = listOf(
    stringResource(Res.string.logs_level_all),
    stringResource(Res.string.logs_level_verbose),
    stringResource(Res.string.logs_level_debug),
    stringResource(Res.string.logs_level_info),
    stringResource(Res.string.logs_level_warning),
    stringResource(Res.string.logs_level_error),
    stringResource(Res.string.logs_level_fatal),
  )

  val liveLabel = stringResource(Res.string.logs_menu_live)
  val moreViewLabel = stringResource(
    if (rawView) Res.string.logs_menu_card_view else Res.string.logs_menu_raw_view,
  )
  val scrollTopLabel = stringResource(Res.string.logs_menu_scroll_top)
  val scrollBottomLabel = stringResource(Res.string.logs_menu_scroll_bottom)
  val clearLogsLabel = stringResource(Res.string.logs_menu_clear)

  AppScaffold(
    title = stringResource(Res.string.logs_title),
    onBack = onBack,
    actions = {
      Box {
        IconButton(
          onClick = {
            focusManager.clearFocus()
            showFilterPopup = true
          },
        ) {
          Icon(
            imageVector = MiuixIcons.Tune,
            contentDescription = stringResource(Res.string.logs_action_filter),
          )
        }
        OverlayListPopup(
          show = showFilterPopup,
          alignment = PopupPositionProvider.Align.End,
          onDismissRequest = { showFilterPopup = false },
        ) {
          ListPopupColumn {
            filterLabels.forEachIndexed { index, label ->
              val isSelected = (selectedLevelOrdinal + 1) == index
              DropdownImpl(
                text = label,
                optionSize = filterLabels.size,
                isSelected = isSelected,
                index = index,
                onSelectedIndexChange = { chosen ->
                  selectedLevelOrdinal = chosen - 1
                  showFilterPopup = false
                },
              )
            }
          }
        }
      }

      IconButton(
        onClick = {
          actions.shareLogs(exportText())
        },
      ) {
        Icon(
          imageVector = MiuixIcons.Share,
          contentDescription = stringResource(Res.string.logs_action_share),
        )
      }

      IconButton(
        onClick = {
          actions.exportLogs(exportText())
        },
      ) {
        Icon(
          imageVector = MiuixIcons.Download,
          contentDescription = stringResource(Res.string.logs_action_export),
        )
      }

      Box {
        IconButton(
          onClick = {
            focusManager.clearFocus()
            showMorePopup = true
          },
        ) {
          Icon(
            imageVector = MiuixIcons.MoreCircle,
            contentDescription = stringResource(Res.string.logs_action_more),
          )
        }
        OverlayListPopup(
          show = showMorePopup,
          alignment = PopupPositionProvider.Align.End,
          onDismissRequest = { showMorePopup = false },
        ) {
          ListPopupColumn {
            DropdownImpl(
              text = liveLabel,
              optionSize = 5,
              isSelected = live,
              index = 0,
              onSelectedIndexChange = {
                showMorePopup = false
                if (live) live = false else jumpToLatest()
              },
            )
            DropdownImpl(
              text = moreViewLabel,
              optionSize = 5,
              isSelected = false,
              index = 1,
              onSelectedIndexChange = {
                rawView = !rawView
                showMorePopup = false
              },
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            DropdownImpl(
              text = scrollTopLabel,
              optionSize = 5,
              isSelected = false,
              index = 2,
              onSelectedIndexChange = {
                showMorePopup = false
                if (filteredLogs.isNotEmpty()) {
                  scope.launch { listState.animateScrollToItem(0) }
                }
              },
            )
            DropdownImpl(
              text = scrollBottomLabel,
              optionSize = 5,
              isSelected = false,
              index = 3,
              onSelectedIndexChange = {
                showMorePopup = false
                if (filteredLogs.isNotEmpty()) {
                  scope.launch { listState.animateScrollToItem(filteredLogs.lastIndex) }
                }
              },
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            DropdownImpl(
              text = clearLogsLabel,
              optionSize = 5,
              isSelected = false,
              index = 4,
              onSelectedIndexChange = {
                showMorePopup = false
                clearCount = state.logs.size
                showClearDialog = true
              },
            )
          }
        }
      }
    },
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
          placeholder = stringResource(Res.string.logs_search_placeholder),
        )
      }

      Box(modifier = Modifier.fillMaxSize()) {
        if (filteredLogs.isEmpty()) {
          Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = stringResource(Res.string.logs_empty),
              style = MiuixTheme.textStyles.subtitle,
              color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
              textAlign = TextAlign.Center,
            )
          }
        } else {
          LazyColumn(
            state = listState,
            modifier = Modifier
              .fillMaxSize()
              .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            items(
              items = filteredLogs,
              key = { it.id },
              contentType = { if (rawView) "raw" else "card" },
            ) { entry ->
              val copyEntry: () -> Unit = {
                actions.copyToClipboard(formatSingleLogLine(entry, actions))
                copySequence++
              }
              if (rawView) {
                RawLogItem(entry = entry, actions = actions, onCopy = copyEntry)
              } else {
                LogCardItem(entry = entry, actions = actions, onCopy = copyEntry)
              }
            }
          }
        }
        if (unseen > 0) {
          NewLogsPill(
            count = unseen,
            onClick = ::jumpToLatest,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
          )
        }
        if (copiedBannerVisible) {
          Text(
            text = stringResource(Res.string.logs_copied),
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
              .align(Alignment.BottomCenter)
              .padding(bottom = 12.dp)
              .clip(RoundedCornerShape(50))
              .background(MiuixTheme.colorScheme.primaryContainer)
              .padding(horizontal = 14.dp, vertical = 7.dp),
          )
        }
      }
    }

    OverlayDialog(
      show = showClearDialog,
      title = stringResource(Res.string.logs_clear_dialog_title),
      summary = stringResource(Res.string.logs_clear_dialog_message, clearCount),
      onDismissRequest = { showClearDialog = false },
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Button(
          onClick = { showClearDialog = false },
          modifier = Modifier.weight(1f),
        ) {
          Text(stringResource(Res.string.settings_dialog_cancel))
        }
        Button(
          onClick = {
            showClearDialog = false
            controller.clearLogs()
          },
          colors = ButtonDefaults.buttonColorsPrimary(),
          modifier = Modifier.weight(1f),
        ) {
          Text(stringResource(Res.string.logs_clear_dialog_confirm))
        }
      }
    }
  }
}

/** 离开顶部或暂停实时日志时提示未读条数，点按回到最新并恢复实时。 */
@Composable
private fun NewLogsPill(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Text(
    text = stringResource(Res.string.logs_new_entries, count),
    style = MiuixTheme.textStyles.footnote1,
    fontWeight = FontWeight.Medium,
    color = MiuixTheme.colorScheme.onPrimary,
    modifier = modifier
      .clip(RoundedCornerShape(50))
      .background(MiuixTheme.colorScheme.primary)
      .clickable(role = Role.Button, onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 7.dp),
  )
}

@Composable
private fun LogCardItem(entry: LogEntry, actions: PlatformActions, onCopy: () -> Unit) {
  val badgeColor = when (entry.level) {
    LogLevel.Verbose -> Color(0xFF6B7280)
    LogLevel.Debug -> Color(0xFF10B981)
    LogLevel.Info -> Color(0xFF3898EC)
    LogLevel.Warning -> Color(0xFFF59E0B)
    LogLevel.Error -> Color(0xFFEF4444)
    LogLevel.Fatal -> Color(0xFFDC2626)
  }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .combinedClickable(onClick = onCopy, onLongClick = onCopy),
    cornerRadius = 14.dp,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(18.dp)
              .clip(RoundedCornerShape(4.dp))
              .background(badgeColor),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = entry.level.badge,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = Color.White,
            )
          }
          Spacer(Modifier.width(6.dp))
          Text(
            text = "GazeKeep (${entry.tag})",
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
          )
        }
        Text(
          text = actions.formatLogTimestamp(entry.timestampMs),
          style = MiuixTheme.textStyles.footnote1,
          color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
      }

      Spacer(Modifier.height(4.dp))
      Text(
        text = "${entry.source}  →  ${entry.target}",
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.75f),
      )

      Spacer(Modifier.height(6.dp))
      Text(
        text = entry.message,
        style = MiuixTheme.textStyles.body2,
        fontFamily = FontFamily.Monospace,
        color = MiuixTheme.colorScheme.onSurface,
      )
    }
  }
}

@Composable
private fun RawLogItem(entry: LogEntry, actions: PlatformActions, onCopy: () -> Unit) {
  Text(
    text = formatSingleLogLine(entry, actions),
    style = MiuixTheme.textStyles.footnote1,
    fontFamily = FontFamily.Monospace,
    color = MiuixTheme.colorScheme.onSurface,
    modifier = Modifier
      .fillMaxWidth()
      .combinedClickable(onClick = onCopy, onLongClick = onCopy)
      .padding(vertical = 2.dp),
  )
}

private fun formatLogsAsRawText(entries: List<LogEntry>, actions: PlatformActions): String =
  entries.joinToString("\n") { formatSingleLogLine(it, actions) }

private fun formatSingleLogLine(entry: LogEntry, actions: PlatformActions): String =
  "${actions.formatLogTimestamp(entry.timestampMs)} ${entry.level.badge}/${entry.tag} " +
    "(${entry.source} -> ${entry.target}): ${entry.message}"

/** 实时日志合并间隔：足以合并一次检测周期内的多条写入，又不让用户感到滞后。 */
private const val LOG_REFRESH_THROTTLE_MS = 150L
