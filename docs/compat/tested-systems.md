# 已测试系统

| 机型 | ROM / Android | GazeKeep 后端 | Shizuku / LSPosed | 结果 |
|---|---|---|---|---|
| 小米 12 Pro（2207122MC） | HyperOS V816 / Android 14（API 34） | 无障碍、Shizuku、LSPosed | Shizuku 13.6.0（r1086）；LSPosed 作用域已启用 | Shizuku 权限、UserService、`readPower`、HyperOS 电源字段解析、deadline 去重、后端切换和 `Armed` 状态通过；无障碍与 Shizuku 已完成 `Looking → Extend`，LSPosed 于 2026-10-05 完成 `TX_PING` 及单次 `TX_EXTEND=0`；2026-10-07 修复设置页抢占回调后，60 秒超时下无触摸连续两个周期、退出设置页后保亮及转头后 timeout 熄屏通过；本轮 ROM 为 OS2.0.6.0.ULGCNXM。无障碍设置入口和服务状态检测通过。 |

## 钩点记录

- LSPosed：APK 已包含 `assets/xposed_init`、`META-INF/xposed/*` 和 `LegacyEntry : IXposedHookLoadPackage`；静态确认候选 A `AttentionDetector.updateUserActivity(long,long):long`、B `PowerManagerService.updateUserActivitySummaryLocked(long,int):void`、C `scheduleUserInactivityTimeout(long):void` 均存在。实测命中 A；同一绝对熄屏时间去重后完成 system_server deadline、相机 Looking 与 `TX_EXTEND=0`。
- Shizuku：不依赖系统钩点；通过 UserService 访问 `power` binder，并按 HyperOS `mLastUserActivityTime(excludingAttention)` 格式解析。
- 无障碍：`cn.rainafter.app.gazekeep/.backend.a11y.GazeA11yService`，保持 `canRetrieveWindowContent=false`。

## 当前设备收尾状态

- `enabled=false`、`backend_lock=Auto`。
- 系统熄屏超时保留用户设置：60 秒（2026-10-07）。
- 相机权限保留，未启动 GazeKeep 前台服务。

## 2026-10-07 降亮前检测修复回归

- 小米 12 Pro / OS2.0.6.0.ULGCNXM：修复 APK 安装成功，重启指令已执行。
- 重启后 `TX_PING` 返回协议 v2、HOOK_STATUS=1。解析安装计划优先候选 A，本次未另读取运行时候选名称；A 的签名命中记录见上方既有验证。
- 60 秒超时、17999 ms 降亮阶段下，正视连续两个周期的活动时间间隔为 34639 / 34635 ms，早于约 42 秒的降亮点；用户确认未再变暗。转头后进入 Dozing，熄屏原因为 timeout，用户确认自行熄灭。更长超时、没看清及采集中触摸待补充。
- 系统超时前后均为 60000 ms。临时启用 LSPosed 验证后，已恢复 `enabled=false`、`backend_lock=Auto`、`m1_test_mode=false` 并停止服务。

- 本轮未读取到 LSPosed 管理器版本号，仍需补录；不推断框架版本。

## 2026-10-07 Shizuku / 无障碍降亮前检测接入

- 两种后端已接入共同调度逻辑并通过主机测试、构建和 lint；新版 APK 已构建安装，无需再次重启 LSPosed。
- 12 Pro / OS2.0.6.0.ULGCNXM 只读查询 framework 降亮资源为 18000 ms / 29.999996%，无障碍使用该配置估算；Shizuku 使用 power 的实际降亮时长。
- 新路径的连续保亮与默认熄屏尚未实测，不能沿用此前后端测试结果作为本次通过证据。

## 同日两后端实机回归完成

- 小米 12 Pro / OS2.0.6.0.ULGCNXM / Android 14，系统超时 60000 ms，亮屏时降亮时长 17999 ms。
- Shizuku：连续两个活动更新周期为 34598 / 34654 ms，均早于降亮点；用户确认没有再变暗。停止正视后不再续期，最终 timeout 熄屏。
- 无障碍：初始化测试暴露触摸事件顺序风险及重复前台启动超时；已修复并重装。当前进程连续约 35 秒检测一轮，123.4 秒采样均保持 Awake；显示策略 BRIGHT / ON，用户确认亮度正常。转头后 NotLooking、亮屏锁释放，最终 Dozing / timeout，引擎 ScreenOff。
- 最新自动验证：core 24 项通过；Shizuku 6 项和 xposed 4 项此前已通过，本次后续只改服务生命周期与回调顺序；最新 assembleDebug / lintDebug 通过。
- 原配置已恢复为 enabled=false、backend_lock=Auto、m1_test_mode=false，GazeService 已停止。无障碍仍启用，Shizuku server 保留，系统超时未改动。
- 更长超时、没看清、多脸、单次保亮上限及采集中触摸仍需对应手工矩阵补充，不以本轮两种场景替代完整验收。LSPosed 版本号仍未补录。
