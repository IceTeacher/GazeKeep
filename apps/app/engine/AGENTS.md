# Android 引擎与采集

本文件继承根 [AGENTS.md](../../../AGENTS.md) 和 [Android 工程规则](../AGENTS.md)，只补充本模块职责与门禁，不重复共同安全承诺。

## 职责与边界

入口：runtime/capture/camera/classify/gate/oracle/config。

只依赖 core 和注入接口，不依赖具体后端或 UI。串行处理 EngineEvent，副作用结果回灌 reducer。取消异常继续传播，帧留在 analyzer；ConfigStore 单实例，不以未落地传感器或日志替代当前事实。

ML Kit 只使用 bundled `face-detection`，依赖版本遵循 app 层版本目录要求。

相关专题：[当前架构](../../../docs/architecture/capture-classification.md)；按任务选择[测试 skill](../../../.agents/skills/testing-unit/SKILL.md)，只加载受影响内容。

## 验证

在仓库根执行：

```bash
pnpm verify:architecture
pnpm --filter @gazekeep/app exec ./gradlew :engine:testDebugUnitTest
```

主机测试不替代硬件与 UI。相机/阈值、后端、服务、系统设置或入口变更按[完整矩阵](../../../docs/testing.md)验收，并记录版本和未执行项。
