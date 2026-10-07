# 无障碍服务与保亮

本文件只补充根 [AGENTS.md](../../AGENTS.md)，不复制共同安全承诺。

## 职责与边界

入口：GazeA11yService/A11yKeepAwakeBackend/A11ySignals。

保持 canRetrieveWindowContent=false，不访问 source 或文本。信号主线程同步，先 UserActivity 再新 deadline；订阅可关闭且归属会话。解绑必须摘 overlay，Hold 到点熄屏需验证 Release，不只断言不 Extend。

相关专题：[当前架构](../../docs/architecture/backends/a11y.md)；按任务选择[测试 skill](../../.agents/skills/testing-device/SKILL.md)，只加载受影响内容。

## 验证

在仓库根执行：

```bash
python3 scripts/check_architecture.py
./gradlew :backend:a11y:testDebugUnitTest
```

主机测试不替代硬件与 UI。相机/阈值、后端、服务、系统设置或入口变更按[完整矩阵](../../docs/testing.md)验收，并记录版本和未执行项。
