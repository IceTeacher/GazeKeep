package cn.rainafter.app.gazekeep.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cn.rainafter.app.gazekeep.ui.contract.GazeKeepController
import cn.rainafter.app.gazekeep.ui.contract.PlatformActions
import cn.rainafter.app.gazekeep.ui.navigation.SettingsNavigation
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun App(controller: GazeKeepController, actions: PlatformActions) {
    val state by controller.state.collectAsState()
    val test by controller.selfTest.collectAsState()
    MiuixTheme(controller = remember { ThemeController() }) {
        SettingsNavigation(controller, actions, state, test)
    }
}
