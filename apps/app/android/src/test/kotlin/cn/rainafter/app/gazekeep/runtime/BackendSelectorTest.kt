package cn.rainafter.app.gazekeep.runtime

import cn.rainafter.app.gazekeep.core.backend.ExtendResult
import cn.rainafter.app.gazekeep.core.backend.ExtendToken
import cn.rainafter.app.gazekeep.core.backend.KeepAwakeBackend
import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.core.model.BackendLock
import cn.rainafter.app.gazekeep.core.model.Readiness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class BackendSelectorTest {
  @Test
  fun autoPrefersShizukuOverAccessibility() {
    val a11y = FakeBackend(BackendId.Accessibility, Readiness.Ready)
    val shizuku = FakeBackend(BackendId.Shizuku, Readiness.Ready)
    val selector = BackendSelector(
      BackendLock.Auto,
      mutableMapOf(
        BackendId.Accessibility to a11y,
        BackendId.Shizuku to shizuku,
      ),
    )

    assertEquals(shizuku, selector.select())
  }

  @Test
  fun lockedBackendDoesNotSilentlyFallback() {
    val a11y = FakeBackend(BackendId.Accessibility, Readiness.Ready)
    val shizuku = FakeBackend(BackendId.Shizuku, Readiness.Unavailable("offline"))
    val selector = BackendSelector(
      BackendLock.Shizuku,
      mutableMapOf(
        BackendId.Accessibility to a11y,
        BackendId.Shizuku to shizuku,
      ),
    )

    assertEquals(null, selector.select())
  }

  @Test
  fun lateConnectionRegistersBackendAndDisconnectionRemovesIt() = runBlocking {
    val selector = BackendSelector(BackendLock.Accessibility, mutableMapOf())
    val a11y = FakeBackend(BackendId.Accessibility, Readiness.Ready)
    val availability = MutableStateFlow<KeepAwakeBackend?>(null)
    val selected = mutableListOf<KeepAwakeBackend?>()
    val job = launch(Dispatchers.Unconfined) {
      selector.trackAvailability(BackendId.Accessibility, availability) { selected.add(selector.select()) }
    }
    try {
      availability.value = a11y
      assertEquals(listOf(null, a11y), selected)
      availability.value = null
      assertEquals(listOf(null, a11y, null), selected)
      availability.value = a11y
      assertEquals(listOf(null, a11y, null, a11y), selected)
    } finally {
      job.cancel()
    }
  }

  @Test
  fun reconnectionReplacesInstanceAndStopsWatchingOldReadiness() = runBlocking {
    val old = FakeBackend(BackendId.Accessibility, Readiness.Ready)
    val replacement = FakeBackend(BackendId.Accessibility, Readiness.Ready)
    val selector = BackendSelector(BackendLock.Accessibility, mutableMapOf())
    val availability = MutableStateFlow<KeepAwakeBackend?>(old)
    val selected = mutableListOf<KeepAwakeBackend?>()
    val job = launch(Dispatchers.Unconfined) {
      selector.trackAvailability(BackendId.Accessibility, availability) { selected.add(selector.select()) }
    }
    try {
      availability.value = replacement
      old.setReadiness(Readiness.Unavailable("disconnected"))
      assertEquals(listOf(old, replacement), selected)
      replacement.setReadiness(Readiness.Unavailable("disconnected"))
      assertEquals(listOf(old, replacement, null), selected)
    } finally {
      job.cancel()
    }
  }

  @Test
  fun failedAttachDoesNotSelectBackend() = runBlocking {
    val a11y = FakeBackend(BackendId.Accessibility, Readiness.Ready, failAttach = true)
    val selector = BackendSelector(BackendLock.Accessibility, mutableMapOf())
    val selected = mutableListOf<KeepAwakeBackend?>()
    selector.trackAvailability(BackendId.Accessibility, flowOf(a11y)) { selected.add(selector.select()) }
    assertEquals(listOf(null), selected)
  }

  @Test
  fun cancelledSessionDoesNotRegisterLaterConnections() = runBlocking {
    val selector = BackendSelector(BackendLock.Accessibility, mutableMapOf())
    val availability = MutableStateFlow<KeepAwakeBackend?>(null)
    var changes = 0
    val job = launch(Dispatchers.Unconfined) {
      selector.trackAvailability(BackendId.Accessibility, availability) { changes++ }
    }
    job.cancel()
    job.join()
    availability.value = FakeBackend(BackendId.Accessibility, Readiness.Ready)
    assertEquals(null, selector.select())
    assertEquals(1, changes)
  }

  private class FakeBackend(
    override val id: BackendId,
    initialReadiness: Readiness,
    private val failAttach: Boolean = false,
  ) : KeepAwakeBackend {
    private val mutableReadiness = MutableStateFlow(initialReadiness)
    override val readiness: StateFlow<Readiness> = mutableReadiness
    override suspend fun extend(token: ExtendToken): ExtendResult = ExtendResult.Done
    override suspend fun release() = Unit
    fun setReadiness(readiness: Readiness) {
      mutableReadiness.value = readiness
    }
    override suspend fun attach() {
      if (failAttach) error("attach failed")
    }
    override suspend fun detach() = Unit
  }
}
