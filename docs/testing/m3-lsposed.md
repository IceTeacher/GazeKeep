# M3 LSPosed 后端验证

## 已完成的静态检查

- `:xposed` 使用 `de.robv.android.xposed:api:82` 作为 `compileOnly`，旧式入口实现为 `LegacyEntry : IXposedHookLoadPackage`。
- APK 包含 `assets/xposed_init`、`META-INF/xposed/scope.list` 和 `module.prop`；现代 `java_init.list` 暂不声明，避免把未确认的 libxposed API 类当作入口；Xposed API 类未打入应用运行时依赖。
- 12 Pro / HyperOS V816 的 `services.jar` 已确认候选 A/B/C 方法签名；解析器只接受精确签名，失败时不安装钩子。
- `IPowerManager.Stub.onTransact` 的自定义 transaction 由 system_server 入口处理，未知 transaction 放行原逻辑；回调和 binder 处理均整体捕获异常。
- APK 权限检查未发现 `INTERNET`、存储、`QUERY_ALL_PACKAGES`、`SYSTEM_ALERT_WINDOW` 或 `READ_PHONE_STATE`。

## 小米 12 Pro 手工矩阵

| 项目 | 结果 |
|---|---|
| LSPosed 识别同一 APK | 通过（2026-10-05） |
| 仅勾选系统框架作用域后重启 | 通过（2026-10-05） |
| `TX_PING` 返回协议版本和 Hook 就绪 | 通过（2026-10-05） |
| Looking → Extend 单次调用 | 2026-10-05 返回 `TX_EXTEND=0`；仅证明单次调用，不能证明稳定重置超时 |
| 无触摸连续两个超时周期保亮 | 2026-10-07 修复后通过：活动时间连续增长约 52.7 秒，60 秒设置保持 Awake |
| 退出设置页后桌面继续保亮 | 2026-10-07 通过：页面退出后服务保持前台，下一周期活动时间增长 |
| 故意改错候选方法名后仍能开机且显示未适配 | 待验证 |
| 禁用作用域后按原超时熄屏 | 待验证 |

就绪状态只表示桥接握手成功；连续保亮和默认熄屏分别验收，不得把单次调用返回成功写成全部用例通过。

## 2026-10-05 设备回归

- 小米 12 Pro 重启并启用系统框架作用域后，主界面显示“LSPosed 系统框架作用域已就绪”。该状态来自 `TX_PING` 的协议握手，确认 `system_server` 已安装桥接钩子。
- 在 15 秒系统超时下，设备收到 A 候选 deadline，摄像头分类得到 `Looking`，随后 `TX_EXTEND` 返回 0（`ExtendResult.Done`）；屏幕保持唤醒。
- 设备侧观察到 A 候选会被高频调用，system_server 现按相同绝对熄屏时刻去重，避免采集期间 seq 失效。

## 2026-10-07 回调所有权修复

- 用户反馈相机能触发，但未重置屏幕超时。代码检查发现设置页和 GazeService 都会执行 TX_REGISTER，设置页刷新会抢占系统的单客户端回调。
- 设置页改为 checkReadiness，仅执行 TX_PING；不再在页面关闭时调用 TX_UNREGISTER。客户端注册和注销由 GazeService 独占。
- assembleDebug、lintDebug、core:allTests 通过，修复 APK 已安装。当前系统超时为 60 秒，保留用户原设置。
- 正视且无触摸时，系统 mLastUserActivityTime(excludingAttention) 为 77676770 → 77729482 → 77782115，连续两个周期约 52.7 秒，屏幕保持 Awake。
- Back 退出设置页后，桌面上的活动时间为 77810736 → 77863424，证明退出页面没有注销 GazeService 回调。
- 本轮 ROM 实际属性 ro.build.version.incremental 为 OS2.0.6.0.ULGCNXM；旧文档 V816 记录不作为本轮版本号。
- 转头后设备进入 Dozing，dumpsys power 记录 mLastSleepReason=timeout，默认熄屏通过。随后系统日志记录 WAKE_REASON_WAKE_KEY / android.policy:WAKEUP_KEY，后续唤醒不是 GazeKeep userActivity 的执行证据。

- 用户确认后续亮屏由手动唤醒引起。本轮结束恢复 enabled=false、backend_lock=Auto、m1_test_mode=false，系统超时保持 60000 ms。

## 2026-10-07 降亮前检测修复与实机回归

- 用户报告 LSPosed 在长超时下先降亮，再在熄屏前重置超时。候选 A 原先丢弃 `nextScreenDimming`，系统侧与 reducer 均按 `screenOffAt − lead` 调度。
- 现在候选 A 保留并传递降亮时刻，两个调度点均改为 `screenDimAt − lead`；真实熄屏时刻仍用于采集截止与迟到判定。未知降亮时刻的候选 C 保留原调度方式。
- 桥接协议升为 v2，旧 system_server 与新 App 的握手会要求重启，避免使用不匹配的 AIDL 回调。
- 自动验证通过：`:core:allTests`（18 项）、`:xposed:testDebugUnitTest`（4 项）、`:androidApp:assembleDebug`、`:androidApp:lintDebug`。新增测试覆盖长超时降亮前通知、App 不再次等待熄屏窗口、Unknown 不 Extend、降亮时间未知或无效的回退、迟到窗口。
- 修复 APK 已于 2026-10-07 通过 `adb install -r` 安装成功，并执行 `adb reboot`。安装前确认 ROM 为 OS2.0.6.0.ULGCNXM、系统超时为 60000 ms，未改动该设置。重启后恢复无线 ADB，直接执行只读 `TX_PING` 验证 `PROTOCOL_VERSION=2`、`HOOK_STATUS=1`。临时检测程序已从手机删除。

| 实机回归用例 | 结果 |
|---|---|
| 安装新版并重启，桥接 v2 就绪 | 通过：v2、HOOK_STATUS=1 |
| 60 秒及更长超时，正视且不触摸，连续两个周期不先降亮 | 60 秒通过：两个连续周期约 34.6 秒更新活动时间，采样保持亮屏，用户确认没有再变暗；更长超时待验证 |
| 转头 / 没看清，仍按原超时降亮并熄屏 | 转头通过：先进入 dim（summary=0x2），随后 Dozing，mLastSleepReason=timeout，用户确认已自行熄灭；没看清场景仍待验证 |
| 检测期间触摸，取消采集并交还系统计时 | 待验证 |
| 退出设置页，桌面连续周期仍正常 | 待验证 |

本轮实机证据与收尾：

- 系统设置保持 60000 ms，亮屏时 power 报告降亮阶段为 17999 ms，原降亮点约为活动后 42 秒。新版提前量 8 秒，检测应约在活动后 34 秒。
- 正视阶段活动时间为 229229 → 263868 → 298503 ms，两次间隔分别为 34639、34635 ms；约每 2 秒采样的 `userActivitySummary` 保持 0x1。用户反馈“没有再变暗”。
- 转头阶段先采到 0x2，随后查询确认 `mWakefulness=Dozing`、`mLastSleepReason=timeout`。熄屏后对应 PowerGroup 的 `lastUserActivityTime` 仍为 298503 ms，查询时已空闲约 69.7 秒。
- 转头观察过程中一次无线 ADB 查询中断，重连后读取到上述息屏状态；未据此猜测相机分类结果，未采集或保存图像。
- 已停止测试应用并恢复安装前配置：`enabled=false`、`backend_lock=Auto`、`m1_test_mode=false`；没有运行中的 GazeService。再次读取系统 `screen_off_timeout` 为 60000 ms。
