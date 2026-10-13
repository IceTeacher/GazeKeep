package cn.rainafter.app.gazekeep.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import gazekeep.shared.generated.resources.Res
import gazekeep.shared.generated.resources.settings_search_clear
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Close
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 统一的 Miuix 风格搜索输入框，样式与应用列表（AppsScreen）及日志搜索（LogScreen）完全保持一致。
 */
@Composable
fun SearchTextField(
  value: String,
  onValueChange: (String) -> Unit,
  placeholder: String,
  modifier: Modifier = Modifier,
) {
  TextField(
    value = value,
    onValueChange = onValueChange,
    label = placeholder,
    useLabelAsPlaceholder = true,
    singleLine = true,
    modifier = modifier.fillMaxWidth(),
    leadingIcon = {
      Icon(
        imageVector = MiuixIcons.Basic.Search,
        contentDescription = null,
        modifier = Modifier.padding(start = 12.dp, end = 4.dp),
        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
      )
    },
    trailingIcon = if (value.isNotEmpty()) {
      {
        IconButton(
          onClick = { onValueChange("") },
          modifier = Modifier.padding(end = 4.dp),
        ) {
          Icon(
            imageVector = MiuixIcons.Basic.Close,
            contentDescription = stringResource(Res.string.settings_search_clear),
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
          )
        }
      }
    } else {
      null
    },
  )
}
