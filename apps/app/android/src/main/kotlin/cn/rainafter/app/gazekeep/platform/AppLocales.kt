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
import cn.rainafter.app.gazekeep.service.GazeQuickTileService
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
    val locales = effectiveLocales(tags)
    if (!locales.isEmpty) LocaleList.setDefault(locales)
    if (Build.VERSION.SDK_INT >= 33) {
      activity.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tags)
    } else {
      // SharedPreferences 先更新进程内副本，随后 recreate 的 attachBaseContext 可立即读到。
      activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY_TAGS, tags) }
      activity.recreate()
    }
    GazeQuickTileService.requestRefresh(activity)
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

  /** 同步进程默认 LocaleList，确保 Compose 资源环境始终按当前应用语言选择。 */
  fun syncDefault(context: Context) {
    val locales = effectiveLocales(storedTags(context))
    if (!locales.isEmpty) LocaleList.setDefault(locales)
  }

  /** 把保存的语言同步到进程默认语言并在配置尚未匹配时包装 Context。 */
  fun wrap(context: Context): Context {
    val tags = storedTags(context)
    val locales = effectiveLocales(tags)
    // Compose 资源读取进程默认 LocaleList；13+ 在 Activity 重建与 Service 上下文中同样需保持同步。
    if (!locales.isEmpty) LocaleList.setDefault(locales)
    if (tags.isEmpty()) return context
    val currentLanguage = if (!context.resources.configuration.locales.isEmpty) {
      context.resources.configuration.locales[0]?.language
    } else {
      null
    }
    val targetLanguage = if (!locales.isEmpty) locales[0]?.language else null
    if (Build.VERSION.SDK_INT >= 33 && currentLanguage == targetLanguage) return context
    return context.createConfigurationContext(
      Configuration(context.resources.configuration).apply {
        setLocales(locales)
      },
    )
  }

  private fun effectiveLocales(tags: String): LocaleList =
    if (tags.isEmpty()) Resources.getSystem().configuration.locales else LocaleList.forLanguageTags(tags)

  private fun storedTags(context: Context): String = if (Build.VERSION.SDK_INT >= 33) {
    context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
  } else {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TAGS, null).orEmpty()
  }
}
