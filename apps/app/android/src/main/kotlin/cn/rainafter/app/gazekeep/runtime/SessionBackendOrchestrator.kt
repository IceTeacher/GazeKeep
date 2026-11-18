package cn.rainafter.app.gazekeep.runtime

import cn.rainafter.app.gazekeep.core.backend.KeepAwakeBackend
import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.core.model.BackendLock
import cn.rainafter.app.gazekeep.core.model.Readiness
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** 按 BackendLock 裁剪并协调后端附着、初始就绪等待与切换通知，避免瞬态 null 与并发取消破坏状态。 */
internal class SessionBackendOrchestrator(
  private val lock: BackendLock,
  private val scope: CoroutineScope,
  private val createShizuku: () -> KeepAwakeBackend,
  private val hasPendingShizukuBind: (KeepAwakeBackend) -> Boolean,
  private val createLsposed: () -> KeepAwakeBackend,
  private val currentAccessibility: () -> KeepAwakeBackend?,
  private val accessibilityAvailability: Flow<KeepAwakeBackend?>,
  private val onShizukuReadiness: (Readiness?) -> Unit = {},
  private val onRetainSameBackend: suspend (KeepAwakeBackend?) -> Unit = {},
  private val onBackendChanged: suspend (previous: KeepAwakeBackend?, next: KeepAwakeBackend?) -> Unit,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
  private val initialBindWaitMs: Long = INITIAL_SHIZUKU_WAIT_MS,
) {
  private val switchMutex = Mutex()
  private val switchRequests = Channel<Unit>(Channel.CONFLATED)
  private var initialized = false
  private var currentBackend: KeepAwakeBackend? = null

  var shizukuBackend: KeepAwakeBackend? = null
    private set
  var lsposedBackend: KeepAwakeBackend? = null
    private set

  private var mainJob: Job? = null
  private var switchWorkerJob: Job? = null
  private var shizukuJob: Job? = null
  private var lsposedJob: Job? = null
  private var a11yJob: Job? = null

  fun start() {
    val allowShizuku = lock == BackendLock.Auto || lock == BackendLock.Shizuku
    val allowLsposed = lock == BackendLock.Auto || lock == BackendLock.Lsposed
    val allowAccessibility = lock == BackendLock.Auto || lock == BackendLock.Accessibility

    val a11y = if (allowAccessibility) currentAccessibility() else null
    val shizuku = if (allowShizuku) createShizuku().also { shizukuBackend = it } else null
    val lsposed = if (allowLsposed) createLsposed().also { lsposedBackend = it } else null

    val candidates = mutableMapOf<BackendId, KeepAwakeBackend>().apply {
      a11y?.let { put(BackendId.Accessibility, it) }
      shizuku?.let { put(BackendId.Shizuku, it) }
      lsposed?.let { put(BackendId.Lsposed, it) }
    }
    val selector = BackendSelector(lock, candidates)

    mainJob = scope.launch {
      if (lsposed != null) {
        withContext(ioDispatcher) { lsposed.attach() }
      }
      val shizukuAttachedInline = shizuku != null && selector.select() == null
      if (shizukuAttachedInline && shizuku != null) {
        shizuku.attach()
        onShizukuReadiness(shizuku.readiness.value)
        if (selector.select() == null && hasPendingShizukuBind(shizuku)) {
          withTimeoutOrNull(initialBindWaitMs) {
            shizuku.readiness.first { it is Readiness.Ready || !hasPendingShizukuBind(shizuku) }
          }
          onShizukuReadiness(shizuku.readiness.value)
        }
      }

      switchBackend(selector)

      // 切换任务由独立串行队列消费，readiness 快速抖动只合并信号，不取消正在进行的切换。
      switchWorkerJob = scope.launch {
        for (signal in switchRequests) {
          switchBackend(selector)
        }
      }

      if (shizuku != null) {
        shizukuJob = scope.launch {
          if (!shizukuAttachedInline) {
            shizuku.attach()
            onShizukuReadiness(shizuku.readiness.value)
          }
          shizuku.readiness.collect { readiness ->
            onShizukuReadiness(readiness)
            switchRequests.trySend(Unit)
          }
        }
      }
      if (lsposed != null) {
        lsposedJob = scope.launch {
          lsposed.readiness.collect { switchRequests.trySend(Unit) }
        }
      }
      if (allowAccessibility) {
        a11yJob = scope.launch {
          selector.trackAvailability(BackendId.Accessibility, accessibilityAvailability) {
            switchRequests.trySend(Unit)
          }
        }
      }
    }
  }

  fun cancel() {
    switchRequests.close()
    mainJob?.cancel()
    mainJob = null
    switchWorkerJob?.cancel()
    switchWorkerJob = null
    shizukuJob?.cancel()
    shizukuJob = null
    lsposedJob?.cancel()
    lsposedJob = null
    a11yJob?.cancel()
    a11yJob = null
  }

  private suspend fun switchBackend(selector: BackendSelector): Unit = switchMutex.withLock {
    val target = selector.select()
    val previous = currentBackend
    if (initialized && target === previous) {
      onRetainSameBackend(target)
      return@withLock
    }
    // 只有切换动作完整结束后才提交目标后端，避免被取消的中间切换被后续误判为已切换完成。
    onBackendChanged(previous, target)
    initialized = true
    currentBackend = target
  }

  private companion object {
    const val INITIAL_SHIZUKU_WAIT_MS = 3_000L
  }
}
