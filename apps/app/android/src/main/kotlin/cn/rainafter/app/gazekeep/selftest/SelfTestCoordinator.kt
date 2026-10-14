package cn.rainafter.app.gazekeep.selftest

import android.content.Context
import android.os.SystemClock
import androidx.camera.core.Preview
import cn.rainafter.app.gazekeep.core.engine.CapturePlan
import cn.rainafter.app.gazekeep.core.model.Attention
import cn.rainafter.app.gazekeep.core.model.Sensitivity
import cn.rainafter.app.gazekeep.core.policy.SelfTestProgress
import cn.rainafter.app.gazekeep.core.policy.advanceSelfTest
import cn.rainafter.app.gazekeep.engine.camera.CameraXFrameSource
import cn.rainafter.app.gazekeep.engine.camera.CaptureOutcome
import cn.rainafter.app.gazekeep.engine.camera.CaptureOwnershipCoordinator
import cn.rainafter.app.gazekeep.engine.camera.FrameSource
import cn.rainafter.app.gazekeep.engine.capture.AttentionCaptureSession
import cn.rainafter.app.gazekeep.engine.classify.FrameClassifier
import cn.rainafter.app.gazekeep.engine.classify.MlKitFrameClassifier
import cn.rainafter.app.gazekeep.engine.config.ConfigStore
import cn.rainafter.app.gazekeep.ui.contract.SelfTestState
import cn.rainafter.app.gazekeep.ui.contract.SelfTestStep
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 自检拥有自己的采集资源，退出设置页不会关闭服务会话的相机。 */
class SelfTestCoordinator internal constructor(
  private val scope: CoroutineScope,
  private val frameSource: FrameSource,
  private val classifier: FrameClassifier,
  private val savePassed: suspend () -> Unit,
  private val clock: () -> Long = { SystemClock.uptimeMillis() },
  private val ownership: CaptureOwnershipCoordinator = CaptureOwnershipCoordinator(),
) : AutoCloseable {
  constructor(
    context: Context,
    scope: CoroutineScope,
    store: ConfigStore,
    ownership: CaptureOwnershipCoordinator,
  ) : this(
    scope,
    CameraXFrameSource(context),
    MlKitFrameClassifier(),
    store::completeSelfTest,
    ownership = ownership,
  )
  private val mutableSelfTest = MutableStateFlow(SelfTestState())
  val state = mutableSelfTest.asStateFlow()
  private var testJob: Job? = null
  private var testActive = false
  private var progress = SelfTestProgress()
  private var lease: CaptureOwnershipCoordinator.Lease? = null
  private var generation = 0L
  private var closed = false
  private var foreground = true

  fun begin() {
    if (closed) return
    cancel()
    testActive = true
    if (foreground) lease = ownership.claimSelfTest()
    progress = SelfTestProgress()
    mutableSelfTest.value = SelfTestState()
  }

  fun setPreviewSurface(provider: Preview.SurfaceProvider?) {
    (frameSource as? CameraXFrameSource)?.previewSurfaceProvider = provider
  }

  fun capture(sensitivity: Sensitivity) {
    if (closed || !testActive || !foreground || state.value.running || progress.complete) return
    val captureLease = lease ?: return
    val captureGeneration = ++generation
    val previousStep = state.value.step
    mutableSelfTest.update { it.copy(running = true, attention = null, lastStepFailed = false) }
    testJob = scope.launch {
      try {
        val result = ownership.capture(captureLease) {
          AttentionCaptureSession(frameSource, classifier, clock).capture(
            CapturePlan(
              abortAtUptime = clock() + 1_600L,
              sensitivity = sensitivity,
            ),
          )
        }
        currentCoroutineContext().ensureActive()
        if (captureGeneration != generation) return@launch
        val attention =
          if (result?.outcome is CaptureOutcome.Completed) result.attention else Attention.Unknown
        val nextProgress = advanceSelfTest(progress, attention)
        // 保存失败时保留第二步，用户可以重试；不能先提交完成状态。
        if (nextProgress.complete) savePassed()
        currentCoroutineContext().ensureActive()
        if (captureGeneration != generation) return@launch
        progress = nextProgress
        val nextStep = when {
          progress.complete -> SelfTestStep.Complete
          progress.lookingPassed -> SelfTestStep.Away
          else -> SelfTestStep.Looking
        }
        if (progress.complete) {
          lease?.close()
          lease = null
        }
        mutableSelfTest.value = SelfTestState(
          step = nextStep,
          attention = attention,
          lastStepFailed = nextStep == previousStep,
        )
      } catch (error: CancellationException) {
        throw error
      } catch (_: Exception) {
        if (captureGeneration != generation) return@launch
        mutableSelfTest.update {
          it.copy(running = false, attention = Attention.Unknown, lastStepFailed = true)
        }
      } finally {
        if (captureGeneration == generation) mutableSelfTest.update { it.copy(running = false) }
      }
    }
  }

  fun resume() {
    foreground = true
    if (!closed && testActive && !progress.complete && lease == null) lease = ownership.claimSelfTest()
  }

  fun pause() {
    foreground = false
    stopCapture()
  }

  private fun stopCapture() {
    generation++
    testJob?.cancel()
    testJob = null
    lease?.close()
    lease = null
    mutableSelfTest.update { it.copy(running = false) }
  }

  fun cancel() {
    stopCapture()
    testActive = false
  }

  override fun close() {
    if (closed) return
    closed = true
    cancel()
    frameSource.close()
    classifier.close()
  }
}
