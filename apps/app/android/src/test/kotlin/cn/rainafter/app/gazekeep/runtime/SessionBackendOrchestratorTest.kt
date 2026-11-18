package cn.rainafter.app.gazekeep.runtime

import cn.rainafter.app.gazekeep.core.backend.ExtendResult
import cn.rainafter.app.gazekeep.core.backend.ExtendToken
import cn.rainafter.app.gazekeep.core.backend.KeepAwakeBackend
import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.core.model.BackendLock
import cn.rainafter.app.gazekeep.core.model.Readiness
import cn.rainafter.app.gazekeep.core.model.SetupStep
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionBackendOrchestratorTest {
  private class FakeBackend(
    override val id: BackendId,
    initial: Readiness = Readiness.NeedsSetup(SetupStep.StartShizuku),
    private val onAttach: suspend (FakeBackend) -> Unit = {},
  ) : KeepAwakeBackend {
    val mutableReadiness = MutableStateFlow(initial)
    override val readiness: StateFlow<Readiness> = mutableReadiness
    var attached = false

    override suspend fun attach() {
      attached = true
      onAttach(this)
    }

    override suspend fun detach() {
      attached = false
    }

    override suspend fun extend(token: ExtendToken) = ExtendResult.Done
    override suspend fun release() = Unit
  }

  private fun orchestrator(
    scope: CoroutineScope,
    lock: BackendLock,
    shizuku: FakeBackend,
    hasPendingBind: () -> Boolean = { false },
    lsposed: FakeBackend,
    a11y: FakeBackend? = null,
    selections: MutableList<BackendId?>,
    shizukuCreated: () -> Unit = {},
    lsposedCreated: () -> Unit = {},
    shizukuReadinessUpdates: MutableList<Readiness?> = mutableListOf(),
  ): SessionBackendOrchestrator = SessionBackendOrchestrator(
    lock = lock,
    scope = scope,
    createShizuku = {
      shizukuCreated()
      shizuku
    },
    hasPendingShizukuBind = { hasPendingBind() },
    createLsposed = {
      lsposedCreated()
      lsposed
    },
    currentAccessibility = { a11y },
    accessibilityAvailability = MutableStateFlow(a11y),
    onShizukuReadiness = { shizukuReadinessUpdates += it },
    onBackendChanged = { _, next -> selections += next?.id },
    ioDispatcher = Dispatchers.Unconfined,
    initialBindWaitMs = 3_000L,
  )

  @Test
  fun lsposedLockNeverCreatesOrAttachesShizukuBackend() = runBlocking {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    var shizukuCreated = false
    val shizuku = FakeBackend(BackendId.Shizuku, Readiness.Ready)
    val lsposed = FakeBackend(BackendId.Lsposed, Readiness.Ready)
    val selections = mutableListOf<BackendId?>()
    val orch = orchestrator(
      scope,
      BackendLock.Lsposed,
      shizuku,
      lsposed = lsposed,
      selections = selections,
      shizukuCreated = { shizukuCreated = true },
    )
    try {
      orch.start()
      assertFalse(shizukuCreated)
      assertFalse(shizuku.attached)
      assertNull(orch.shizukuBackend)
      assertTrue(lsposed.attached)
      assertEquals(listOf<BackendId?>(BackendId.Lsposed), selections)
    } finally {
      orch.cancel()
      scope.cancel()
    }
  }

  @Test
  fun shizukuLockNeverAttachesLsposedAndWaitsForPendingBindWithoutSpuriousNull() = runBlocking {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    var lsposedCreated = false
    var pendingBind = false
    val bindCompleted = CompletableDeferred<Unit>()
    val shizuku = FakeBackend(
      BackendId.Shizuku,
      Readiness.NeedsSetup(SetupStep.StartShizuku),
      onAttach = { backend ->
        pendingBind = true
        scope.launch {
          bindCompleted.await()
          pendingBind = false
          backend.mutableReadiness.value = Readiness.Ready
        }
      },
    )
    val lsposed = FakeBackend(BackendId.Lsposed, Readiness.Ready)
    val selections = mutableListOf<BackendId?>()
    val readinessUpdates = mutableListOf<Readiness?>()
    val orch = orchestrator(
      scope,
      BackendLock.Shizuku,
      shizuku,
      hasPendingBind = { pendingBind },
      lsposed = lsposed,
      selections = selections,
      lsposedCreated = { lsposedCreated = true },
      shizukuReadinessUpdates = readinessUpdates,
    )
    try {
      orch.start()
      assertFalse(lsposedCreated)
      assertNull(orch.lsposedBackend)
      assertTrue(selections.isEmpty())

      // UserService 绑定完成后直接选中 Shizuku，不发射瞬态 null 告警。
      bindCompleted.complete(Unit)
      assertEquals(listOf<BackendId?>(BackendId.Shizuku), selections)
      assertEquals(Readiness.Ready, readinessUpdates.last())
    } finally {
      orch.cancel()
      scope.cancel()
    }
  }

  @Test
  fun shizukuOfflineEmitsUnavailableImmediatelyThenRecoversWithoutDuplicateNull() = runBlocking {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val shizuku = FakeBackend(BackendId.Shizuku, Readiness.NeedsSetup(SetupStep.StartShizuku))
    val lsposed = FakeBackend(BackendId.Lsposed, Readiness.Ready)
    val selections = mutableListOf<BackendId?>()
    val orch = orchestrator(
      scope,
      BackendLock.Shizuku,
      shizuku,
      hasPendingBind = { false },
      lsposed = lsposed,
      selections = selections,
    )
    try {
      orch.start()
      assertEquals(listOf<BackendId?>(null), selections)

      // 之后用户启动 Shizuku 并恢复，直接切换到 Shizuku，不能再夹带一次 null。
      shizuku.mutableReadiness.value = Readiness.Ready
      assertEquals(listOf(null, BackendId.Shizuku), selections)
    } finally {
      orch.cancel()
      scope.cancel()
    }
  }

  @Test
  fun autoLockSelectsReadyLsposedImmediatelyWithoutWaitingForShizuku() = runBlocking {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    var pendingBind = true
    val shizuku = FakeBackend(BackendId.Shizuku, Readiness.NeedsSetup(SetupStep.StartShizuku))
    val lsposed = FakeBackend(BackendId.Lsposed, Readiness.Ready)
    val selections = mutableListOf<BackendId?>()
    val orch = orchestrator(
      scope,
      BackendLock.Auto,
      shizuku,
      hasPendingBind = { pendingBind },
      lsposed = lsposed,
      selections = selections,
    )
    try {
      orch.start()
      assertEquals(listOf<BackendId?>(BackendId.Lsposed), selections)

      pendingBind = false
      shizuku.mutableReadiness.value = Readiness.Ready
      assertEquals(listOf<BackendId?>(BackendId.Lsposed), selections)
    } finally {
      orch.cancel()
      scope.cancel()
    }
  }
}
