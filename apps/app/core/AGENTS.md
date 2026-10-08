# 纯决策与策略

本文件继承根 [AGENTS.md](../../../AGENTS.md) 和 [Android 工程规则](../AGENTS.md)，只补充本模块职责与门禁，不重复共同安全承诺。

## 职责与边界

入口：commonMain 的 model/backend/engine/policy。

业务逻辑保持纯 Kotlin，不依赖 Android/AndroidX。reducer 是状态转移唯一入口，时间显式传入；测试用可控时钟。投票、门控、上限和退避纯函数维护；不把硬件释放当作纯测试已证明。

相关专题：[当前架构](../../../docs/architecture/engine.md)；按任务选择[测试 skill](../../../.agents/skills/testing-unit/SKILL.md)，只加载受影响内容。

## 验证

在仓库根执行：

```bash
pnpm verify:architecture
pnpm --filter @gazekeep/app exec ./gradlew :core:allTests
```
