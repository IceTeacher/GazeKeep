# 应用装配与运行组件

本文件继承根 [AGENTS.md](../../../AGENTS.md) 和 [Android 工程规则](../AGENTS.md)，只补充本模块职责与门禁，不重复共同安全承诺。

## 职责与边界

入口：app/controller/selftest/applist/platform/runtime/service/notification。

组装 shared、engine、各后端和 xposed；业务决策只能回到 core reducer。RuntimeStatusStore 只发布状态，BackendSession 持有会话资源。保持 MainActivity、GazeService 外部类名；应用级启停与页面任务分清生命周期。

应用 Manifest 合并必须满足 app 层禁止权限要求；新增依赖若带入 `INTERNET`，用 `tools:node="remove"` 去掉，并核对最终合并 Manifest。

相关专题：[当前架构](../../../docs/architecture/android-runtime.md)；按任务选择[测试 skill](../../../.agents/skills/testing-browser/SKILL.md)，只加载受影响内容。

## 验证

在仓库根执行：

```bash
pnpm verify:architecture
pnpm --filter @gazekeep/app exec ./gradlew :android:testDebugUnitTest :android:assembleDebug :android:lintDebug
```

主机测试不替代硬件与 UI。相机/阈值、后端、服务、系统设置或入口变更按[完整矩阵](../../../docs/development/testing.md)验收，并记录版本和未执行项。
