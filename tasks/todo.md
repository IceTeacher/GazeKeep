# 当前任务与验证

本页只维护未完成事项与本次真实验证；实现和主机测试不替代实机验收。

## 未完成事项

- [ ] 完成新版 Miuix UI（双 Tab 底栏导航、图1三态卡片与系统信息首页、图2内联卡片设置页、图3渐变关于页）的真机/模拟器交互与旋转/明暗主题验收；当前 `:shared:testAndroidHostTest` 为 `NO-SOURCE`，主机编译与单测不替代真机页面验收。
- [ ] 复现并排查实际采集中按 Home 后的相机释放延迟：本轮独立模拟器一次未在 3 秒检查阈值内释放，随后无残留；返回与撤销权限分别约 2.05 秒、0.45 秒释放。不能将无前置相机实例的无客户端结果作为生命周期成功证据。
- [ ] 完成当前 APK 的实机/UI 矩阵：长超时降亮前检测、连续注视、Unknown/多脸、真实触摸、相机抢占、口袋/朝下、后端切换/掉线与服务被杀。重点验证无障碍 Hold 上限释放后实际熄一次；分别记录 Android、ROM、系统超时、后端/框架版本和实际钩点。
- [ ] 完成 Shizuku 原系统超时恢复的实机验收：临时延长、屏灭、进程被杀、Binder 重连及 Shizuku 重启。同步持久化失败、恢复读回失败和重试已由隔离测试覆盖，真实恢复仍未验证。设备/框架重启不在本次授权内。
- [ ] 完成正式签名与可分发 APK 验收：提供正式签名配置后验证签名、安装升级与压缩包运行，补齐对应版本发布说明及第三方条款复核。MIT 与当前运行依赖声明已落地；未配置正式签名的 Release 为 unsigned，本次未发布。

## 当前 APK 验证（2026-10-10）

- 检测提前量与单次最长保亮自定义及无限制选项验证：
  1. 设置页「检测提前量」支持 `5 秒 / 8 秒 / 自定义（0～60 秒整数）`，「单次最长保亮」支持 `10 分钟 / 30 分钟 / 60 分钟 / 自定义（1～1440 分钟整数） / 无限制`；点击「自定义」弹出含「取消」「确定」按钮的 `OverlayDialog` 数字输入框，点击「取消」仅关闭弹窗、不改动配置。
  2. 先在 `ScreenDimmingPolicyTest`、`EngineReducerTest`、`ConfigStoreTest`、`PreTimeoutScheduleTest` 中新增 0 秒提前量最小窗口兜底、退避不放大、无限制保亮不触发上限及自定义范围持久化/越界拒绝单测，确认旧实现失败后再完成实现并全量通过。
  3. `:android:assembleDebug`、`:android:lintDebug` 通过；`:shared:testAndroidHostTest` 为 `NO-SOURCE`，本轮未在设备上执行页面交互验收。
- 门禁：`pnpm verify:docs`、`pnpm verify:agent-assets`、`pnpm verify:architecture`、`pnpm test:tools`、`pnpm format:check` 全部通过，`git diff --check` 无违规。
