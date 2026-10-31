package cn.rainafter.app.gazekeep.ui.screens

import cn.rainafter.app.gazekeep.ui.contract.AppEntry

/** 应用列表当前可见的应用，以及因隐藏系统应用而未显示的搜索匹配数。 */
internal data class AppListView(val visible: List<AppEntry>, val hiddenSystemMatches: Int)

/**
 * 按搜索词与系统应用开关过滤应用列表。隐藏系统应用时仍显示 [pinned] 内的应用，
 * 否则已选中的系统应用会从列表消失，用户既看不到也无法取消。
 */
internal fun filterApps(apps: List<AppEntry>, query: String, showSystem: Boolean, pinned: Set<String>): AppListView {
  val matches = if (query.isBlank()) {
    apps
  } else {
    apps.filter { it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
  }
  if (showSystem) return AppListView(matches, 0)
  val (visible, hidden) = matches.partition { !it.isSystem || it.packageName in pinned }
  return AppListView(visible, hidden.size)
}
