---
name: testing-unit
description: 编写或评审 GazeKeep 单元测试时加载；覆盖 Kotlin 规则与隔离协作、web 纯逻辑和仓库工具，强调测试先行与风险覆盖。
---

# 单元测试

## 何时加载

涉及纯规则、reducer、解析、协议、配置、状态机、窄接口 adapter、构建脚本或架构守卫时加载。页面与视觉使用 `testing-ui`，真实设备旅程使用 `testing-e2e`。具体任务名与当前覆盖以[测试规范](../../../docs/development/testing.md#主机入口)为准，不捏造入口。

## 工作顺序

1. 从可观察行为和改动风险列出最小场景：正常、边界、非法输入、依赖失败、权限与隐私、取消、幂等或恢复。
2. 先写或调整最小测试并确认旧实现失败；文档、格式、纯重命名、生成物同步和无行为配置变更记录例外。
3. 实现使测试通过，再运行受影响测试与目录门禁。
4. 失败先核对实现、fixture、调用栈和环境；不删断言、扩大 mock、增加 skip 或改成空收集换取通过。

## 选择与边界

- 测试公开行为与稳定协作接口，不锁定私有调用顺序、局部常量或无意义的 getter；数值类断言优先表达比例、单调、连续与边界关系。
- 覆盖率只用于发现未解释的风险，不为行数或文件数补无行为价值的用例；重复覆盖同一决策的用例合并。
- 修复缺陷时回归测试先于修复；重构前用 characterization test 固定现有行为。
- 时间、随机数、协程调度、文件和系统服务必须可控；使用窄接口 fake 与隔离 fixture，不依赖真实摄像头、随机等待或执行顺序。

## 各工程

- Kotlin：kotlin.test 与 JUnit4，协程使用 `kotlinx-coroutines-test` 的虚拟时间。core 与 shared 的纯规则放在 `commonTest`，Android 模块的隔离协作放在 `src/test`。涉及引擎或后端时检查[七条不变量](../../../docs/development/testing.md#七条必守不变量)中的受影响项；不 Extend 不能证明 Hold 释放或实际熄屏，`NO-SOURCE` 不代表有行为测试。
- web：Vitest `unit` project，运行于 Node，测试位于 `apps/web/tests/unit/`，只覆盖不依赖 DOM 的模块与构建脚本；需要 DOM 的场景交给 `testing-ui` 的 `browser` project。
- 仓库工具：`node:test` 与严格断言，使用临时仓库和隔离 fixture，不操作真实索引；不引入第三方测试框架，保持与生产脚本相同的 Node 原生运行方式。
