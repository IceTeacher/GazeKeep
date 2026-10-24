package cn.rainafter.app.gazekeep.platform

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import cn.rainafter.app.gazekeep.ui.contract.AppLanguage

/**
 * 应用内语言。Android 13+ 交给系统的按应用语言保存（与系统设置「应用语言」同源），切换后由系统重建页面；
 * 更低版本没有该能力，语言标签存入独立偏好，由页面 attachBaseContext 与通知 Context 套用。
 */
object AppLocales {
  private const val PREFS = "gazekeep_app_locale"
  private const val KEY_TAGS = "language_tags"

  fun current(context: Context): AppLanguage = AppLanguage.fromLanguageTags(storedTags(context))

  fun set(activity: Activity, language: AppLanguage) {
    val tags = language.languageTag.orEmpty()
    if (Build.VERSION.SDK_INT >= 33) {
      activity.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tags)
    } else {
      // SharedPreferences 先更新进程内副本，随后 recreate 的 attachBaseContext 可立即读到。
      activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY_TAGS, tags) }
      activity.recreate()
    }
  }

  /**
   * 低版本的语言只写进程内偏好，不产生配置变化，运行中的服务靠该回调刷新文案；返回取消函数。
   * 13+ 由系统推送配置变化，调用方在 onConfigurationChanged 处理，这里不注册。
   */
  fun observeLegacy(context: Context, onChange: () -> Unit): () -> Unit {
    if (Build.VERSION.SDK_INT >= 33) return {}
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    // 偏好只弱引用监听器，由返回的取消函数持有强引用。
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == KEY_TAGS) onChange() }
    prefs.registerOnSharedPreferenceChangeListener(listener)
    return { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
  }

  /** 低版本把保存的语言套到 Context；13+ 由系统完成，原样返回。 */
  fun wrap(context: Context): Context {
    if (Build.VERSION.SDK_INT >= 33) return context
    val tags = storedTags(context)
    // Compose 资源按进程默认语言选择；跟随系统时也要重置，覆盖同进程内从指定语言切回的情况。
    val locales = if (tags.isEmpty()) Resources.getSystem().configuration.locales else LocaleList.forLanguageTags(tags)
    LocaleList.setDefault(locales)
    if (tags.isEmpty()) return context
    return context.createConfigurationContext(
      Configuration(context.resources.configuration).apply {
        setLocales(locales)
      },
    )
  }

  private fun storedTags(context: Context): String = if (Build.VERSION.SDK_INT >= 33) {
    context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
  } else {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TAGS, null).orEmpty()
  }
}
