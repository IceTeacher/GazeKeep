package cn.rainafter.app.gazekeep.core.model

/** 一轮检测的结论；Unknown 在保亮决策上必须按 Away 处理。 */
enum class Attention { Looking, Away, Unknown }

/** 单帧结论。调试页可以展示原因，但原因不包含图像或人脸数据。 */
sealed interface FrameVerdict {
    data object Looking : FrameVerdict

    data class NotLooking(val reason: Reason) : FrameVerdict

    enum class Reason {
        NoFace,
        MultipleFaces,
        FaceTooSmall,
        HeadTurned,
        EyesClosed,
        TooDark,
        ClassifierError,
    }
}

enum class Sensitivity { Standard, Loose }

data class ClassifierThresholds(
    val maxYawDeg: Float = 25f,
    val maxPitchDeg: Float = 25f,
    val minEyeOpen: Float = 0.6f,
    val minFaceWidthRatio: Float = 0.18f,
)

/** ML Kit 无关的特征快照，便于在 commonTest 中冻结判定边界。 */
data class FaceFeatures(
    val yawDeg: Float,
    val pitchDeg: Float,
    val leftEyeOpenProbability: Float?,
    val rightEyeOpenProbability: Float?,
    val faceWidthRatio: Float,
    val faceCount: Int,
)
