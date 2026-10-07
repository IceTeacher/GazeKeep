# Android 引擎与采集

本文件只补充根 [AGENTS.md](../AGENTS.md)，不复制共同安全承诺。

## 职责与边界

入口：runtime/capture/camera/classify/gate/oracle/config。

只依赖 core 和注入接口，不依赖具体后端或 UI。串行处理事件，副作用结果回灌。取消异常继续传播，帧留在 analyzer；ConfigStore 单实例，不以未落地传感器或日志替代当前事实。

相关专题：[当前架构](../docs/architecture/capture-classification.md)；按任务选择[测试 skill](../.agents/skills/testing-unit/SKILL.md)，只加载受影响内容。

## 验证

在仓库根执行：

```bash
python3 scripts/check_architecture.py
./gradlew :engine:testDebugUnitTest
```

主机测试不替代硬件与 UI。相机/阈值、后端、服务、系统设置或入口变更按[完整矩阵](../docs/testing.md)验收，并记录版本和未执行项。
