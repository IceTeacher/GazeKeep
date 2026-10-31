---
name: testing-ui
description: 编写或评审 GazeKeep 界面与视觉测试时加载；覆盖 app 的 Compose 交互与 Roborazzi 截图，以及 web 的 Vitest 真实 Chromium 测试。
---

# 界面与视觉测试

## 何时加载

涉及页面导航、可见状态、交互、主题、语言、字体缩放、布局、截图基线，或 web 的 DOM、事件、滚动、焦点、存储与客户端生命周期时加载。纯规则与 mapper 使用 `testing-unit`；连接真实相机、ROM、系统设置或后端的旅程使用 `testing-e2e`。测试目录、命令和当前覆盖以[测试规范](../../../docs/development/testing.md#界面与视觉入口)为准。

## 独有边界

1. 先定义用户可见的可观察场景；场景稳定且可自动化时，可以先写失败测试。截图回归先确认差异确实来自本次改动。
2. 使用可见文案、角色、可访问名称和稳定状态断言；只有没有语义替代时才使用测试标签或 data 属性。
3. 每个用例独立、可重复，固定时间、语言、主题、尺寸与动画；清理 listener、timer、协程、存储和路由，不依赖执行顺序。
4. 截图只覆盖关键状态与组合，基线逐张核对；diff 和实际图像只留在构建目录，不批量重录掩盖回归。
5. 截图不得包含相机画面、人脸或真实个人数据；自检页使用无帧占位预览，应用列表使用虚构包名与图标。

## app：Compose 与截图

`shared` 的 `androidHostTest` 使用 Robolectric、Compose UI 测试和 Roborazzi，在主机 JVM 驱动真实 App 入口，但 controller 与 PlatformActions 为测试实现，不连接真实相机或后端。fake 只证明 UI 与契约；相机释放、真实 ROM 字体/系统栏和厂商主题由 `testing-e2e` 或实机验收补证。页面场景和截图组合按[测试规范](../../../docs/development/testing.md#界面与视觉入口)选择。

## web：真实浏览器

Vitest `browser` project 使用 `@vitest/browser-playwright` 的 Chromium；不用 happy-dom、jsdom 或 DOM polyfill 替代真实浏览器。KMP iframe 使用受控 fixture 模拟就绪、失败和重试，真实 KMP 产物另在构建或预览中验收。视觉断言固定 viewport、关闭动画和 WebGPU 等环境变量；无头 Chromium 不代表 Safari、Firefox、真实 GPU 或实体手机触摸。

## 结果判定

失败先核对实现、断言、fixture 与环境，不放宽截图阈值、删断言或 skip 换取通过。按测试规范报告实际运行的 project、截图数量和未执行条件。
