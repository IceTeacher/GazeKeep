package cn.rainafter.app.gazekeep.engine.classify

import androidx.camera.core.ImageProxy
import cn.rainafter.app.gazekeep.core.model.FrameVerdict
import cn.rainafter.app.gazekeep.core.model.Sensitivity
import java.io.Closeable

interface FrameClassifier : Closeable {
  suspend fun classify(image: ImageProxy, sensitivity: Sensitivity): FrameVerdict
}
