# M1 无障碍后端验证

代码已加入 `:backend:a11y`，透明无障碍 overlay 同时提供保亮和触摸探针；服务声明 `canRetrieveWindowContent=false`，事件回调不读取 `event.source` 或文本。小米 12 Pro（HyperOS V816）实测脉冲释放不会重置完整熄屏周期，因此当前默认使用持有 `FLAG_KEEP_SCREEN_ON`，在下一轮非注视或息屏时释放；脉冲实现仍保留在后端代码中供其他 ROM 验证。

## 小米 12 Pro 手工矩阵

| 项目 | 结果 | 备注 |
|---|---|---|
| 服务可在系统无障碍列表中发现 | 已通过 | `dumpsys accessibility` 显示 GazeKeep 服务已启用 |
| 启用服务后 overlay 可附着 | 已通过 | WindowManager 出现 1×1、`TYPE_ACCESSIBILITY_OVERLAY` 窗口 |
| 脉冲后重新获得一个系统超时周期 | 未通过 | 2207122MC / HyperOS V816 / Android 14：300 ms 脉冲后仍约一个系统周期熄屏，改用持有模式 |
| 持有模式跨两个系统超时周期 | 已通过 | 03:32:09、03:33:02、03:33:54 连续正视三轮均 Extend，期间未熄屏 |
| 熄屏时服务仍存活且不访问相机 | 已通过 | `mWakefulness=Dozing` 时进程、服务和 overlay 仍在；相机 Active Clients 为空 |
| `ACTION_OUTSIDE` 能报告触摸 | 待测 | HyperOS / Android 14 行为需确认 |

2026-10-05 首轮 M1 引擎实测：日志出现 `Armed → Gating → Capturing → Release → Yielded`，Camera service 记录到 GazeKeep 前摄连接与断开；本轮未产生 `Extend`。

第二轮正视实测：日志出现 `Gating → Capturing → Extend → Extending → Yielded`，Camera service 记录 03:18:08 连接、03:18:09 断开；无崩溃，说明无障碍后端的脉冲调用已被引擎触发。脉冲是否让系统重新获得完整熄屏周期，以及脉冲后的下一轮 deadline，待安装包含 oracle 回灌修复的 APK 后再测。

最新 APK（含 `markExtended()` deadline 回灌）实测：03:26:51 `Gating → Capturing → Extend → Extending → Yielded`，随后 03:27:02 收到系统 `ScreenOff`；这确认脉冲模式在该 ROM 上不满足保亮承诺。切换持有模式后，03:32:09、03:33:02、03:33:54 连续正视三轮均重新安排 deadline，03:34:47 侧脸轮次 `Capturing → Yielded → Release`，03:35:49 系统进入 `Dozing`，持有模式验证通过。

当前 APK 已在 `2207122MC`（Android 14 / HyperOS `V816`）安装，用户已在系统设置中启用 `GazeA11yService`；未通过 ADB 代替用户启用服务。

2026-10-05 回归：发现首个 `IdleClockOracle` deadline 可能在 reducer 收到 `BackendSelected` 前发出，导致 M1 处于 `Yielded` 后不再进入门控；现已固定为先提交后端就绪事件再启动 oracle。无障碍服务同时消费 `TYPE_TOUCH_INTERACTION_START`，补足 HyperOS 上 overlay `ACTION_OUTSIDE` 的不稳定投递。

同日解锁状态复测（系统熄屏超时 15 秒）：`interactive=true`、`locked=false` 时门控为 `Proceed`，两帧均为 `Looking`，随后 `extend result=Done`，引擎回到 `Armed`；锁屏状态则按设计记录 `interactive=false`、`locked=true` 并 `Skip`，不访问相机也不保亮。

M1 收尾实现已加入 `alerts` 通知渠道：相机不可用、权限丢失、后端失败和无障碍服务解绑均按告警枚举去重，10 分钟内同类最多一条；无障碍重新连接会重新选择后端。通知展示和关闭无障碍后的系统设置交互仍需下一轮设备矩阵复核。

前台服务已加入 APK，但总开关仍由 `gazekeep_config/enabled` 控制，默认关闭；因此本轮验证只启动无障碍服务和 overlay，不会在用户未开启总开关时访问相机。

## 2026-10-07 降亮前检测接入与实机回归

- IdleClockOracle 读取 ROM 的 `config_maximumScreenDimDuration` 和 `config_maximumScreenDimRatio`，纯逻辑以 `min(maxDuration, timeout × ratio)` 估算降亮时刻，再交给 reducer 在降亮前启动检测。
- 小米 12 Pro / OS2.0.6.0.ULGCNXM 的只读资源查询返回 18000 ms、29.999996%；与 LSPosed 实测 60 秒超时下约 17999 ms 降亮阶段一致。资源匹配不代表无障碍完整链路已验收。
- 缺失、异常和非法资源回退原有熄屏前调度；不新增权限、不修改系统设置，不硬编码厂商比例。运行时覆盖值、触摸时钟与 ROM 自定义逻辑仍可能产生误差。
- 自动验证：core 23 项、Shizuku 6 项、xposed 4 项单测通过，assembleDebug 和 lintDebug 通过；涵盖长短超时、降亮上限、零降亮、非法资源回退及 Unknown 不 Extend。
- 检查时 GazeKeep 无障碍服务未启用，未代替用户开启；新版降亮前保亮、连续两周期及转头后熄屏均待实机验证。

### 同日实机验证准备

- Shizuku 回归后切换页面显示无障碍选中，但 GazeService 未运行；这段屏幕观察不计入无障碍验收。
- 测试过程中强制停止并重新启动应用，系统未恢复 GazeA11yService 的有效绑定；需用户在系统设置中关闭再开启服务后重试。未将系统开关“已启用”视为服务已绑定或引擎已运行的证据。

### 同日初始化与重复启动故障修复

- 触摸回调改为先提交 UserActivity 再发新 deadline，防止即时恢复的 SharedFlow 收集器先 Armed、随后被取消；core 新增事件顺序回归测试，共 24 项通过。
- 后续复测仍有退出，crash buffer 明确记录 ForegroundServiceDidNotStartInTimeException，重复启动调用来自 AndroidM2ShizukuController。GazeService 原先只在 onCreate 注册前台，现 onStartCommand 也立即调用同一注册方法；不依赖首次注册仍有效的假设。
- 加入只读 Service.dump，输出开关、后端、状态 / 原因枚举及 uptime 时间戳；不包含帧、概率或人脸数据。最新 APK 构建 / lint 通过并已安装。
- 修复后诊断为 enabled=true、backend=Accessibility、backendReadiness=Ready、state=Armed；服务持续 isForeground=true。待检测时刻比估算降亮时刻早 8000 ms，真实熄屏时刻仍保留。
- 正视连续 123.4 秒采样均为 Awake，相机连接 / 断开记录显示当前进程连续约 35 秒一轮检测；显示策略 BRIGHT、屏幕 ON。用户确认“亮度正常，没有再变暗”。持有模式下空闲 summary 可进入 0x2 / 0x4，不能把这个字段单独当作实际屏幕已变暗。
- 转头后只读诊断为 `Yielded / NotLooking`，随后亮屏锁摘要为 0；释放产生的 `lastUserActivityTimeNoChangeLights=2073529` 表明系统开始走释放后的计时。最终 `mWakefulness=Dozing`、`mLastSleepReason=timeout`，引擎为 `ScreenOff`，默认熄屏通过。
- 无障碍持有模式与 userActivity 后端的时间语义不同：转头后先等下一轮检测释放，释放后系统可能再走完整超时；不承诺转头开始时就立即熄屏。
- 本轮结束恢复原配置：enabled=false、backend_lock=Auto、m1_test_mode=false，GazeService 已停止；系统设置仍为 60000 ms，无障碍启用状态与 Shizuku server 保留。
