# 引擎与决策

本页维护当前决策接口；硬件熄屏结果以[测试规范](../development/testing.md)与[待办](../plans/todo.md)中的当前版本证据为准。

## 接口与时序

EngineReducer 是状态转移唯一入口，显式接收 uptime，返回 snapshot 与 effects。运行端串行处理事件并执行副作用；采集与 Extend 结果带本轮 token，门控结果带 deadline，迟到结果不影响新轮次。

TimeoutDeadline 保留真实 off 与有效 dim（0 < dim <= off），优先在 dim−有效 lead 调度，未知 dim 使用 off−lead，且唤醒时刻不晚于 off−2500 ms。采集截止为 min(off−200 ms, now+1600 ms)，不会为了检测修改系统降亮设置。

默认三帧两票；连续两轮 Looking 后 lead 减少 3000 ms（下限 4000 ms 且不超过用户设定值），最多两帧且仍要求两票 Looking。开启单次保亮上限（maxHoldMinutes > 0）时，首次成功 Extend 的起点用于计算上限，独立定时携带起点，旧定时不能结束新会话；设为无限制（0）时不调度或触发上限。

## 默认熄屏与恢复

Gate Skip、TooLate、Unknown/Away、中止、Extend 失败、关闭、权限丢失和切换释放后端。上限到点取消采集、唤醒与 Extend，Release 并锁定 MaxHold；后续 deadline 不再检测，直到 ScreenOff 或真实用户活动重置。硬件是否实际熄灭必须额外验收。

ScreenOff 清累计状态，直到 ScreenOn 不采帧。真实触摸先取消旧采集、释放并清保亮累计，再等待新 deadline。独立 oracle 的新外部活动时间也走同一 reducer 路径；Shizuku 自身 userActivity 不算外部活动。

DefaultEngineEffectRunner 的门控、采集与 Extend 归会话所有，取消异常继续传播；采集、门控与 Extend 返回后重新检查取消，旧任务即使忽略取消并返回成功，也不能回灌事件或更新新会话 oracle。Extend 和 Release 使用互斥锁，结果先回灌再触发 oracle 读取，避免新 deadline 抢先丢失保亮累计。会话销毁另有硬件清理。

无障碍 oracle 分开记录系统超时起点与真实用户活动；自身 Extend 只推进前者，不能清除保亮累计和退避。运行定时使用带唤醒时刻的 ScheduledWakeFired，错误结果也携带当前轮次身份。

## 门控、来源与日志

GatePolicy 保持纯 Kotlin；AndroidGateProbe 拥有传感器和相机可用性订阅。前台来源按后端选择：无障碍事件、Shizuku shell 查询、LSPosed Handler 查询。未知前台不通过白名单，不借用旧后端的缓存。传感器及相机边界见[采集架构](capture-classification.md)。

暗光累计只计最终 Unknown 且至少两帧 TooDark；其他 Unknown 不增加暗光失败。连续两次暗光且 lux <3 时休眠，光照变亮后可以重新检测。

Auto 按 LSPosed→Shizuku→Accessibility 选择就绪后端；锁定只选指定后端。切换先取消旧会话并释放，切换期不 Extend。DecisionRecord 包含允许字段、门控/中止/分类原因与检测耗时，持久日志见[运行架构](android-runtime.md)。
