package cn.rainafter.app.gazekeep.backend.a11y

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class A11yForegroundTest {
  private val activities = setOf(
    "com.android.chrome" to "org.chromium.chrome.browser.ChromeTabbedActivity",
    "com.miui.notes" to "com.miui.notes.ui.NotesListActivity",
  )
  private val foreground = A11yForeground { packageName, className -> packageName to className in activities }

  @Test
  fun activityWindowSwitchesForeground() {
    foreground.onWindowStateChanged("com.android.chrome", "org.chromium.chrome.browser.ChromeTabbedActivity")
    assertEquals("com.android.chrome", foreground.packageName)
    foreground.onWindowStateChanged("com.miui.notes", "com.miui.notes.ui.NotesListActivity")
    assertEquals("com.miui.notes", foreground.packageName)
  }

  @Test
  fun overlayFromAnotherPackageKeepsTheActivityForeground() {
    // HyperOS 传送门等悬浮层也发 TYPE_WINDOW_STATE_CHANGED，不能把黑名单应用换成名单外应用。
    foreground.onWindowStateChanged("com.android.chrome", "org.chromium.chrome.browser.ChromeTabbedActivity")
    foreground.onWindowStateChanged("com.miui.contentextension", "android.widget.FrameLayout")
    foreground.onWindowStateChanged("com.tencent.wetype", "android.inputmethodservice.SoftInputWindow")
    foreground.onWindowStateChanged("com.android.systemui", null)
    assertEquals("com.android.chrome", foreground.packageName)
  }

  @Test
  fun dialogOfTheForegroundPackageKeepsIt() {
    foreground.onWindowStateChanged("com.android.chrome", "org.chromium.chrome.browser.ChromeTabbedActivity")
    foreground.onWindowStateChanged("com.android.chrome", "android.app.AlertDialog")
    assertEquals("com.android.chrome", foreground.packageName)
  }

  @Test
  fun foregroundStaysUnknownUntilAnActivityIsSeen() {
    foreground.onWindowStateChanged("com.miui.contentextension", "android.widget.FrameLayout")
    foreground.onWindowStateChanged(null, "org.chromium.chrome.browser.ChromeTabbedActivity")
    assertNull(foreground.packageName)
  }
}
