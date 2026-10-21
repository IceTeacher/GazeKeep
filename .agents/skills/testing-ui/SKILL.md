---
name: testing-ui
description: 编写或评审 GazeKeep 界面与视觉测试时加载；覆盖 app 的 Compose 交互与 Roborazzi 截图，以及 web 的 Vitest 真实 Chromium 测试。
---

# 界面与视觉测试

## 何时加载

涉及页面导航、可见状态、交互、主题、语言、字体缩放、布局、截图基线，或 web 的 DOM、事件、滚动、焦点、存储与客户端生命周期时加载。纯规则与 mapper 使用 `testing-unit`；连接真实相机、ROM、系统设置或后端的旅程使用 `testing-e2e`。具体任务名、目录与当前覆盖以[测试规范](../../../docs/development/testing.md#界面与视觉入口)为准。

## 共同规则

1. 先写能在当前实现上失败的用户可见场景，再实现或修复；截图回归先确认旧基线与新实现的差异确实来自本次改动。
2. 用可见文案、角色、可访问名称和稳定状态断言；只有没有语义替代时才使用测试标签或 data 属性。
3. 每个用例独立、可重复，固定时间、语言、主题、尺寸与动画；清理 listener、timer、协程、存储和路由，不依赖执行顺序。
4. 截图基线只覆盖关键状态与组合，随测试提交；diff 与实际图像只留在构建目录。基线更新必须逐张核对并在交付中说明原因，不批量重录掩盖回归。
5. 截图不得包含相机画面、人脸或真实个人数据；自检页使用无帧占位预览，应用列表使用虚构包名与图标。

## app：Compose 交互与截图

- 位于 `shared` 的 `androidHostTest`，使用 Robolectric、Compose UI 测试和 Roborazzi，在主机 JVM 上运行，不需要设备。页面通过 controller 与 PlatformActions 的测试实现驱动，不连接真实相机或后端。
- 覆盖受影响页面的导航与返回、授权与未就绪原因、配置保存回显、白名单搜索/选择/空结果、日志列表；截图组合按风险选择明暗、中英、字体缩放和横竖屏。
- 自检：Looking→Away 才完成，Unknown 不推进；离页、取消后 controller 收到释放。fake 只证明 UI 与契约，相机实际释放仍由 `testing-e2e` 补证。
- Robolectric 渲染不等于真实 ROM 渲染；字体、系统栏与厂商主题差异在实机验收记录。

## web：Vitest Browser Mode

- `apps/web` 使用 Vitest projects：`unit` 运行于 Node，收集 `tests/unit/**/*.test.ts`；`browser` 使用 `@vitest/browser-playwright` 的 Chromium，收集 `tests/browser/**/*.browser.test.ts`。两个 project 互斥且都必须产生真实结果。
- 不用 happy-dom、jsdom 或 DOM polyfill 代替真实浏览器。KMP 展示 iframe 使用受控 fixture 模拟就绪、失败与重试；真实 KMP 产物只在完整构建后的人工或预览验收中验证。
- 覆盖受影响的滚动叙事与吸附、减少动态、语言与 `lang`、展示加载失败回退、页面卸载清理；视觉断言使用 `toMatchScreenshot`，固定 viewport 并关闭动画与 WebGPU 等环境相关效果。
- 无头 Chromium 不代表 Safari、Firefox、真实 GPU 或实体手机触摸；这些条件按[记录要求](../../../docs/development/testing.md#记录与验收状态)单列未执行。

## 结果判定

失败先核对实现、断言、fixture 与环境，不放宽截图阈值、删断言或 skip 换取通过。按测试规范报告实际运行的 project、截图数量与未执行条件。
