package cn.rainafter.app.gazekeep.ui

import cn.rainafter.app.gazekeep.core.model.BackendLock
import kotlinx.coroutines.flow.StateFlow

enum class M2ShizukuStatus {
    Unknown,
    NeedsStart,
    NeedsPermission,
    Ready,
    Unavailable,
}

interface M2ShizukuController {
    val status: StateFlow<M2ShizukuStatus>
    val backendLock: StateFlow<BackendLock>
    val accessibilityEnabled: StateFlow<Boolean>
    val lsposedReady: StateFlow<Boolean>
    fun refresh()
    fun requestPermission()
    fun setBackendLock(lock: BackendLock)
    fun openAccessibilitySettings()
    fun close()
}
