# 当前任务与验证

本页维护尚未完成的工作和最近一次交付验证，不累积历史流水。产品承诺不因实现缺口而降低。

## 未完成事项

- [ ] 补齐当前版本的完整实机/UI 回归：长超时、Unknown/多脸、触摸、相机抢占、后端切换/掉线、服务被杀及上限熄屏；按 Android、ROM 与后端能力选择设备，记录框架版本和实际钩点。
- [ ] 修复 reducer 的 GateSkip/MaxHold 未发 Release，验证无障碍 Hold 到点释放并实际熄屏。
- [ ] 为 Shizuku pendingTimeoutRestore 提供有结果的同步持久化保证，验证写入失败、重启、Binder 重连和息屏恢复。
- [ ] 实现独立前台应用来源；当前白名单依赖无障碍，LSPosed topPackage 与 Shizuku 独立来源尚未落地。
- [ ] 补齐 proximity/lux/faceDown 和实际相机占用门控，不能将摄像头存在视为可用。
- [ ] 接入提前量/帧数退避，区分暗光 Unknown 与其他 Unknown。
- [ ] 完整决策日志、默认关闭与 24 小时滚动清理。
- [ ] 现代 libxposed 入口、hidden-api 存根及 Release R8/资源压缩验证。
- [ ] 发布前补齐许可证、第三方依赖说明、签名与可分发 APK 验收。
- [ ] Android Studio 重新同步后验证重复导入模型与 ListenableFuture 类路径问题；命令行构建成功不代表 IDE 已恢复。
- [ ] 单独处理现存 Kotlin/Gradle 与 XML 格式问题，保持本次目录调整不扩展为源码格式重写。

## 本次规则调整（2026-10-08）

- 完成：根 AGENTS.md 只保留通用协作规则与导航；Android 工程约束、十条安全承诺与验收要求下沉到 apps/app，各模块补充专属细节，统一继承链并修正安全承诺引用与 Skill 加载顺序。
- 测试先行例外：纯规则文档重组，无应用行为变更，不新增测试或重跑 Android 构建。
- 验证：十条安全承诺正文逐字一致，仅修正相对链接；九个模块继承链、规则归属与未完成事项保留核对通过；`pnpm verify:docs`、`pnpm verify:agent-assets`、受影响 Markdown 格式检查与 `git diff --check` 通过。
- 未执行：应用行为测试、Android 构建、实机/UI 验收、设备操作和发布；本轮未提交或推送。

## IDEA 与新目录状态复查（2026-10-08）

- 现状：Gradle 工程已迁至 `apps/app`，应用模块名为 `:android`，IDEA `.idea/gradle.xml` 指向新根目录；`.idea/workspace.xml` 仍保留仓库根与 `apps/app` 两份 ExternalProjectsManager 状态。
- 证据：idea-mcp 当前列出 126 个模块，包含 `GazeKeep.*`、`android.GazeKeep.*`、`app.GazeKeep.*` 三代前缀；引擎文件仍有 12 条 `ListenableFuture` 相关解析错误，而入口、UI 文件 ERROR 检查为空。重复模块与类路径错误吻合，但需清掉旧导入状态后验证因果。
- 验证：在 `apps/app` 运行 `./gradlew :engine:compileDebugKotlin --rerun-tasks`，9 个任务实际执行并成功；仅 `setTargetResolution` 弃用提示。Gradle 缓存报告旧 `../android/settings.gradle.kts` 已删除，重建后本次编译通过。
- 未完成：IDEA Gradle 模型仍含旧模块，编辑器错误未消失；IDEA 同步/模型清理后的验证、全文件诊断和设备验收未执行。源码未修改。
