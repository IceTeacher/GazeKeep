package cn.rainafter.app.gazekeep.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cn.rainafter.app.gazekeep.ui.contract.UiNotice
import cn.rainafter.app.gazekeep.ui.text.noticeLabel
import gazekeep.shared.generated.resources.Res
import gazekeep.shared.generated.resources.settings_notice_title
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun NoticeCard(
    notice: UiNotice,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surfaceContainerHigh,
            contentColor = MiuixTheme.colorScheme.onSurfaceContainerHigh,
        ),
    ) {
        BasicComponent(
            title = stringResource(Res.string.settings_notice_title),
            summary = stringResource(noticeLabel(notice)),
            onClick = onClick,
        )
    }
}
