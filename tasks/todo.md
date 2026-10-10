# 当前任务与验证

本页只维护未完成事项与本次真实验证；实现和主机测试不替代实机验收。

## 未完成事项

- [ ] 修正首页相机自检摘要：当前仅授权相机、尚未通过 Looking→Away 时也显示“已就绪”；应根据 selfTestPassed 显示待自检。模拟器已复现，启用开关仍保持关闭。
- [ ] 复现并排查实际采集中按 Home 后的相机释放延迟：本轮独立模拟器一次未在 3 秒检查阈值内释放，随后无残留；返回与撤销权限分别约 2.05 秒、0.45 秒释放。不能将无前置相机实例的无客户端结果作为生命周期成功证据。

- [ ] 完成当前 APK 的实机/UI 矩阵：长超时降亮前检测、连续注视、Unknown/多脸、真实触摸、相机抢占、口袋/朝下、后端切换/掉线与服务被杀。重点验证无障碍 Hold 上限释放后实际熄一次；分别记录 Android、ROM、系统超时、后端/框架版本和实际钩点。
- [ ] 完成 Shizuku 原系统超时恢复的实机验收：临时延长、屏灭、进程被杀、Binder 重连及 Shizuku 重启。同步持久化失败、恢复读回失败和重试已由隔离测试覆盖，真实恢复仍未验证。设备/框架重启不在本次授权内。
- [ ] 完成正式签名与可分发 APK 验收：提供正式签名配置后验证签名、安装升级与压缩包运行，补齐对应版本发布说明及第三方条款复核。MIT 与当前运行依赖声明已落地；未配置正式签名的 Release 为 unsigned，本次未发布。

## 当前 APK 验证（2026-10-10）

- 本轮 LSPosed (Android 15 x86_64 模拟器) 跨进程加载与 Hook 验证：
  1. 根因解决：逆向并修复 LSPosed 1.9.3_mod 在 Android 15 x86_64 上的两大致命缺陷：① 修正 ART `JitCodeCache::DoCollection` 符号使 LSPlant 正常初始化；② 补全 `system` 与 `android` 双作用域；③ 规避 API 100/102 构造函数失配，通过资源排除 `java_init.list` 使 LSPosed 稳定调用 `LegacyEntry`。
  2. 真实 Hook 捕获：软重启 `system_server` 后，在真实的系统进程（PID 12274）中成功捕获 `system_context_resolved_via_reflection` 与 `hook_installed` 日志。
  3. Binder 握手与 UI 验收：启动 GazeKeep，App 通过跨进程 Binder `TX_PING` 握手成功，后端设置页识别为“LSPosed 已就绪”，自动选择第一优先级 LSPosed，引擎状态转为 `state=Yielded backend=Lsposed`。截图已存证。
- 门禁：Kotlin 主机测试通过，Node 工具测试 18 个全绿，format:check、verify:docs、verify:architecture 全部通过，`git diff --check` 无违规。
