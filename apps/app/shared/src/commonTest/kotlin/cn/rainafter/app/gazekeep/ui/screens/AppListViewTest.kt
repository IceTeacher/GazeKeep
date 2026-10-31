package cn.rainafter.app.gazekeep.ui.screens

import cn.rainafter.app.gazekeep.ui.contract.AppEntry
import kotlin.test.Test
import kotlin.test.assertEquals

class AppListViewTest {
  private val reader = AppEntry("com.example.reader", "Reader")
  private val gallery = AppEntry("com.example.gallery", "Gallery", isSystem = true)
  private val clock = AppEntry("com.example.clock", "Clock", isSystem = true)
  private val apps = listOf(reader, gallery, clock)

  private fun AppListView.packages() = visible.map { it.packageName }

  @Test fun hidesSystemAppsAndCountsThem() {
    val view = filterApps(apps, query = "", showSystem = false, pinned = emptySet())
    assertEquals(listOf(reader.packageName), view.packages())
    assertEquals(2, view.hiddenSystemMatches)
  }

  @Test fun showsEverythingWhenSystemAppsAreShown() {
    val view = filterApps(apps, query = "", showSystem = true, pinned = emptySet())
    assertEquals(apps.map { it.packageName }, view.packages())
    assertEquals(0, view.hiddenSystemMatches)
  }

  @Test fun pinnedSystemAppStaysVisible() {
    val view = filterApps(apps, query = "", showSystem = false, pinned = setOf(gallery.packageName))
    assertEquals(listOf(reader.packageName, gallery.packageName), view.packages())
    assertEquals(1, view.hiddenSystemMatches)
  }

  @Test fun hiddenCountOnlyIncludesSearchMatches() {
    val view = filterApps(apps, query = "GALL", showSystem = false, pinned = emptySet())
    assertEquals(emptyList(), view.packages())
    assertEquals(1, view.hiddenSystemMatches)
  }

  @Test fun searchMatchesPackageName() {
    val view = filterApps(apps, query = "example.read", showSystem = false, pinned = emptySet())
    assertEquals(listOf(reader.packageName), view.packages())
    assertEquals(0, view.hiddenSystemMatches)
  }
}
