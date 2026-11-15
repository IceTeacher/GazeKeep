package cn.rainafter.app.gazekeep.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import cn.rainafter.app.gazekeep.MainActivity
import cn.rainafter.app.gazekeep.R
import cn.rainafter.app.gazekeep.app.AppGraph
import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.platform.AppLocales
import cn.rainafter.app.gazekeep.runtime.RuntimeStatusStore
import cn.rainafter.app.gazekeep.runtime.ServiceActivation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 控制中心快捷开关：前置条件满足时直接启停，缺少相机权限或自检时收起面板并跳转应用。 */
class GazeQuickTileService : TileService() {
  private var observeScope: CoroutineScope? = null
  private var observeJob: Job? = null
  private var actionScope: CoroutineScope? = null

  override fun onCreate() {
    super.onCreate()
    actionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  }

  override fun onStartListening() {
    super.onStartListening()
    startObserving()
  }

  override fun onStopListening() {
    observeJob?.cancel()
    observeJob = null
    observeScope?.cancel()
    observeScope = null
    super.onStopListening()
  }

  override fun onDestroy() {
    actionScope?.cancel()
    actionScope = null
    super.onDestroy()
  }

  override fun onClick() {
    super.onClick()
    val scope = actionScope ?: return
    val graph = AppGraph(applicationContext)
    scope.launch {
      val settings = runCatching { graph.settings.settings.first() }.getOrNull()
      val cameraGranted = hasCameraPermission()
      val selfTestPassed = settings?.selfTestPassedAt != null
      val enabled = settings?.config?.enabled == true
      val runtime = RuntimeStatusStore.status.value
      val decision = QuickTileStateMapper.resolve(
        cameraGranted = cameraGranted,
        selfTestPassed = selfTestPassed,
        enabled = enabled,
        serviceRunning = runtime.running,
        activeBackend = runtime.backend,
      )
      when (decision.action) {
        QuickTileAction.OpenMainActivity -> openMainFromTile()

        QuickTileAction.Disable -> {
          RuntimeStatusStore.clearLastDecision()
          runCatching { graph.settings.update { it.copy(enabled = false) } }
          ServiceActivation.stop(applicationContext)
          applyTileState(
            QuickTileStateMapper.resolve(
              cameraGranted = cameraGranted,
              selfTestPassed = selfTestPassed,
              enabled = false,
              serviceRunning = false,
              activeBackend = null,
            ),
          )
        }

        QuickTileAction.Enable -> {
          if (QuickTileStateMapper.requiresForegroundActivityToEnable(Build.VERSION.SDK_INT)) {
            openMainFromTile(ACTION_ENABLE_FROM_TILE)
          } else {
            val executeEnable = suspend {
              runCatching { graph.settings.update { it.copy(enabled = true) } }
              ServiceActivation.reconfigure(applicationContext)
              applyTileState(
                QuickTileStateMapper.resolve(
                  cameraGranted = cameraGranted,
                  selfTestPassed = selfTestPassed,
                  enabled = true,
                  serviceRunning = RuntimeStatusStore.status.value.running,
                  activeBackend = RuntimeStatusStore.status.value.backend,
                ),
              )
            }
            if (isLocked) {
              unlockAndRun { scope.launch { executeEnable() } }
            } else {
              executeEnable()
            }
          }
        }
      }
    }
  }

  private fun startObserving() {
    observeJob?.cancel()
    observeScope?.cancel()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    observeScope = scope
    val graph = AppGraph(applicationContext)
    observeJob = scope.launch {
      combine(graph.settings.settings, RuntimeStatusStore.status) { settings, runtime ->
        QuickTileStateMapper.resolve(
          cameraGranted = hasCameraPermission(),
          selfTestPassed = settings.selfTestPassedAt != null,
          enabled = settings.config.enabled,
          serviceRunning = runtime.running,
          activeBackend = runtime.backend,
        )
      }.collect(::applyTileState)
    }
  }

  private fun hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

  private fun applyTileState(decision: QuickTileDecision) {
    val tile = qsTile ?: return
    val localized = AppLocales.wrap(this)
    tile.label = localized.getString(R.string.app_name)
    tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_gazekeep)
    tile.state = when (decision.visualState) {
      QuickTileVisualState.Inactive -> Tile.STATE_INACTIVE
      QuickTileVisualState.Active -> Tile.STATE_ACTIVE
    }
    val subtitleText = when (val sub = decision.subtitle) {
      QuickTileSubtitle.NeedsCamera -> localized.getString(R.string.tile_state_needs_camera)

      QuickTileSubtitle.NeedsSelfTest -> localized.getString(R.string.tile_state_needs_self_test)

      QuickTileSubtitle.Off -> localized.getString(R.string.tile_state_off)

      QuickTileSubtitle.WaitingBackend -> localized.getString(R.string.tile_state_waiting_backend)

      is QuickTileSubtitle.Running -> {
        val backendName = localized.getString(
          when (sub.backend) {
            BackendId.Lsposed -> R.string.tile_backend_lsposed
            BackendId.Shizuku -> R.string.tile_backend_shizuku
            BackendId.Accessibility -> R.string.tile_backend_accessibility
          },
        )
        localized.getString(R.string.tile_state_running, backendName)
      }
    }
    if (Build.VERSION.SDK_INT >= 29) {
      tile.subtitle = subtitleText
    }
    tile.contentDescription = "${tile.label}, $subtitleText"
    tile.updateTile()
  }

  @SuppressLint("StartActivityAndCollapseDeprecated")
  private fun openMainFromTile(tileAction: String? = null) {
    val intent = Intent(this, MainActivity::class.java).apply {
      action = tileAction
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    val launch = {
      if (Build.VERSION.SDK_INT >= 34) {
        val pendingIntent = PendingIntent.getActivity(
          this,
          TILE_REQUEST_CODE,
          intent,
          PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        startActivityAndCollapse(pendingIntent)
      } else {
        @Suppress("DEPRECATION")
        startActivityAndCollapse(intent)
      }
    }
    if (isLocked) {
      unlockAndRun(launch)
    } else {
      launch()
    }
  }

  companion object {
    const val ACTION_ENABLE_FROM_TILE = "cn.rainafter.app.gazekeep.action.ENABLE_FROM_TILE"
    private const val TILE_REQUEST_CODE = 3101

    fun requestRefresh(context: Context) {
      val appContext = context.applicationContext
      runCatching {
        requestListeningState(appContext, ComponentName(appContext, GazeQuickTileService::class.java))
      }
    }
  }
}
