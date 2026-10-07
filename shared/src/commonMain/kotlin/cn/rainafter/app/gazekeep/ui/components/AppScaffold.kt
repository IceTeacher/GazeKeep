package cn.rainafter.app.gazekeep.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import gazekeep.shared.generated.resources.Res
import gazekeep.shared.generated.resources.settings_back
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    scrollBehavior: ScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = title,
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = MiuixIcons.Back,
                        contentDescription = stringResource(Res.string.settings_back),
                    )
                }
            }
        },
        actions = actions,
    )
}

@Composable
internal fun AppScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    scrollBehavior: ScrollBehavior = MiuixScrollBehavior(),
    content: @Composable (PaddingValues, ScrollBehavior) -> Unit,
) {
    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            AppTopBar(
                title = title,
                onBack = onBack,
                scrollBehavior = scrollBehavior,
            )
        }
    ) { padding ->
        content(padding, scrollBehavior)
    }
}
