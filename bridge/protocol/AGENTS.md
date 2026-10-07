# LSPosed 跨进程契约

本文件只补充根 [AGENTS.md](../../AGENTS.md)，不复制共同安全承诺。

## 职责与边界

入口：BridgeProtocol 与 IGazeKeepClient.aidl。

只维护常量和 AIDL，保留 .xposed.protocol 包名。无 core、协程、UI、DataStore 或模型依赖；改协议前明确升级与重启兼容，不只调整一端。

相关专题：[当前架构](../../docs/architecture/backends/lsposed.md)；按任务选择[测试 skill](../../.agents/skills/testing-unit/SKILL.md)，只加载受影响内容。

## 验证

在仓库根执行：

```bash
python3 scripts/check_architecture.py
./gradlew :bridge:protocol:assembleDebug :xposed:testDebugUnitTest
```

当前没有本模块独立行为测试；编译只能证明组装，不代表协议或设备通过。依赖协议主机测试和实机矩阵补证。
