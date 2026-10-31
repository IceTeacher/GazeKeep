package cn.rainafter.app.gazekeep.backend.a11y

/**
 * 无障碍前台线索。TYPE_WINDOW_STATE_CHANGED 也来自悬浮层、弹窗和输入法（如 HyperOS 传送门），
 * 只有能解析为该包 Activity 的事件才切换前台，否则沿用上一个 Activity。
 */
internal class A11yForeground(private val isActivity: (packageName: String, className: String) -> Boolean) {
  @Volatile
  var packageName: String? = null
    private set

  fun onWindowStateChanged(packageName: String?, className: String?) {
    if (packageName == null || className == null || packageName == this.packageName) return
    if (isActivity(packageName, className)) this.packageName = packageName
  }
}
