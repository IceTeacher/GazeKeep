---
name: testing-unit
description: 编写或评审 GazeKeep 单元测试时加载；覆盖 Kotlin 规则与隔离协作、web 纯逻辑和仓库工具的回归与风险边界。
---

# 单元测试

## 何时加载

涉及纯规则、reducer、解析、协议、配置、状态机、窄接口 adapter、构建脚本或架构守卫时加载。页面与视觉使用 `testing-ui`，真实设备旅程使用 `testing-e2e`。测试层次、入口和当前覆盖以[测试规范](../../../docs/development/testing.md)为准。

## 独有边界

- 缺陷修复、纯规则、reducer、状态机、解析、协议和稳定可自动复现的安全行为，先写或调整最小回归测试并确认旧实现失败，再修改实现。
- 时间、随机数、协程调度、文件和系统服务必须可控；使用窄接口 fake 与隔离 fixture，不依赖真实摄像头、随机等待或执行顺序。fake 只证明被测协作契约，不替代真实设备、ROM、GPU 或特权后端验收。
- 测试公开行为与稳定协作接口，不锁定私有调用顺序、局部常量或无意义的 getter；覆盖率只用于发现未解释的风险，不为行数补用例。
- 失败时不删断言、扩大 mock、增加 skip 或改成空收集换取通过；文档、格式、纯重命名、生成物同步和无行为配置变更不引入行为测试。

## 各工程

- Kotlin：kotlin.test 与 JUnit4，协程使用 `kotlinx-coroutines-test` 的虚拟时间。core 与 shared 的纯规则放在 `commonTest`，Android 模块的隔离协作放在 `src/test`。引擎或后端相关不变量按[测试规范](../../../docs/development/testing.md#七条必守不变量)选择；不 Extend 不能证明 Hold 释放或实际熄屏，`NO-SOURCE` 不代表有行为测试。
- web：Vitest `unit` project 运行于 Node，测试按源码镜像放在 `apps/web/tests/src/` 与 `tests/scripts/`；只覆盖不依赖 DOM 的模块和构建脚本，需要 DOM 的场景交给 `testing-ui` 的 `browser` project。
- 仓库工具：`node:test` 与严格断言，使用临时仓库和隔离 fixture，不操作真实索引；不引入第三方测试框架，保持与生产脚本相同的 Node 原生运行方式。
