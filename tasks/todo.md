# 当前任务与验证

本页只维护未完成事项与本次真实验证；实现和主机测试不替代实机验收。

## 未完成事项

- [ ] 完成新版 Miuix UI（双 Tab 底栏导航、图1三态卡片与系统信息首页、图2内联卡片设置页、图3渐变关于页）的真机/模拟器交互与旋转/明暗主题验收；当前 `:shared:testAndroidHostTest` 为 `NO-SOURCE`，主机编译与单测不替代真机页面验收。
- [ ] 复现并排查实际采集中按 Home 后的相机释放延迟：本轮独立模拟器一次未在 3 秒检查阈值内释放，随后无残留；返回与撤销权限分别约 2.05 秒、0.45 秒释放。不能将无前置相机实例的无客户端结果作为生命周期成功证据。
- [ ] 完成当前 APK 的实机/UI 矩阵：长超时降亮前检测、连续注视、Unknown/多脸、真实触摸、相机抢占、口袋/朝下、后端切换/掉线与服务被杀。重点验证无障碍 Hold 上限释放后实际熄一次；分别记录 Android、ROM、系统超时、后端/框架版本和实际钩点。
- [ ] 完成 Shizuku 原系统超时恢复的实机验收：临时延长、屏灭、进程被杀、Binder 重连及 Shizuku 重启。同步持久化失败、恢复读回失败和重试已由隔离测试覆盖，真实恢复仍未验证。设备/框架重启不在本次授权内。
- [ ] 完成正式签名与可分发 APK 验收：提供正式签名配置后验证签名、安装升级与压缩包运行，补齐对应版本发布说明及第三方条款复核。MIT 与当前运行依赖声明已落地；未配置正式签名的 Release 为 unsigned，本次未发布。
- [ ] 完成 LSPosed 实验性“隐藏相机隐私指示器”真机重启与多 ROM 验收：在已启用 LSPosed `system_server` 作用域的真机上验证开启/关闭实验开关、自检页与后台采样的状态栏绿点隐藏与恢复，以及系统相机等其他应用绿点不受影响；主机单元测试不替代真机框架注入验收。

## 当前 APK 验证（2026-10-12）

- LSPosed 实验性“隐藏系统相机隐私指示器”需求对齐、文档/ADR 维护与代码实现：
  1. 更新领域术语 [CONTEXT.md](../CONTEXT.md)、架构分析 [camera-privacy-indicator.md](../docs/architecture/camera-privacy-indicator.md)、后端文档 [lsposed.md](../docs/architecture/backends/lsposed.md)、隐私规范 [privacy-security.md](../docs/privacy-security.md)、UI 架构 [ui.md](../docs/architecture/ui.md)，新增架构决策记录 [ADR 0001](../docs/adr/0001-lsposed-hide-camera-privacy-indicator.md) 并登记至 [docs/index.md](../docs/index.md)。
  2. 在 `xposed` 模块新增独立 `CameraIndicatorResolver`，解析 `AppOpsService` 的 `scheduleOpActiveChangedIfNeededLocked`、`notifyOpActiveChanged` 与 `isOperationActive` 候选，通过独立 `HookCallbackGuard` 与电源超时主链路隔离；仅在开关启用且目标为 GazeKeep 自身 `OP_CAMERA (26)` 时拦截 `active = true` 通知并覆盖活跃查询，保持 `startOp` 返回 `MODE_ALLOWED` 不变。
  3. 扩展 `BridgeProtocol.TX_SET_HIDE_INDICATOR`、`LsposedKeepAwakeBackend.syncHideCameraIndicator`、`GazeConfig.hideCameraIndicator`、`ConfigStore` 及 `SettingsScreen` / `PrivacyScreen`，支持在 LSPosed 就绪且选中时免重启实时切换隐藏状态，切换至非 LSPosed 后端或关闭开关时自动恢复默认绿点。
  4. 主机测试 `:core:allTests`、`:xposed:testDebugUnitTest`、`:engine:testDebugUnitTest`、`:android:testDebugUnitTest`、`:android:assembleDebug`、`:android:lintDebug` 通过；`:shared:testAndroidHostTest` 为 `NO-SOURCE`，真机 LSPosed 重启与状态栏绿点视觉验收待在授权设备上执行。
- 门禁：`pnpm verify:docs`、`pnpm verify:agent-assets`、`pnpm verify:architecture`、`pnpm test:tools`、`pnpm format:check` 全部通过，`git diff --check` 无违规。
