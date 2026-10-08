---
name: testing-browser
description: 定义或评审 GazeKeep 页面交互验收时加载；区分 Android miuix 页面与未来网站浏览器测试，覆盖导航、自检、权限和生命周期。
---

# 页面与浏览器测试

## 何时加载

涉及用户可见页面、导航、焦点、授权、配置交互或页面生命周期时加载。先识别目标是 apps/app 的 Android UI 还是 apps/web 的网站；纯规则读 [testing-unit](../testing-unit/SKILL.md)，真实相机/后端完整旅程读 [testing-e2e](../testing-e2e/SKILL.md)。

## Android 页面

1. 读取 [UI 架构](../../../docs/architecture/ui.md) 和[测试规范](../../../docs/testing.md)。先定义可复现场景、预期状态、版本、设备与清理方式；有条件时先复现再修复。
2. 使用可见文案与语义验证八页导航/返回、字体/明暗/旋转、授权与未就绪原因、白名单搜索/选择/滚动、保存/冷启动和快速开关。
3. 自检必须 Looking→Away，Unknown 不推进；离页、后台、取消与 Activity 关闭释放相机。
4. 当前 shared 没有 UI 行为测试，host task 为 NO-SOURCE。允许按矩阵人工验收，缺设备记未执行，不伪造失败或通过。
5. 不保存含人脸的自检截图，不用 controller fake 证明真实硬件能力。

## 网站浏览器

apps/web 当前仅预留目录，没有网页、浏览器框架、测试 runner 或执行命令。网站建立后，依据实际框架选择真实浏览器入口，用可访问名称和用户可见状态验证 DOM、事件、焦点与生命周期，并清理监听器、计时器和存储。

本 Skill 不授权为文档任务安装 Playwright、Vitest 或 Android UI 框架，也不把未来套件当作已有能力。

## 与 E2E 的边界

单页或组件测试验证交互；连接真实相机、系统权限和后端的旅程由 testing-e2e 负责。两层分别报告结果与未执行条件。
