package cn.rainafter.app.gazekeep.core.policy

import cn.rainafter.app.gazekeep.core.model.ClassifierThresholds
import cn.rainafter.app.gazekeep.core.model.FaceFeatures
import cn.rainafter.app.gazekeep.core.model.FrameVerdict
import cn.rainafter.app.gazekeep.core.model.Sensitivity
import kotlin.math.abs

fun judgeFeatures(
  features: FaceFeatures,
  sensitivity: Sensitivity,
  thresholds: ClassifierThresholds = ClassifierThresholds(),
): FrameVerdict {
  if (features.faceCount <= 0) return FrameVerdict.NotLooking(FrameVerdict.Reason.NoFace)
  if (features.faceCount > 1) return FrameVerdict.NotLooking(FrameVerdict.Reason.MultipleFaces)
  if (features.faceWidthRatio < thresholds.minFaceWidthRatio) {
    return FrameVerdict.NotLooking(FrameVerdict.Reason.FaceTooSmall)
  }
  if (abs(features.yawDeg) > thresholds.maxYawDeg || abs(features.pitchDeg) > thresholds.maxPitchDeg) {
    return FrameVerdict.NotLooking(FrameVerdict.Reason.HeadTurned)
  }
  if (sensitivity == Sensitivity.Loose) return FrameVerdict.Looking
  if ((features.leftEyeOpenProbability ?: 0f) < thresholds.minEyeOpen ||
    (features.rightEyeOpenProbability ?: 0f) < thresholds.minEyeOpen
  ) {
    return FrameVerdict.NotLooking(FrameVerdict.Reason.EyesClosed)
  }
  return FrameVerdict.Looking
}
