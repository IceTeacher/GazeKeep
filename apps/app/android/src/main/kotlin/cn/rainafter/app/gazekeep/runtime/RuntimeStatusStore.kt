package cn.rainafter.app.gazekeep.runtime

import cn.rainafter.app.gazekeep.core.model.Attention
import cn.rainafter.app.gazekeep.core.model.BackendId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RuntimeStatus(
  val running: Boolean = false,
  val backend: BackendId? = null,
  val lastDecision: Attention? = null,
)

/** 只发布 reducer 的输出与服务生命周期，不能通过此对象驱动决策。 */
object RuntimeStatusStore {
  @Volatile var intentionalStop: Boolean = false
  private val mutableStatus = MutableStateFlow(RuntimeStatus())
  val status = mutableStatus.asStateFlow()
  fun publish(status: RuntimeStatus) {
    mutableStatus.value = status
  }
}
