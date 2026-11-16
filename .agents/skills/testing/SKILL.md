---
name: testing
description: 编写、运行或评审 GazeKeep 测试时加载；统一覆盖单元纯规则、Compose/Chromium 界面与截图、UiAutomator 实机旅程及风险边界。
---

# 测试与验收规范

涉及纯规则、状态机（`EngineReducer`）、跨进程协议、构建/仓库脚本、UI 交互与截图基线或真实设备旅程时加载。具体命令、七条必守不变量与实机矩阵见 [开发、测试与发布](../../../docs/development.md)。

## 核心准则

- **风险驱动 Red-Green**：缺陷修复、纯规则、`EngineReducer`、解析、跨进程协议与稳定可自动复现的安全边界，**必须先写或调整最小失败测试并确认旧实现失败**，再修改实现。文档、纯格式、重命名与无行为配置变更不引入占位测试。
- **可控与隔离**：单元测试中时间、协程调度（`kotlinx-coroutines-test` 虚拟时间）、文件与系统服务必须可控；使用窄接口 fake 与临时目录隔离 fixture（`node:test` 不碰真实 Git 索引），不依赖真实摄像头、随机等待或用例执行顺序。
- **诚实断言**：测试公开行为与契约，不为凑覆盖率写无意义 getter 测试；失败时严禁删断言、放宽安全/截图阈值或无理由 skip。主机 fake、Robolectric、无头 Chromium 或构建成功均不能替代真实 ROM、硬件相机释放、无障碍 `Hold` 摘除或实际熄屏验收。

## 三层测试边界

1. **单元测试（Kotlin / Web Node / 仓库工具）**：
   - `core` 与 `shared` 纯规则放 `commonTest`，Android 模块隔离协作放 `src/test`（`backend:lsposed` 与 `android` 使用 Robolectric 时开放 `java.base/jdk.internal.access`）。
   - `apps/web` 纯逻辑与脚本测试放 `tests/src/` 与 `tests/scripts/`（运行于 Vitest `unit` project）。
   - `scripts/repo-tools` 测试放 `tests/`（使用 Node 原生 `node:test`）。
2. **界面与视觉测试（Compose Roborazzi / Web Chromium）**：
   - **App（`:shared:testAndroidHostTest`）**：使用 Robolectric 4.17 原生图形、Compose UI 与 Roborazzi（392×851dp，xhdpi）。截图基线位于 `apps/app/shared/src/androidHostTest/screenshots/`；有意改版时加 `-Pgazekeep.recordScreenshots=true` 重录并逐张核对。自检预览使用无帧占位块，应用列表使用虚构样本。
   - **Web（`pnpm --filter web test:browser:docker`）**：使用 Vitest `browser` project 在官方锁定版本 Playwright 容器的真实 Chromium 中运行，严禁用 jsdom/happy-dom 替代；KMP 内屏用 `tests/fixtures/showcase.html` 模拟桥接，截图基线只在容器内校验或加 `--update` 重录。
3. **实机完整旅程（`e2e` UiAutomator + 人工矩阵）**：
   - `apps/app/e2e` 为独立进程 `com.android.test` 自插桩模块，只经 UiAutomator、shell 设置与可见文案驱动被测应用；运行 `pnpm --filter @gazekeep/app test:device` 前必须先核对设备操作授权，测试结束后恢复所改系统设置，前提不满足时以 assumption 跳过并按未执行报告。
   - 真人注视、闭眼、多脸、暗光、口袋、相机抢占、Shizuku 退路写回与 LSPosed 钩点按 [实机完整验收矩阵](../../../docs/development.md#实机完整验收矩阵) 执行并脱敏记录。
