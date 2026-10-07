package cn.rainafter.app.gazekeep

import android.content.Context
import cn.rainafter.app.gazekeep.core.model.Sensitivity
import cn.rainafter.app.gazekeep.engine.camera.CameraXFrameSource
import cn.rainafter.app.gazekeep.engine.classify.MlKitFrameClassifier
import cn.rainafter.app.gazekeep.engine.m0.M0CaptureSession
import cn.rainafter.app.gazekeep.ui.M0DebugController
import cn.rainafter.app.gazekeep.ui.M0DebugState
import cn.rainafter.app.gazekeep.ui.M0DebugError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AndroidM0DebugController(context: Context) : M0DebugController, AutoCloseable {
    private val frameSource = CameraXFrameSource(context)
    private val classifier = MlKitFrameClassifier()
    private val session = M0CaptureSession(frameSource, classifier)
    private val mutableState = MutableStateFlow(M0DebugState())
    override val state: StateFlow<M0DebugState> = mutableState

    override suspend fun capture() {
        mutableState.value = M0DebugState(running = true)
        try {
            val result = session.capture(sensitivity = Sensitivity.Standard)
            mutableState.value = M0DebugState(
                attention = result.attention,
                frames = result.frames,
                elapsedMs = result.elapsedMs,
            )
        } catch (error: Throwable) {
            mutableState.value = M0DebugState(error = M0DebugError.CameraUnavailable)
        }
    }

    override fun close() {
        classifier.close()
        frameSource.close()
    }
}
