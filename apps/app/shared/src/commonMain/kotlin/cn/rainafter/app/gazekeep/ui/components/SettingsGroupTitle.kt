package cn.rainafter.app.gazekeep.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SettingsGroupTitle(
  text: String,
  modifier: Modifier = Modifier,
  textColor: Color = MiuixTheme.colorScheme.onBackgroundVariant,
  insideMargin: PaddingValues = PaddingValues(start = 16.dp, top = 8.dp, bottom = 2.dp),
) {
  SmallTitle(
    text = text,
    modifier = modifier,
    textColor = textColor,
    insideMargin = insideMargin,
  )
}
