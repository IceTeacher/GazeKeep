package cn.rainafter.app.gazekeep.ui

import cn.rainafter.app.gazekeep.core.model.Attention
import cn.rainafter.app.gazekeep.core.model.FrameVerdict
import kotlinx.coroutines.flow.StateFlow

data class M0DebugState(
    val running: Boolean = false,
    val attention: Attention? = null,
    val frames: List<FrameVerdict> = emptyList(),
    val elapsedMs: Long? = null,
    val error: M0DebugError? = null,
)

enum class M0DebugError { CameraUnavailable }

interface M0DebugController {
    val state: StateFlow<M0DebugState>
    suspend fun capture()
}

interface M1EngineController {
    val enabled: StateFlow<Boolean>
    val status: StateFlow<M1EngineStatus>
    fun setEnabled(enabled: Boolean)
}

enum class M1EngineStatus {
    Disabled,
    WaitingBackend,
    Ready,
}
