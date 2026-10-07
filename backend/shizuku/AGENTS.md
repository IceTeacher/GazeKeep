# Shizuku 客户端与 shell

本文件只补充根 [AGENTS.md](../../AGENTS.md)，不复制共同安全承诺。

## 职责与边界

入口：client/protocol/oracle/restore；ShellService 保留根包。

保持 ShellService 反射入口、Binder descriptor、事务编号与字段顺序。只操作授权范围；临时系统超时原值必须先持久化，恢复成功再清记录。解析 fixture 去序列号；当前 apply() 债务保留。

相关专题：[当前架构](../../docs/architecture/backends/shizuku.md)；按任务选择[测试 skill](../../.agents/skills/testing-device/SKILL.md)，只加载受影响内容。

## 验证

在仓库根执行：

```bash
python3 scripts/check_architecture.py
./gradlew :backend:shizuku:testDebugUnitTest
```

主机测试不替代硬件与 UI。相机/阈值、后端、服务、系统设置或入口变更按[完整矩阵](../../docs/testing.md)验收，并记录版本和未执行项。
