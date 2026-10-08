package cn.rainafter.app.gazekeep.selftest

import android.content.Context
import androidx.camera.core.Preview
import cn.rainafter.app.gazekeep.core.model.Attention
import cn.rainafter.app.gazekeep.core.model.Sensitivity
import cn.rainafter.app.gazekeep.core.policy.SelfTestProgress
import cn.rainafter.app.gazekeep.core.policy.advanceSelfTest
import cn.rainafter.app.gazekeep.engine.camera.CameraXFrameSource
import cn.rainafter.app.gazekeep.engine.camera.CaptureOutcome
import cn.rainafter.app.gazekeep.engine.capture.AttentionCaptureSession
import cn.rainafter.app.gazekeep.engine.classify.MlKitFrameClassifier
import cn.rainafter.app.gazekeep.engine.config.ConfigStore
import cn.rainafter.app.gazekeep.runtime.RuntimeStatusStore
import cn.rainafter.app.gazekeep.ui.contract.SelfTestState
import cn.rainafter.app.gazekeep.ui.contract.SelfTestStep
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** 自检拥有自己的采集资源，退出设置页不会关闭服务会话的相机。 */
class SelfTestCoordinator(context: Context, private val scope: CoroutineScope, private val store: ConfigStore) :
  AutoCloseable {
  private val frameSource = CameraXFrameSource(context)
  private val classifier = MlKitFrameClassifier()
  private val mutableSelfTest = MutableStateFlow(SelfTestState())
  val state = mutableSelfTest.asStateFlow()
  private var testJob: Job? = null
  private var testActive = false
  private var progress = SelfTestProgress()

  fun begin() {
    cancel()
    testActive = true
    progress = SelfTestProgress()
    mutableSelfTest.value = SelfTestState()
  }
  fun setPreviewSurface(provider: Preview.SurfaceProvider?) {
    frameSource.previewSurfaceProvider = provider
  }
  fun capture(sensitivity: Sensitivity) {
    if (!testActive || state.value.running || progress.complete) return
    testJob = scope.launch {
      mutableSelfTest.update { it.copy(running = true, attention = null) }
      try {
        withTimeout(1_000L) { RuntimeStatusStore.status.first { !it.running } }
        val result = AttentionCaptureSession(frameSource, classifier).capture(sensitivity = sensitivity)
        val attention =
          if (result.outcome is CaptureOutcome.Completed) result.attention else Attention.Unknown
        progress = advanceSelfTest(progress, attention)
        if (progress.complete) store.completeSelfTest()
        mutableSelfTest.value = SelfTestState(
          step = when {
            progress.complete -> SelfTestStep.Complete
            progress.lookingPassed -> SelfTestStep.Away
            else -> SelfTestStep.Looking
          },
          attention = attention,
        )
      } catch (error: CancellationException) {
        throw error
      } catch (_: Exception) {
        mutableSelfTest.update { it.copy(running = false, attention = Attention.Unknown) }
      } finally {
        mutableSelfTest.update { it.copy(running = false) }
      }
    }
  }

  fun pause() {
    testJob?.cancel()
    testJob = null
  }

  fun cancel() {
    testJob?.cancel()
    testJob = null
    testActive = false
  }
  override fun close() {
    cancel()
    frameSource.close()
    classifier.close()
  }
}
