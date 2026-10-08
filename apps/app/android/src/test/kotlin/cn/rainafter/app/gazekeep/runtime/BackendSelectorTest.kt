package cn.rainafter.app.gazekeep.runtime

import cn.rainafter.app.gazekeep.core.backend.ExtendResult
import cn.rainafter.app.gazekeep.core.backend.ExtendToken
import cn.rainafter.app.gazekeep.core.backend.KeepAwakeBackend
import cn.rainafter.app.gazekeep.core.model.BackendId
import cn.rainafter.app.gazekeep.core.model.BackendLock
import cn.rainafter.app.gazekeep.core.model.Readiness
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

  private class FakeBackend(override val id: BackendId, initialReadiness: Readiness) : KeepAwakeBackend {
    private val mutableReadiness = MutableStateFlow(initialReadiness)
    override val readiness: StateFlow<Readiness> = mutableReadiness
    override suspend fun extend(token: ExtendToken): ExtendResult = ExtendResult.Done
    override suspend fun release() = Unit
    override suspend fun attach() = Unit
    override suspend fun detach() = Unit
  }
}
