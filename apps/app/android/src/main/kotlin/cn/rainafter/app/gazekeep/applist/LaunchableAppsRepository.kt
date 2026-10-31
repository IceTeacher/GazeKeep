package cn.rainafter.app.gazekeep.applist

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import cn.rainafter.app.gazekeep.ui.contract.AppEntry
import cn.rainafter.app.gazekeep.ui.contract.AppListLoadState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 只有从未被更新过的预装应用算系统应用；商店更新过的预装应用（如 Chrome）按用户应用显示。 */
internal fun isSystemApp(flags: Int): Boolean =
  flags and ApplicationInfo.FLAG_SYSTEM != 0 && flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0

class LaunchableAppsRepository internal constructor(
  private val scope: CoroutineScope,
  private val onStateChanged: (AppListLoadState, List<AppEntry>) -> Unit,
  private val query: suspend () -> List<AppEntry>,
  private val preloadIcons: suspend (List<AppEntry>) -> Unit = {},
) : AutoCloseable {
  constructor(
    context: Context,
    scope: CoroutineScope,
    onStateChanged: (AppListLoadState, List<AppEntry>) -> Unit,
  ) : this(
    scope,
    onStateChanged,
    {
      withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
          .map {
            AppEntry(
              it.activityInfo.packageName,
              it.loadLabel(pm).toString(),
              isSystemApp(it.activityInfo.applicationInfo.flags),
            )
          }
          .distinctBy { it.packageName }
          .sortedBy { it.label.lowercase() }
      }
    },
    { apps ->
      val sizePx = (40 * context.resources.displayMetrics.density).toInt().coerceAtLeast(96)
      for (app in apps) {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        if (AppIconCache.get(app.packageName) == null) {
          AppIconCache.loadBitmap(context, app.packageName, sizePx)
        }
      }
    },
  )
  private var apps: List<AppEntry>? = null
  private var appsJob: Job? = null
  private var iconPreloadJob: Job? = null
  suspend fun load() {
    if (apps == null) {
      val existing = appsJob
      if (existing?.isActive == true) {
        existing.join()
      } else {
        val job = scope.launch {
          onStateChanged(AppListLoadState.Loading, emptyList())
          try {
            val result = query()
            apps = result
            onStateChanged(AppListLoadState.Loaded, result)
          } catch (error: CancellationException) {
            throw error
          } catch (_: Exception) {
            onStateChanged(AppListLoadState.Failed, emptyList())
          }
        }
        appsJob = job
        job.join()
      }
    }
    apps?.let(::startPreloadIcons)
  }

  private fun startPreloadIcons(apps: List<AppEntry>) {
    if (apps.isEmpty()) return
    if (iconPreloadJob?.isActive == true) return
    iconPreloadJob = scope.launch(Dispatchers.IO) {
      // 系统应用默认隐藏，先读取用户应用的图标。
      preloadIcons(apps.sortedBy { it.isSystem })
    }
  }

  override fun close() {
    appsJob?.cancel()
    iconPreloadJob?.cancel()
  }
}
