# LSPosed App 桥接

本文件继承根 [AGENTS.md](../../../../AGENTS.md) 和 [Android 工程规则](../../AGENTS.md)，只补充本模块职责与门禁，不重复共同安全承诺。

## 职责与边界

入口：LsposedKeepAwakeBackend/SystemPowerBinderResolver。

只依赖 core 与 bridge:protocol，不 import xposed entry/hook/power。握手/鉴权、过期 seq 与掉线不允许保亮。页面探测不得占有运行服务 Binder 生命周期。

相关专题：[当前架构](../../../../docs/architecture/backends/lsposed.md)；按任务选择[测试 skill](../../../../.agents/skills/testing-e2e/SKILL.md)，只加载受影响内容。

## 验证

在仓库根执行：

```bash
pnpm verify:architecture
pnpm --filter @gazekeep/app exec ./gradlew :backend:lsposed:assembleDebug
```

当前没有本模块独立行为测试；编译只能证明组装，不代表协议或设备通过。依赖协议主机测试和实机矩阵补证。
