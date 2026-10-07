# M2 Shizuku 后端验证

## 当前实现

- 新增 `:backend:shizuku`，使用 Shizuku API `13.1.5`。
- `ShellService` 运行在 Shizuku UserService 的 shell/root 身份，只通过 `ServiceManager` 取得 `power` binder，并按 Android 12+ / 旧签名反射调用 `IPowerManager.userActivity`。
- App 侧 `ShizukuKeepAwakeBackend` 已实现 binder 收发、UserService 绑定、协议版本检查、过期 seq 拒绝和 binder 掉线 readiness 更新。
- `PowerDumpParser` 已加入结构化电源状态解析及单测；不跨进程传递原始 dumpsys 文本。
- `IGazeKeepShell.readPower()` 已加入：原始 `dumpsys power` 只在 shell 侧解析为 `PowerSnapshot`，App 侧只接收结构化字段。
- `PowerDumpOracle` 已加入轮询与 deadline 推送逻辑，并由 `GazeService` 在 Auto 选择 Shizuku 时启用。
- `BackendSelector` 已加入 Auto / 锁定模式测试；锁定后端不可用时不会静默回退。
- 主界面已收敛为 `Auto / Shizuku / Accessibility` 后端选择；选择值写入 `gazekeep_config.backend_lock`，并立即停止旧引擎、启动新选择对应的引擎。选择无障碍时会显示服务开关状态，未启用时可直接跳转系统无障碍设置。

## 小米 12 Pro 环境

| 项目 | 结果 | 备注 |
|---|---|---|
| 无线 ADB | 已开启 | `192.168.12.46:5555` |
| Shizuku 包 | 已安装 | `moe.shizuku.privileged.api` |
| Shizuku server 进程 | 已发现 | 设备进程表显示 `shizuku_server` |
| UserService `userActivity` | 已通过 | 授权后 UI 显示“Shizuku 已就绪”；点击测试按钮后 shell 进程保持存活，`mLastUserActivityTime` 更新 |
| `DEVICE_POWER` 权限 | 已通过 | 小米 12 Pro / HyperOS V816 上一次调用未抛出异常，未触发超时退路 |
| UserService `readPower` | 已通过调用链验证 | 测试按钮先读取结构化电源状态，再调用 `userActivity`；shell 进程保持存活且无异常 |

Auto 模式现在按 LSPosed → Shizuku → 无障碍选择已就绪后端；后端切换先进入无后端状态，避免切换窗口继续保亮。M1 测试页可显式锁定 Shizuku 或无障碍，选择在下一次启动 M1 时生效。`pendingTimeoutRestore` 的落盘与写回框架已加入。

最终 APK 重装后已在设备上重新拉起 Shizuku UserService，shell 进程存活且无应用崩溃；当前总开关关闭，因此未启动完整引擎轮询。后端锁定 UI 与配置解析已通过 APK 编译、主机测试和 lint。

2026-10-05 Auto 验证：临时设置 `enabled=true`、`m1_test_mode=false` 后通过无障碍服务拉起引擎，关闭主界面后仍出现独立的 `cn.rainafter.app.gazekeep:shell` 进程，确认 Auto 选择了 Shizuku。系统熄屏超时设为 15 秒；未确认到 Looking 时屏幕进入 Dozing，符合 Unknown/Away 和默认熄屏约束。验证结束后已恢复 `enabled=false`，无障碍服务保持启用。

2026-10-05 回归发现当前安装包的 Shizuku API 权限为未授予（`moe.shizuku.manager.permission.API_V23: granted=false`），此前版本的授权记录不能作为当前包状态。现已把未授权状态直接映射为“授权 GazeKeep”按钮并调用 `Shizuku.requestPermission()`；需在设备上点击该按钮并接受弹窗后，才能重新验证 UserService 和 `userActivity`。

2026-10-05 M2 完整链路验证通过：小米 12 Pro / HyperOS V816 / Android 14 上锁定 `backend_lock=Shizuku` 后，日志依次出现 `deadline`、`state=Armed backend=Shizuku`、`gates ... decision=Proceed`、两帧 `Looking`、`extend result=Done`，随后进入下一轮 `Armed`。验证同时发现并修复 HyperOS 的 `mLastUserActivityTime(excludingAttention)=...` 字段格式。结束后已恢复 `enabled=false`、`backend_lock=Auto`，系统熄屏超时仍为 15 秒。

2026-10-05 Shizuku 重复 deadline 修复回归：在无障碍服务关闭、Shizuku 锁定条件下，修复 `PowerDumpOracle` 对相同 deadline 的重复发射后，日志稳定出现单次 `Gating → Capturing → Looking → Extending → extend result=Done`，未再发生重复采集取消。结束后已恢复 `enabled=false`、`backend_lock=Auto`，并重新启用无障碍服务。

2026-10-05 后端选择页回归：移除 M0/M1 调试卡片后，安装包主界面仅显示后端选择；锁定无障碍且服务未启用时显示“打开无障碍设置”，点击可进入系统无障碍设置页；锁定 Shizuku 时显示“Shizuku 已就绪”，选择后服务记录进入前台运行。验证结束后已恢复 `enabled=false`、`backend_lock=Auto`，系统熄屏超时恢复为 15 秒。

2026-10-05 Shizuku 切换竞态回归：修复后端 readiness 到达时 oracle 先于 reducer 处理 `BackendSelected` 发出 deadline 的竞态；新 APK 在 12 Pro 上锁定 Shizuku 后日志进入 `state=Armed backend=Shizuku`。验证结束后已恢复 `enabled=false`、`backend_lock=Auto`，无障碍服务和 15 秒熄屏超时均已恢复。

2026-10-05 M2 验收复测：关闭 GazeKeep 无障碍服务、保留 Shizuku 授权，锁定 Shizuku 后新 APK 再次进入 `state=Armed backend=Shizuku`；验证结束后恢复 `enabled=false`、`backend_lock=Auto`、15 秒熄屏超时和无障碍服务。

## 2026-10-07 降亮前检测接入与实机回归

- PowerDumpOracle 使用已解析的 `dimDurationMs` 构建降亮时刻，轮询确认点与 reducer 使用相同的 `降亮时刻 − lead` 计算，保留真实熄屏时刻作为迟到与采集截止依据。
- PowerDumpParser 优先使用 `Screen off timeout:` 有效计算值，缺失才回退 `mScreenOffTimeoutSetting`；避免运行时超时覆盖存在时与降亮时长配错。
- 降亮时长缺失、负数或超过有效超时则回退原调度；相同 deadline 仍去重，防止重复事件取消采集。
- 自动验证：core 23 项、Shizuku 6 项、xposed 4 项通过，assembleDebug / lintDebug 通过。覆盖实际有效超时优先、降亮时刻跨 flow 传递、降亮未知回退与相同 deadline 去重。
- 检查时 Shizuku 授权仍在，但 server 未运行；新版完整保亮与默认熄屏待服务恢复后实机验证。

### 同日服务恢复后的实机回归

- 用户启动 Shizuku 并启用无障碍后，临时锁定 Shizuku；运行前确认 UserService 进程与 GazeService 前台服务均已启动，不把初始化等待计入连续保亮测试。
- 系统超时 60000 ms、降亮阶段 17999 ms，正视采样中 PowerGroup 的 `lastUserActivityTime` 为 883941 → 918539 → 953193 ms，两次间隔为 34598、34654 ms；约每 2 秒采样的 summary 均为 0x1。用户确认没有先变暗再恢复。
- 转头观察初期还有一次活动更新至 987807 ms，随后不再增长；约 42 秒时进入 dim（0x2），约 60 秒时进入 Dozing（0x4），`mLastSleepReason=timeout`。默认熄屏通过。
- 本轮不更改系统超时。转头观察初期额外的一次活动更新未单独归因，不将其来源写成已验证事实；最终活动停止后按原周期降亮与熄屏。

- 同轮无障碍复测发现重复前台启动超时，最新 APK 已加入每次 onStartCommand 立即注册前台的共用修复；后端计算与此前 Shizuku 已通过链路一致。结束恢复 enabled=false、backend_lock=Auto、m1_test_mode=false，系统超时仍为 60000 ms。
