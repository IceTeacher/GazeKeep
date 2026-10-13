package cn.rainafter.app.gazekeep.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import gazekeep.shared.generated.resources.logs_menu_raw_view
import gazekeep.shared.generated.resources.logs_menu_scroll_bottom
import gazekeep.shared.generated.resources.logs_menu_scroll_top
import gazekeep.shared.generated.resources.logs_search_placeholder
import gazekeep.shared.generated.resources.logs_title
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
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
  val listState = rememberLazyListState()

  LaunchedEffect(Unit) {
    controller.refreshLogs()
  }

  val selectedLevel = LogLevel.entries.getOrNull(selectedLevelOrdinal)
  val filteredLogs = remember(state.logs, search, selectedLevel) {
    state.logs.filter { entry ->
      val levelMatched = selectedLevel == null || entry.level.priority >= selectedLevel.priority
      val searchMatched = search.isBlank() ||
        entry.message.contains(search, ignoreCase = true) ||
        entry.tag.contains(search, ignoreCase = true) ||
        entry.source.contains(search, ignoreCase = true) ||
        entry.target.contains(search, ignoreCase = true)
      levelMatched && searchMatched
    }
  }

  val filterLabels = listOf(
    stringResource(Res.string.logs_level_all),
    stringResource(Res.string.logs_level_verbose),
    stringResource(Res.string.logs_level_debug),
    stringResource(Res.string.logs_level_info),
    stringResource(Res.string.logs_level_warning),
    stringResource(Res.string.logs_level_error),
    stringResource(Res.string.logs_level_fatal),
  )

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
        IconButton(onClick = { showFilterPopup = true }) {
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
          actions.shareLogs(formatLogsAsRawText(filteredLogs))
        },
      ) {
        Icon(
          imageVector = MiuixIcons.Share,
          contentDescription = stringResource(Res.string.logs_action_share),
        )
      }

      IconButton(
        onClick = {
          actions.exportLogs(formatLogsAsRawText(filteredLogs))
        },
      ) {
        Icon(
          imageVector = MiuixIcons.Download,
          contentDescription = stringResource(Res.string.logs_action_export),
        )
      }

      Box {
        IconButton(onClick = { showMorePopup = true }) {
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
              text = moreViewLabel,
              optionSize = 4,
              isSelected = false,
              index = 0,
              onSelectedIndexChange = {
                rawView = !rawView
                showMorePopup = false
              },
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            DropdownImpl(
              text = scrollTopLabel,
              optionSize = 4,
              isSelected = false,
              index = 1,
              onSelectedIndexChange = {
                showMorePopup = false
                if (filteredLogs.isNotEmpty()) {
                  scope.launch { listState.animateScrollToItem(0) }
                }
              },
            )
            DropdownImpl(
              text = scrollBottomLabel,
              optionSize = 4,
              isSelected = false,
              index = 2,
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
              optionSize = 4,
              isSelected = false,
              index = 3,
              onSelectedIndexChange = {
                showMorePopup = false
                controller.clearLogs()
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
            if (rawView) {
              RawLogItem(entry = entry)
            } else {
              LogCardItem(entry = entry)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun LogCardItem(entry: LogEntry) {
  val badgeColor = when (entry.level) {
    LogLevel.Verbose -> Color(0xFF6B7280)
    LogLevel.Debug -> Color(0xFF10B981)
    LogLevel.Info -> Color(0xFF3898EC)
    LogLevel.Warning -> Color(0xFFF59E0B)
    LogLevel.Error -> Color(0xFFEF4444)
    LogLevel.Fatal -> Color(0xFFDC2626)
  }

  Card(
    modifier = Modifier.fillMaxWidth(),
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
          text = formatTimestamp(entry.timestampMs),
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
private fun RawLogItem(entry: LogEntry) {
  Text(
    text = formatSingleLogLine(entry),
    style = MiuixTheme.textStyles.footnote1,
    fontFamily = FontFamily.Monospace,
    color = MiuixTheme.colorScheme.onSurface,
    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
  )
}

private fun formatLogsAsRawText(entries: List<LogEntry>): String =
  entries.joinToString("\n", transform = ::formatSingleLogLine)

private fun formatSingleLogLine(entry: LogEntry): String =
  "${formatTimestamp(entry.timestampMs)} ${entry.level.badge}/${entry.tag} " +
    "(${entry.source} -> ${entry.target}): ${entry.message}"

/** 纯 Kotlin 格式化 MM-dd HH:mm:ss（以 UTC+8 本地偏移展示，不引入平台日期依赖）。 */
private fun formatTimestamp(epochMillis: Long): String {
  val localSeconds = (epochMillis / 1_000L) + 8L * 3_600L
  val days = localSeconds.floorDiv(86_400L)
  val secondsOfDay = localSeconds.mod(86_400L).toInt()
  val hour = secondsOfDay / 3_600
  val minute = (secondsOfDay % 3_600) / 60
  val second = secondsOfDay % 60

  // Howard Hinnant civil-from-days algorithm
  val z = days + 719_468L
  val era = if (z >= 0) z / 146_097L else (z - 146_096L) / 146_097L
  val doe = (z - era * 146_097L).toInt()
  val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
  val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
  val mp = (5 * doy + 2) / 153
  val day = doy - (153 * mp + 2) / 5 + 1
  val month = mp + if (mp < 10) 3 else -9

  return "${pad2(month)}-${pad2(day)} ${pad2(hour)}:${pad2(minute)}:${pad2(second)}"
}

private fun pad2(value: Int): String = if (value < 10) "0$value" else value.toString()
