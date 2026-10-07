# 引擎与决策

本页描述当前接口和转移，不用目标设计替代源码现状。策略目标仍受[安全承诺](../../AGENTS.md)约束。

## 当前接口

```kotlin
fun EngineReducer.reduce(snapshot: EngineSnapshot, event: EngineEvent, now: Long): Transition
// Transition 包含新 snapshot 与 effects，不直接执行硬件操作。
```

snapshot 包含 state、context、config、backend、cameraPermissionGranted。context 保存 holdStart、lastExternalActivity、lookingStreak、darkUnknownStreak、forcedYieldPending、extendFailures、foregroundPackage。

`KeepAwakeBackend` 提供 id、readiness，以及 suspend attach/detach/extend(token)/release。token 为 None 或 Seq(Long)，结果为 Done、Stale、Failed(Throwable)。`TimeoutOracle` 提供 source、deadlines Flow 和 millisUntilScreenOff()。

`TimeoutDeadline` 保存 screenOffAtUptime、token、foregroundPackage、lastExternalActivityUptime、backend、screenDimAtUptime，时间统一 uptime。有效降亮时刻满足 0 < dim <= off；检测在 dim−lead 调度，未知时用 off−lead。不会为了检测修改系统降亮或熄屏设置。

## 当前转移摘要

| 事件 / 前提                         | 状态与副作用                                               |
| ----------------------------------- | ---------------------------------------------------------- |
| 配置关闭、后端不可用或权限丢失      | Disabled；取消采集/唤醒，Release，按原因通知               |
| ScreenOff                           | ScreenOff；取消采集/唤醒，Release，清保亮起点              |
| ScreenOn / 解锁，从 ScreenOff       | 可启用则等待 deadline，否则 Disabled                       |
| DeadlineUpdated，Disabled/ScreenOff | 忽略                                                       |
| deadline 的 wakeAt > now            | Armed；ScheduleWake                                        |
| 进入窗口，熄屏剩余 >2500 ms         | Gating；RunGates                                           |
| 太晚                                | Yielded(TooLate)；Record                                   |
| WakeTimerFired，Armed               | Gating；RunGates                                           |
| UserActivity，活跃态                | 取消旧采集/唤醒，清保亮/注视累计，等待新 deadline          |
| Gate Skip                           | Yielded(Gate)；Record                                      |
| Gate Proceed，上限 pending          | Yielded(MaxHold)；清 pending，Record                       |
| Gate Proceed                        | Capturing；StartCapture                                    |
| CaptureFinished Looking，Capturing  | Extending；Extend(token)                                   |
| CaptureFinished Away/Unknown        | Yielded(NotLooking)；Release、Record                       |
| CaptureAborted，Capturing           | Yielded(Aborted)；Release、Record，按原因通知              |
| Extend Done，Extending              | 等待新 deadline；累计保亮/注视，达到上限置 pending，Record |
| Extend Stale / Failed               | 不标记保亮成功，Record；累计失败 >=2 时通知                |

副作用包括 ScheduleWake、CancelWake、RunGates、StartCapture、CancelCapture、Extend、Release、Record、Notify。GazeEngine 在串行事件循环更新 snapshot，DefaultEngineEffectRunner 按窄接口执行并回灌事件。当前采集截止为 min(off−200 ms, now+1600 ms)。取消异常继续传播，不把旧轮次取消伪装为采集 Error。

## 门控、选择与保护

纯 GatePolicy 在 core，AndroidGateProbe 只采集输入。默认白名单空列表，因此首次选择应用是启用流程的一部分。真实前台来源当前仍依赖无障碍；接近、光照、朝下输入尚为 null。不能声称已完成所有口袋或相机占用门控。

Auto 在就绪后端中按 LSPosed→Shizuku→Accessibility 选择；锁定只选指定后端，不自动换到其他后端。切换先停止旧会话并释放资源，切换期不 Extend。Extend 连续失败通知本身不等于完成降级，降级取决于 readiness 与会话选择链路。

HoldPolicy 计算上限，LeadBackoff 提供退避设置。当前 Gate Skip/MaxHold 分支未发 Release，尚不能证明无障碍持有锁必释放；退避缩 lead/帧数未完整接入。所有 Unknown 增加 darkUnknownStreak，并未单独归因暗光。以上是[维护缺口](../maintenance/overview.md)，不是新设计许可。

必须验证的七条不变量见[测试规范](../testing.md)。目标上限“必须熄一次”不能仅由不再采帧/Extend 的单测代替。
