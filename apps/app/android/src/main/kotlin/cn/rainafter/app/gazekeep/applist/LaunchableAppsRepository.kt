package cn.rainafter.app.gazekeep.applist

import android.content.Context
import android.content.Intent
import cn.rainafter.app.gazekeep.ui.contract.AppEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LaunchableAppsRepository(
  private val context: Context,
  private val scope: CoroutineScope,
  private val onLoaded: (List<AppEntry>) -> Unit,
) : AutoCloseable {
  private var apps = emptyList<AppEntry>()
  private var appsJob: Job? = null
  private var iconPreloadJob: Job? = null
  suspend fun load() {
    if (apps.isEmpty()) {
      val existing = appsJob
      if (existing?.isActive == true) {
        existing.join()
      } else {
        val job = scope.launch {
          val apps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(intent, 0)
              .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
              .distinctBy { it.packageName }
              .sortedBy { it.label.lowercase() }
          }
          this@LaunchableAppsRepository.apps = apps
          onLoaded(apps)
        }
        appsJob = job
        job.join()
      }
    }
    startPreloadIcons(apps)
  }

  private fun startPreloadIcons(apps: List<AppEntry>) {
    if (apps.isEmpty()) return
    if (iconPreloadJob?.isActive == true) return
    iconPreloadJob = scope.launch(Dispatchers.IO) {
      val sizePx = (40 * context.resources.displayMetrics.density).toInt().coerceAtLeast(96)
      for (app in apps) {
        if (!isActive) break
        if (AppIconCache.get(app.packageName) == null) {
          AppIconCache.loadBitmap(context, app.packageName, sizePx)
        }
      }
    }
  }

  override fun close() {
    appsJob?.cancel()
    iconPreloadJob?.cancel()
  }
}
