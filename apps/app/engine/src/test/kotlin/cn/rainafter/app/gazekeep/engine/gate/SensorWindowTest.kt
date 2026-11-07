package cn.rainafter.app.gazekeep.engine.gate

import cn.rainafter.app.gazekeep.engine.gate.SensorWindow.Kind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SensorWindowTest {
  private var now = 0L
  private val window = SensorWindow { now }
  private val all = setOf(Kind.Proximity, Kind.Light, Kind.Accel)

  @Test fun settlesOnlyAfterEveryRegisteredSensorReports() {
    window.offer(Kind.Light, 120f)
    window.offer(Kind.Accel, 0.2f)
    assertFalse(window.settled(all))

    window.offer(Kind.Proximity, 5f)
    assertTrue(window.settled(all))
    // 设备缺少的传感器不注册，也不等待。
    assertTrue(window.settled(setOf(Kind.Light)))
  }

  @Test fun faceDownNeedsThreeHundredMillisecondsOfDownSamples() {
    window.offer(Kind.Proximity, 5f)
    window.offer(Kind.Light, 50f)
    window.offer(Kind.Accel, -0.95f)
    now = 200L
    window.offer(Kind.Accel, -0.95f)
    assertFalse(window.settled(all))
    assertEquals(false, window.faceDown())

    now = 300L
    window.offer(Kind.Accel, -0.95f)
    assertTrue(window.settled(all))
    assertEquals(true, window.faceDown())
  }

  @Test fun liftingThePhoneRestartsTheDownTimer() {
    window.offer(Kind.Accel, -0.95f)
    now = 200L
    window.offer(Kind.Accel, 0.1f)
    now = 250L
    window.offer(Kind.Accel, -0.95f)
    now = 400L
    assertEquals(false, window.faceDown())
  }

  @Test fun resetDropsSamplesFromThePreviousWindow() {
    window.offer(Kind.Light, 1f)
    window.offer(Kind.Accel, -0.95f)
    now = 400L
    window.reset()

    assertNull(window.value(Kind.Light))
    assertNull(window.faceDown())
    assertFalse(window.settled(setOf(Kind.Light)))
  }
}
