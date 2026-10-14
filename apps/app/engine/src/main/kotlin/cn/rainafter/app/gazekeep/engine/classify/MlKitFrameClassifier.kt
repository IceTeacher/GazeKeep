package cn.rainafter.app.gazekeep.engine.classify

import androidx.camera.core.ImageProxy
import cn.rainafter.app.gazekeep.core.model.ClassifierThresholds
import cn.rainafter.app.gazekeep.core.model.FaceFeatures
import cn.rainafter.app.gazekeep.core.model.FrameVerdict
import cn.rainafter.app.gazekeep.core.model.Sensitivity
import cn.rainafter.app.gazekeep.core.policy.judgeFeatures
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class MlKitFrameClassifier(private val thresholds: ClassifierThresholds = ClassifierThresholds()) : FrameClassifier {
  private val detector: FaceDetector = FaceDetection.getClient(
    FaceDetectorOptions.Builder()
      .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
      .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
      .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
      .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
      .setMinFaceSize(0.15f)
      .build(),
  )

  private val lifetime = ClassifierLifetime { detector.close() }

  override suspend fun classify(image: ImageProxy, sensitivity: Sensitivity): FrameVerdict = lifetime.use {
    val mediaImage = image.image ?: return@use FrameVerdict.NotLooking(FrameVerdict.Reason.ClassifierError)
    if (LumaProbe.isTooDark(image)) return@use FrameVerdict.NotLooking(FrameVerdict.Reason.TooDark)
    try {
      val faces = detector.process(
        InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees),
      ).awaitClassification()
      val rotatedWidth = if (image.imageInfo.rotationDegrees % 180 == 0) image.width else image.height
      val minWidth = thresholds.minFaceWidthRatio * 0.6f
      val significant = faces.filter { it.boundingBox.width().toFloat() / rotatedWidth >= minWidth }
      if (significant.isEmpty()) {
        FrameVerdict.NotLooking(FrameVerdict.Reason.NoFace)
      } else {
        val face = significant.first()
        judgeFeatures(
          FaceFeatures(
            yawDeg = face.headEulerAngleY,
            pitchDeg = face.headEulerAngleX,
            leftEyeOpenProbability = face.leftEyeOpenProbability,
            rightEyeOpenProbability = face.rightEyeOpenProbability,
            faceWidthRatio = face.boundingBox.width().toFloat() / rotatedWidth,
            faceCount = significant.size,
          ),
          sensitivity,
          thresholds,
        )
      }
    } catch (error: CancellationException) {
      throw error
    } catch (_: Exception) {
      FrameVerdict.NotLooking(FrameVerdict.Reason.ClassifierError)
    }
  }

  override fun close() {
    lifetime.close()
  }
}

private suspend fun <T> Task<T>.awaitClassification(): T = awaitClassificationResult { success, failure, cancelled ->
  addOnSuccessListener({ it.run() }, success)
    .addOnFailureListener({ it.run() }, failure)
    .addOnCanceledListener({ it.run() }, cancelled)
}

/** SDK 自行取消属于本轮分类失败；调用方取消仍由 continuation 传播。 */
internal suspend fun <T> awaitClassificationResult(
  register: (success: (T) -> Unit, failure: (Exception) -> Unit, cancelled: () -> Unit) -> Unit,
): T = suspendCancellableCoroutine { continuation ->
  register(
    { if (continuation.isActive) continuation.resume(it) },
    { if (continuation.isActive) continuation.resumeWith(Result.failure(it)) },
    {
      if (continuation.isActive) {
        continuation.resumeWith(
          Result.failure(IllegalStateException("classification task cancelled")),
        )
      }
    },
  )
}
