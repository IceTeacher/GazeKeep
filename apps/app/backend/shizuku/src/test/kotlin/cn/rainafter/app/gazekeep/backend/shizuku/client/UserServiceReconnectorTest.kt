package cn.rainafter.app.gazekeep.backend.shizuku.client

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class UserServiceReconnectorTest {
  private class Fixture(scope: TestScope, var binderAlive: Boolean = true) {
    var binds = 0
    val reconnector = UserServiceReconnector(
      scope = scope.backgroundScope,
      binderAlive = { binderAlive },
      bind = { binds++ },
      delaysMs = listOf(1_000L, 2_000L, 5_000L),
    )
  }

  @Test
  fun rebindsAfterUserServiceDiesWhileShizukuIsAlive() = runTest {
    val fixture = Fixture(this)
    fixture.reconnector.onLost()
    advanceTimeBy(999L)
    assertEquals(0, fixture.binds)
    advanceTimeBy(1L)
    runCurrent()
    assertEquals(1, fixture.binds)
  }

  @Test
  fun backsOffAndCapsDelayUntilConnected() = runTest {
    val fixture = Fixture(this)
    repeat(4) {
      fixture.reconnector.onLost()
      advanceTimeBy(5_000L)
      runCurrent()
    }
    assertEquals(4, fixture.binds)
    // 第 5 次失败仍使用最大退避，连接成功后重置为首个间隔。
    fixture.reconnector.onLost()
    advanceTimeBy(4_999L)
    assertEquals(4, fixture.binds)
    advanceTimeBy(1L)
    runCurrent()
    assertEquals(5, fixture.binds)
    fixture.reconnector.onConnected()
    fixture.reconnector.onLost()
    advanceTimeBy(1_000L)
    runCurrent()
    assertEquals(6, fixture.binds)
  }

  @Test
  fun waitsForShizukuWhenBinderIsDead() = runTest {
    val fixture = Fixture(this, binderAlive = false)
    fixture.reconnector.onLost()
    advanceTimeBy(60_000L)
    runCurrent()
    assertEquals(0, fixture.binds)
  }

  @Test
  fun cancelDropsPendingRebindAndRepeatedLossKeepsOneAttempt() = runTest {
    val fixture = Fixture(this)
    fixture.reconnector.onLost()
    fixture.reconnector.onLost()
    advanceTimeBy(1_000L)
    runCurrent()
    assertEquals(1, fixture.binds)
    fixture.reconnector.onLost()
    fixture.reconnector.cancel()
    advanceTimeBy(60_000L)
    runCurrent()
    assertEquals(1, fixture.binds)
  }
}
