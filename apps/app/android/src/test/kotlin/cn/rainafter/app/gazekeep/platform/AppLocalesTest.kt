package cn.rainafter.app.gazekeep.platform

import android.app.Activity
import android.os.Looper
import cn.rainafter.app.gazekeep.ui.contract.AppLanguage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class AppLocalesTest {
  // Android 12 及以下语言只存在进程内偏好，不产生配置变化；运行中的服务只能靠该回调得知切换。
  @Config(sdk = [32])
  @Test
  fun legacySwitchNotifiesObserverUntilClosed() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    var changes = 0
    val close = AppLocales.observeLegacy(activity.applicationContext) { changes++ }

    AppLocales.set(activity, AppLanguage.English)
    shadowOf(Looper.getMainLooper()).idle()
    assertEquals(1, changes)

    close()
    AppLocales.set(activity, AppLanguage.System)
    shadowOf(Looper.getMainLooper()).idle()
    assertEquals(1, changes)
  }
}
