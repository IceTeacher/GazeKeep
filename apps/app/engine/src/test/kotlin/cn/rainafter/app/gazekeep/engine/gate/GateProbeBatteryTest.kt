package cn.rainafter.app.gazekeep.engine.gate

import android.os.BatteryManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GateProbeBatteryTest {
  @Test
  fun parseBatteryStateTreatsPluggedAsChargingAndRejectsUnpluggedFullResidual() {
    val acCharging = AndroidGateProbe.parseBatteryState(
      level = 50,
      scale = 100,
      plugged = BatteryManager.BATTERY_PLUGGED_AC,
      status = BatteryManager.BATTERY_STATUS_CHARGING,
    )
    assertEquals(50, acCharging.percent)
    assertTrue(acCharging.charging)

    val usbFull = AndroidGateProbe.parseBatteryState(
      level = 100,
      scale = 100,
      plugged = BatteryManager.BATTERY_PLUGGED_USB,
      status = BatteryManager.BATTERY_STATUS_FULL,
    )
    assertEquals(100, usbFull.percent)
    assertTrue(usbFull.charging)

    // 80% 充电保护停充但仍插电源时 plugged != 0，须保持充电豁免有效。
    val pluggedLimitPaused = AndroidGateProbe.parseBatteryState(
      level = 80,
      scale = 100,
      plugged = BatteryManager.BATTERY_PLUGGED_WIRELESS,
      status = BatteryManager.BATTERY_STATUS_NOT_CHARGING,
    )
    assertEquals(80, pluggedLimitPaused.percent)
    assertTrue(pluggedLimitPaused.charging)

    // 100% 拔线瞬间若系统仍残留 BATTERY_STATUS_FULL 但 plugged == 0，绝不能误判为充电中。
    val unpluggedResidualFull = AndroidGateProbe.parseBatteryState(
      level = 100,
      scale = 100,
      plugged = 0,
      status = BatteryManager.BATTERY_STATUS_FULL,
    )
    assertEquals(100, unpluggedResidualFull.percent)
    assertFalse(unpluggedResidualFull.charging)

    val discharging = AndroidGateProbe.parseBatteryState(
      level = 42,
      scale = 100,
      plugged = 0,
      status = BatteryManager.BATTERY_STATUS_DISCHARGING,
    )
    assertEquals(42, discharging.percent)
    assertFalse(discharging.charging)

    val statusChargingOnly = AndroidGateProbe.parseBatteryState(
      level = 65,
      scale = 100,
      plugged = 0,
      status = BatteryManager.BATTERY_STATUS_CHARGING,
    )
    assertTrue(statusChargingOnly.charging)

    val invalidBattery = AndroidGateProbe.parseBatteryState(
      level = -1,
      scale = 0,
      plugged = 0,
      status = BatteryManager.BATTERY_STATUS_UNKNOWN,
    )
    assertNull(invalidBattery.percent)
    assertFalse(invalidBattery.charging)
  }
}
