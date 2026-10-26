# Shizuku 客户端与 shell

本文件继承根 [AGENTS.md](../../../../AGENTS.md) 和 [Android 工程规则](../../AGENTS.md)，只补充本模块职责与门禁，不重复共同安全承诺。

## 职责与边界

入口：client/protocol/oracle/restore；ShellService 保留根包。

保持 ShellService 反射入口、Binder descriptor、事务编号与字段顺序。只操作授权范围；临时系统超时原值必须先持久化，恢复成功再清记录。解析 fixture 去序列号；同步 commit 失败不得修改系统超时，恢复读回确认后才清记录。故障注入只放在 debug 源码集，release 源码集保持同名空实现。

相关专题：[当前架构](../../../../docs/architecture/backends/shizuku.md)；按任务选择[测试 skill](../../../../.agents/skills/testing-e2e/SKILL.md)，只加载受影响内容。

## 验证

在仓库根执行：

```bash
pnpm verify:architecture
pnpm --filter @gazekeep/app exec ./gradlew :backend:shizuku:testDebugUnitTest
```

主机测试不替代硬件与 UI。相机/阈值、后端、服务、系统设置或入口变更按[完整矩阵](../../../../docs/development/testing.md)验收，并记录版本和未执行项。
