package cn.rainafter.app.gazekeep.xposed.hook

import android.os.Binder
import android.os.Parcel
import android.util.Log
import cn.rainafter.app.gazekeep.xposed.power.PowerHookCore

data class HookInstallResult(val resolution: HookResolution, val installed: Boolean)

/** Hook 框架适配层的窄接口；签名解析失败时不安装任何候选。 */
interface HookFacade {
  fun hookBefore(method: java.lang.reflect.Method, callback: (HookBeforeParam) -> Unit): HookRegistration
  fun hookAfter(method: java.lang.reflect.Method, callback: (Array<Any?>, Any?) -> Unit): HookRegistration
}

class HookBeforeParam(val args: Array<Any?>, private val resultWriter: (Any?) -> Unit) {
  fun setResult(value: Any?) = resultWriter(value)
}

object HookPlan {
  fun install(facade: HookFacade, classLoader: ClassLoader, core: PowerHookCore): HookInstallResult {
    val resolver = DeadlineResolver(classLoader)
    val resolution = resolver.resolve()
    val candidate = resolution.candidate ?: return HookInstallResult(resolution, installed = false)
    val method = resolution.method ?: return HookInstallResult(resolution, installed = false)
    val bridgeMethod = resolver.resolveBridge()
      ?: return HookInstallResult(resolution, installed = false)

    val deadlineGuard = HookCallbackGuard(
      onFailure = { Log.w("GazeKeepXposed", "deadline hook failed", it) },
      onDisabled = core::disable,
    )
    val bridgeGuard = HookCallbackGuard(
      onFailure = { Log.w("GazeKeepXposed", "bridge hook failed", it) },
      onDisabled = core::disable,
    )
    when (candidate) {
      HookCandidate.A_ATTENTION -> facade.hookAfter(method) { args, _ ->
        deadlineGuard.run {
          val nextDimming = args.firstOrNull() as? Long ?: return@run
          val dimDuration = args.getOrNull(1) as? Long ?: 0L
          core.onDeadlineComputed(nextDimming + dimDuration, -1L, nextDimming)
        }
      }

      HookCandidate.C_INACTIVITY -> facade.hookBefore(method) { param ->
        deadlineGuard.run {
          val timeout = param.args.firstOrNull() as? Long ?: return@run
          core.onDeadlineComputed(timeout, -1L)
        }
      }
    }
    facade.hookBefore(bridgeMethod) { param ->
      bridgeGuard.run {
        val code = param.args.getOrNull(0) as? Int ?: return@run
        val data = param.args.getOrNull(1) as? Parcel ?: return@run
        val reply = param.args.getOrNull(2) as? Parcel ?: return@run
        if (core.handleTransaction(code, data, reply, Binder.getCallingUid())) {
          param.setResult(true)
        }
      }
    }
    installCameraIndicatorHook(facade, classLoader, core)
    return HookInstallResult(resolution, installed = true)
  }

  private fun installCameraIndicatorHook(facade: HookFacade, classLoader: ClassLoader, core: PowerHookCore) {
    val indicatorPlan = runCatching { CameraIndicatorResolver(classLoader).resolve() }.getOrNull() ?: return
    val activeChanged = indicatorPlan.activeChanged ?: return
    val indicatorGuard = HookCallbackGuard(
      onFailure = { Log.w("GazeKeepXposed", "indicator hook failed", it) },
      onDisabled = core::disableIndicatorHook,
    )
    val installed = installOptionalHooks(facade, core::disableIndicatorHook) { indicatorFacade ->
      indicatorFacade.hookBefore(activeChanged.method) { param ->
        indicatorGuard.run {
          if (
            shouldSuppressCameraActiveNotification(
              args = param.args,
              activeArgIndex = activeChanged.activeArgIndex,
              hideEnabled = core.shouldHideCameraIndicator(),
              isTargetApp = core::isOurCameraOp,
            )
          ) {
            param.setResult(null)
          }
        }
      }
      indicatorPlan.operationActiveMethod?.let { opActiveMethod ->
        indicatorFacade.hookBefore(opActiveMethod) { param ->
          indicatorGuard.run {
            if (
              shouldOverrideCameraOperationActive(
                args = param.args,
                hideEnabled = core.shouldHideCameraIndicator(),
                isTargetApp = core::isOurCameraOp,
              )
            ) {
              param.setResult(false)
            }
          }
        }
      }
      core.markIndicatorHookInstalled()
    }
    if (installed) {
      Log.i("GazeKeepXposed", "indicator_hook_installed candidate=${activeChanged.candidate}")
    } else {
      Log.w("GazeKeepXposed", "indicator_hook_install_failed")
    }
  }
}
