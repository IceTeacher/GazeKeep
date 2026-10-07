# system_server 薄钩子

本文件只补充根 [AGENTS.md](../AGENTS.md)，不复制共同安全承诺。

## 职责与边界

入口：entry/hook/power。

禁止协程、DataStore、Compose、相机、模型和文件 I/O。候选逐签名解析，失配不安装，回调整体 try/catch(Throwable)，连续异常熔断；PMS 锁内只计算/投递，不 Binder、不取其他锁。保持 LegacyEntry 入口。

相关专题：[当前架构](../docs/architecture/backends/lsposed.md)；按任务选择[测试 skill](../.agents/skills/testing-device/SKILL.md)，只加载受影响内容。

## 验证

在仓库根执行：

```bash
python3 scripts/check_architecture.py
./gradlew :xposed:testDebugUnitTest
```

主机测试不替代硬件与 UI。相机/阈值、后端、服务、系统设置或入口变更按[完整矩阵](../docs/testing.md)验收，并记录版本和未执行项。
