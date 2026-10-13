package cn.rainafter.app.gazekeep.engine.camera

import android.content.Context
import android.os.Handler
import android.os.Looper
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
  private val appContext = context.applicationContext
  private val mainHandler = Handler(Looper.getMainLooper())
  private val providerFuture = ProcessCameraProvider.getInstance(appContext)

  var previewSurfaceProvider: Preview.SurfaceProvider? = null

  override suspend fun capture(plan: CapturePlan, onFrame: (ImageProxy) -> Boolean): CaptureOutcome {
    require(plan.maxFrames > 0)
    val provider = providerFuture.await()
    val owner = CaptureLifecycleOwner()
    val executor = Executors.newSingleThreadExecutor { runnable ->
      Thread(runnable, "GazeKeep-Analyzer").apply { isDaemon = true }
    }
    val done = AtomicBoolean(false)
    val startedAt = android.os.SystemClock.uptimeMillis()
    var warmupFrames = 0
    var accepted = 0
    var lastAcceptedAt = Long.MIN_VALUE
    return suspendCancellableCoroutine { continuation ->
      fun finish(outcome: CaptureOutcome, cancellation: CancellationException? = null) {
        if (!done.compareAndSet(false, true)) return
        mainHandler.post {
          mainHandler.removeCallbacksAndMessages(owner)
          provider.unbindAll()
          owner.destroy()
          executor.shutdownNow()
          if (continuation.isActive) {
            if (cancellation != null) continuation.resumeWithException(cancellation) else continuation.resume(outcome)
          }
        }
      }

      continuation.invokeOnCancellation {
        finish(CaptureOutcome.Aborted(AbortReason.Cancelled))
      }

      val remaining = (plan.abortAtUptime - android.os.SystemClock.uptimeMillis()).coerceAtLeast(1L)
      mainHandler.postAtTime(
        { finish(CaptureOutcome.Aborted(AbortReason.Deadline)) },
        owner,
        android.os.SystemClock.uptimeMillis() + remaining,
      )
      if (done.get()) return@suspendCancellableCoroutine

      val analysis = ImageAnalysis.Builder()
        .setTargetResolution(android.util.Size(640, 480))
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
        .build()

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
        val preview = previewSurfaceProvider?.let { surface ->
          Preview.Builder().build().also { it.setSurfaceProvider(surface) }
        }
        if (preview == null) {
          provider.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
        } else {
          provider.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, analysis, preview)
        }
      } catch (_: Throwable) {
        analysis.clearAnalyzer()
        finish(CaptureOutcome.Aborted(AbortReason.Error))
      }
    }
  }

  override fun close() {
    previewSurfaceProvider = null
    // providerFuture 由 ProcessCameraProvider 管理；每轮 capture 都会 unbindAll。
  }

  private class CaptureLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    private val lifecycle: Lifecycle = registry

    override fun getLifecycle(): Lifecycle = lifecycle

    fun resume() {
      registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
      registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
      registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun destroy() {
      if (registry.currentState != Lifecycle.State.DESTROYED) {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
      }
    }
  }
}

private suspend fun <T> com.google.common.util.concurrent.ListenableFuture<T>.await(): T =
  suspendCancellableCoroutine { continuation ->
    addListener(
      {
        try {
          continuation.resume(get())
        } catch (error: Throwable) {
          if (continuation.isActive) continuation.resumeWithException(error)
        }
      },
      Executor { runnable -> runnable.run() },
    )
    continuation.invokeOnCancellation { cancel(true) }
  }

/** 图像在同步回调返回后关闭，随后由调用方发布结果并解绑相机。 */
internal fun analyzeFrame(image: ImageProxy, onFrame: (ImageProxy) -> Boolean): Result<Boolean> = try {
  Result.success(onFrame(image))
} catch (error: Throwable) {
  Result.failure(error)
} finally {
  image.close()
}
