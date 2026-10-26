package cn.rainafter.app.gazekeep.core.backend

import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.core.model.Readiness
import kotlinx.coroutines.flow.StateFlow

interface KeepAwakeBackend {
  val id: BackendId
  val readiness: StateFlow<Readiness>
  suspend fun extend(token: ExtendToken): ExtendResult
  suspend fun release()

  /** 放弃保亮（到达上限、被切换走）时的释放，不得推迟恢复临时设置；默认与 [release] 相同。 */
  suspend fun forceRelease() = release()
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
