---
name: testing-unit
description: 编写、修复或评审 GazeKeep Kotlin 与 Node 主机测试。
---

# 主机测试

用于纯规则、reducer、解析、协议、配置、隔离 adapter 与仓库工具。按[测试规范](../../../docs/development/testing.md#主机入口)和目标目录规则选择实际入口；页面与真实设备旅程分别由 testing-browser、testing-e2e 处理。

## 测试设计

- 行为变更先用最小用例确认旧实现失败，再实现并运行受影响测试。按风险选择正常、边界、错误、权限、恢复或取消场景。
- core/commonTest 验证纯规则，Android 模块 src/test 验证隔离协作；Node 使用 node:test 和临时仓库，不操作真实索引。
- 固定时间与依赖，使用窄接口和隔离 fixture；验证公开行为和稳定协作，不锁定私有调用顺序，不依赖真实摄像头、随机等待或执行顺序。
- 涉及引擎或后端时检查[七条不变量](../../../docs/development/testing.md#七条必守不变量)中的受影响项；不 Extend 不能证明 Hold 释放或实际熄屏。

## 结果判定

失败先核对实现、断言、fixture、调用栈和环境，不扩大 mock 或 skip 掩盖错误。NO-SOURCE 不代表有行为测试。按[验证范围与停止条件](../../../docs/development/testing.md#验证范围与停止条件)结束检查；具体模块与缺失套件以测试规范为准，不捏造入口。
