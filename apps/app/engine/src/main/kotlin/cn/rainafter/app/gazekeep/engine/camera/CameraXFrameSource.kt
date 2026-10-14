package cn.rainafter.app.gazekeep.engine.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import cn.rainafter.app.gazekeep.core.engine.AbortReason
import cn.rainafter.app.gazekeep.core.engine.CapturePlan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 短生命周期前摄采集器；仅自检可附加预览，不复制 ImageProxy。 */
class CameraXFrameSource(context: Context) :
  FrameSource,
  AutoCloseable {
  private val providerFuture = ProcessCameraProvider.getInstance(context.applicationContext)
  private val lifecycle =
    CaptureLifecycle(kotlinx.coroutines.Dispatchers.Main.immediate, android.os.SystemClock::uptimeMillis)

  @Volatile var previewSurfaceProvider: Preview.SurfaceProvider? = null

  override suspend fun capture(plan: CapturePlan, onFrame: (ImageProxy) -> Boolean): CaptureOutcome {
    require(plan.maxFrames > 0)
    return lifecycle.capture(plan.abortAtUptime) {
      val provider = providerFuture.await()
      val create: () -> CaptureBinding = { CaptureResources(provider, plan, onFrame, previewSurfaceProvider) }
      create
    }
  }

  override fun close() {
    previewSurfaceProvider = null
    lifecycle.close()
  }

  private class CaptureResources(
    private val provider: ProcessCameraProvider,
    private val plan: CapturePlan,
    private val onFrame: (ImageProxy) -> Boolean,
    surface: Preview.SurfaceProvider?,
  ) : CaptureBinding {
    private val owner = CaptureLifecycleOwner()
    private val done = AtomicBoolean(false)
    private val analysis = ImageAnalysis.Builder()
      .setTargetResolution(android.util.Size(640, 480))
      .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
      .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
      .build()
    private val preview = surface?.let { Preview.Builder().build().also { preview -> preview.setSurfaceProvider(it) } }
    private val executor = Executors.newSingleThreadExecutor { runnable ->
      Thread(runnable, "GazeKeep-Analyzer").apply { isDaemon = true }
    }

    override suspend fun capture(): CaptureOutcome = suspendCancellableCoroutine { continuation ->
      fun finish(outcome: CaptureOutcome, cancellation: CancellationException? = null) {
        if (!done.compareAndSet(false, true)) return
        if (continuation.isActive) {
          if (cancellation != null) continuation.resumeWithException(cancellation) else continuation.resume(outcome)
        }
      }
      continuation.invokeOnCancellation { done.set(true) }
      if (!continuation.isActive) return@suspendCancellableCoroutine
      val startedAt = android.os.SystemClock.uptimeMillis()
      var warmupFrames = 0
      var accepted = 0
      var lastAcceptedAt = Long.MIN_VALUE
      analysis.setAnalyzer(executor) { image ->
        if (done.get()) {
          image.close()
          return@setAnalyzer
        }
        val now = android.os.SystemClock.uptimeMillis()
        if (now >= plan.abortAtUptime) {
          image.close()
          finish(CaptureOutcome.Aborted(AbortReason.Deadline))
          return@setAnalyzer
        }
        if (warmupFrames < 2 && now - startedAt < 200L) {
          warmupFrames++
          image.close()
          return@setAnalyzer
        }
        if (lastAcceptedAt != Long.MIN_VALUE && now - lastAcceptedAt < plan.frameIntervalMs) {
          image.close()
          return@setAnalyzer
        }
        accepted++
        lastAcceptedAt = now
        val frame = analyzeFrame(image, onFrame)
        val error = frame.exceptionOrNull()
        when {
          error is CancellationException -> finish(CaptureOutcome.Aborted(AbortReason.Cancelled), error)
          error != null -> finish(CaptureOutcome.Aborted(AbortReason.Error))
          !frame.getOrThrow() || accepted >= plan.maxFrames -> finish(CaptureOutcome.Completed)
        }
      }
      try {
        owner.resume()
        if (done.get()) return@suspendCancellableCoroutine
        if (preview == null) {
          provider.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
        } else {
          provider.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, analysis, preview)
        }
      } catch (_: Exception) {
        finish(CaptureOutcome.Aborted(AbortReason.Error))
      }
    }

    override suspend fun release() {
      done.set(true)
      val failures = listOf(
        runCatching { analysis.clearAnalyzer() },
        runCatching { if (preview == null) provider.unbind(analysis) else provider.unbind(analysis, preview) },
        runCatching { owner.destroy() },
      )
      // ML Kit 仍可能读取在途图像；不 shutdownNow，不在回调结束前关闭图像。
      val drained = kotlinx.coroutines.CompletableDeferred<Unit>()
      executor.execute { drained.complete(Unit) }
      executor.shutdown()
      drained.await()
      failures.firstNotNullOfOrNull { it.exceptionOrNull() }?.let { throw it }
    }
  }

  private class CaptureLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override fun getLifecycle(): Lifecycle = registry
    fun resume() {
      registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
      registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
      registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }
    fun destroy() {
      if (registry.currentState != Lifecycle.State.INITIALIZED) registry.currentState = Lifecycle.State.DESTROYED
    }
  }
}

private suspend fun <T> com.google.common.util.concurrent.ListenableFuture<T>.await(): T =
  suspendCancellableCoroutine { continuation ->
    addListener(
      {
        if (continuation.isActive) {
          try {
            continuation.resume(get())
          } catch (error: Exception) {
            if (continuation.isActive) continuation.resumeWithException(error)
          }
        }
      },
      Executor { runnable -> runnable.run() },
    )
  }

/** 图像在同步回调返回后关闭，随后由调用方发布结果并解绑相机。 */
internal fun analyzeFrame(image: ImageProxy, onFrame: (ImageProxy) -> Boolean): Result<Boolean> = try {
  Result.success(onFrame(image))
} catch (error: Throwable) {
  Result.failure(error)
} finally {
  image.close()
}
