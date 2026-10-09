package cn.rainafter.app.gazekeep.runtime

import cn.rainafter.app.gazekeep.core.backend.KeepAwakeBackend
import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.core.model.BackendLock
import cn.rainafter.app.gazekeep.core.model.Readiness
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

/** 按用户锁定值或产品优先级选择已就绪的后端。 */
class BackendSelector(private val lock: BackendLock, private val backends: MutableMap<BackendId, KeepAwakeBackend>) {
  fun register(backend: KeepAwakeBackend) {
    backends[backend.id] = backend
  }

  fun unregister(id: BackendId) {
    backends.remove(id)
  }

  /** 会话持续跟踪实例及其就绪状态；新实例替换旧实例的订阅。 */
  suspend fun trackAvailability(
    id: BackendId,
    availability: Flow<KeepAwakeBackend?>,
    onSelectionChanged: suspend () -> Unit,
  ) {
    availability.collectLatest { backend ->
      unregister(id)
      if (backend == null) {
        onSelectionChanged()
        return@collectLatest
      }
      try {
        backend.attach()
      } catch (error: CancellationException) {
        throw error
      } catch (_: Exception) {
        onSelectionChanged()
        return@collectLatest
      }
      register(backend)
      backend.readiness.collectLatest { onSelectionChanged() }
    }
  }

  fun select(): KeepAwakeBackend? {
    val candidates = when (lock) {
      BackendLock.Auto -> PRIORITY
      BackendLock.Lsposed -> listOf(BackendId.Lsposed)
      BackendLock.Shizuku -> listOf(BackendId.Shizuku)
      BackendLock.Accessibility -> listOf(BackendId.Accessibility)
    }
    return candidates.asSequence()
      .mapNotNull(backends::get)
      .firstOrNull { it.readiness.value is Readiness.Ready }
  }

  private companion object {
    val PRIORITY = listOf(BackendId.Lsposed, BackendId.Shizuku, BackendId.Accessibility)
  }
}
