package cn.rainafter.app.gazekeep.core.backend

import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.core.model.Readiness
import kotlinx.coroutines.flow.StateFlow

interface KeepAwakeBackend {
    val id: BackendId
    val readiness: StateFlow<Readiness>
    suspend fun extend(token: ExtendToken): ExtendResult
    suspend fun release()
    suspend fun attach()
    suspend fun detach()
}

sealed interface ExtendToken {
    data object None : ExtendToken
    data class Seq(val value: Long) : ExtendToken
}

sealed interface ExtendResult {
    data object Done : ExtendResult
    data object Stale : ExtendResult
    data class Failed(val cause: Throwable) : ExtendResult
}
