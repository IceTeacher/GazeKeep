package cn.rainafter.app.gazekeep.platform

import android.app.Activity
import android.app.LocaleManager
import android.content.res.Configuration
import android.os.LocaleList
import android.os.Looper
import cn.rainafter.app.gazekeep.ui.contract.AppLanguage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.Locale
import kotlin.test.assertEquals
import org.robolectric.annotation.Config as RobolectricConfig

@RunWith(RobolectricTestRunner::class)
class AppLocalesTest {
  // Android 12 及以下语言只存在进程内偏好，不产生配置变化；运行中的服务只能靠该回调得知切换。
  @RobolectricConfig(sdk = [32])
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

  @RobolectricConfig(sdk = [35], qualifiers = "+zh-rCN")
  @Test
  fun wrapOnApi33PlusSyncsDefaultLocaleAndMismatchedContext() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
    activity.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("en")

    val wrapped = AppLocales.wrap(activity.applicationContext)
    assertEquals("en", LocaleList.getDefault()[0].language)
    assertEquals("en", wrapped.resources.configuration.locales[0].language)

    // 切回跟随系统时应恢复系统 LocaleList。
    activity.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.getEmptyLocaleList()
    val systemWrapped = AppLocales.wrap(activity)
    assertEquals("zh", LocaleList.getDefault()[0].language)
    assertEquals(Configuration::class, systemWrapped.resources.configuration::class)
  }
}
