# LSPosed App 桥接

本文件继承根 [AGENTS.md](../../../../AGENTS.md) 和 [Android 工程规则](../../AGENTS.md)，只补充本模块职责与门禁，不重复共同安全承诺。

## 职责与边界

入口：LsposedKeepAwakeBackend/SystemPowerBinderResolver。

只依赖 core 与 bridge:protocol，不 import xposed entry/hook/power。握手/鉴权、过期 seq 与掉线不允许保亮。页面探测不得占有运行服务 Binder 生命周期。

相关专题：[当前架构](../../../../docs/architecture/backends/lsposed.md)；按任务选择 [testing-unit](../../../../.agents/skills/testing-unit/SKILL.md) 或 [testing-e2e](../../../../.agents/skills/testing-e2e/SKILL.md)，只加载受影响内容。

## 验证

在仓库根执行：

```bash
pnpm verify:architecture
pnpm --filter @gazekeep/app exec ./gradlew :backend:lsposed:testDebugUnitTest :backend:lsposed:assembleDebug
```

主机测试用 Robolectric 与本地 Binder 按 BridgeProtocol 布局应答，覆盖握手、注册、seq/STALE 映射与 Binder 失败关闭；不证明真实 system_server 钩点、签名或作用域，仍以实机矩阵补证。
