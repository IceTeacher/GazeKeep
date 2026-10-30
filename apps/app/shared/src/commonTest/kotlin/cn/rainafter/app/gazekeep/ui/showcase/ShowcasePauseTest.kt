package cn.rainafter.app.gazekeep.ui.showcase

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ShowcasePauseTest {
  @Test
  fun waitCountsOnlyVisibleTime() = runTest {
    val pause = ShowcasePause(testTimeSource)
    val done = async {
      pause.delay(1000)
      currentTime
    }
    advanceTimeBy(400)
    pause.set(false)
    advanceTimeBy(5000)
    assertFalse(done.isCompleted, "离开视口期间不计时")
    pause.set(true)
    advanceUntilIdle()
    // 可见 400ms + 离开 5000ms + 回来后补足剩余 600ms。
    assertEquals(6000, done.await())
  }

  @Test
  fun pauseRepeatedlyStillCompletesFullDuration() = runTest {
    val pause = ShowcasePause(testTimeSource)
    val done = async {
      pause.delay(900)
      currentTime
    }
    repeat(3) {
      advanceTimeBy(200)
      pause.set(false)
      advanceTimeBy(1000)
      pause.set(true)
    }
    advanceUntilIdle()
    assertEquals(3 * 1200L + 300, done.await())
  }

  @Test
  fun awaitActiveReportsWhetherItWaited() = runTest {
    val pause = ShowcasePause(testTimeSource)
    assertFalse(pause.awaitActive(), "可见时立即通过")
    pause.set(false)
    val waited = async { pause.awaitActive() }
    runCurrent()
    assertFalse(waited.isCompleted)
    pause.set(true)
    assertTrue(waited.await())
  }
}
