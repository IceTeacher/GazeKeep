package com.android.server.power

// 仅提供签名，不调用任何 Android 实现。
class AttentionDetector {
  fun updateUserActivity(nextDimming: Long, duration: Long): Long = nextDimming + duration
}

class PowerManagerService {
  fun updateUserActivitySummaryLocked(now: Long, groupId: Int) = Unit
  fun scheduleUserInactivityTimeout(timeout: Long) = Unit
}
