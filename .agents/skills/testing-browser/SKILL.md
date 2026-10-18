---
name: testing-browser
description: 设计、执行或评审 GazeKeep 页面交互验收；覆盖导航、授权与生命周期。
---

# 页面交互验收

先识别目标是 apps/app 的 Android UI 还是 apps/web 的网站。纯逻辑测试使用 testing-unit；连接真实相机、系统权限与后端的完整旅程使用 testing-e2e。

## Android 页面

读取 [UI 架构](../../../docs/architecture/ui.md) 和[页面场景](../../../docs/development/testing.md#分类与页面场景)，选择受影响交互。先定义前提、操作、预期状态、版本、设备与清理方式；条件允许时先复现问题再修复。

- 用可见文案和语义验证相关导航、授权、配置与生命周期行为；场景范围以测试规范为准。
- 自检必须 Looking→Away，Unknown 不推进；离页、后台、取消与 Activity 关闭释放相机。不保存含人脸的自检截图。
- shared host task 当前只覆盖许可文本搜索的纯逻辑，没有 UI 行为测试。可按场景人工验收；缺设备记未执行，controller fake 不能证明真实硬件能力。

## 网站浏览器

apps/web 使用 Astro 静态双语页面与 KMP Web 实时内屏；先运行 `pnpm --filter web build`，再使用 `pnpm --filter web dev` 启动本地预览。按实际可用的浏览器入口，以可访问名称和用户可见状态验证 DOM、事件、焦点与生命周期，清理监听器、计时器和存储。

页面验收需求不自动授权安装测试框架；文档任务无需引入 Playwright、Vitest 或 Android UI 依赖。按[测试规范](../../../docs/development/testing.md#记录与验收状态)报告实际结果与未执行条件。
