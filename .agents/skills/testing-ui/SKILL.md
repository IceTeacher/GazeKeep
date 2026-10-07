---
name: testing-ui
description: 定义、执行或评审 GazeKeep miuix 页面、导航、自检、权限和生命周期交互验收时使用；不把 NO-SOURCE 当成 UI 通过。
---

# Android UI 验收

页面与平台交互规则见 [UI 架构](../../../docs/architecture/ui.md)，场景和证据见[测试规范](../../../docs/testing.md)。

当前 shared 没有 UI 行为测试；host test 是 NO-SOURCE。先定义可复现的用户场景、预期状态、设备/版本与清理方式；有设备时先确认原行为，再修改与回归。设备不可用记录未执行，不假造失败或通过证据。

覆盖八页导航/返回、系统字体/明暗/旋转、授权/未就绪原因、白名单搜索/选择/滚动、配置保存/冷启动、快速开关、自检 Looking→Away、Unknown 不推进、离页/后台释放相机。

断言使用用户可见文案与语义，检查资源释放；不只判断方法被调用。未来引入自动化时才配置实际 runner、依赖和入口，本 skill 不授权为文档任务安装 UI 框架。当前允许人工按矩阵验收，并记录版本和未执行项。

UI fake/controller 只证明页面交互，完整相机/后端旅程读 [testing-device](../testing-device/SKILL.md)，纯状态规则读 [testing-unit](../testing-unit/SKILL.md)。不截取或保存自检含人脸画面。
