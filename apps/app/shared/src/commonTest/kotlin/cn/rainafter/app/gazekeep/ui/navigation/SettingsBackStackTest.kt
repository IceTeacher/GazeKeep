package cn.rainafter.app.gazekeep.ui.navigation

import androidx.compose.runtime.mutableStateListOf
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsBackStackTest {
  @Test
  fun unavailableScreensCannotBePushedWhileAllowedRoutesWork() {
    val unavailable = setOf(
      SettingsScreen.SelfTest,
      SettingsScreen.Logs,
      SettingsScreen.About,
      SettingsScreen.Notices,
    )
    val backStack = SettingsBackStack(mutableStateListOf(SettingsScreen.Home), unavailable)

    backStack.selectTab(SettingsScreen.Settings)
    assertEquals(SettingsScreen.Settings, backStack.current)

    for (blocked in unavailable) {
      backStack.navigateTo(blocked)
      assertEquals(SettingsScreen.Settings, backStack.current)
    }

    backStack.navigateTo(SettingsScreen.Backend)
    assertEquals(SettingsScreen.Backend, backStack.current)

    backStack.navigateBack()
    assertEquals(SettingsScreen.Settings, backStack.current)

    backStack.show(SettingsScreen.Privacy, animate = false)
    assertEquals(SettingsScreen.Privacy, backStack.current)
    assertEquals(2, backStack.size)
  }
}
