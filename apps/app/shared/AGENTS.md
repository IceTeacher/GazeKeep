# KMP miuix UI

本文件继承根 [AGENTS.md](../../../AGENTS.md) 和 [Android 工程规则](../AGENTS.md)，只补充本模块职责与门禁，不重复共同安全承诺。

## 职责与边界

入口：ui/contract/navigation/screens/components/text。

commonMain 不 import android.*、engine 或具体后端。通过 GazeKeepController/PlatformActions 协作；UI 使用 miuix，字符串进入 composeResources，用户结果语言为「注视了屏幕 / 未注视屏幕 / 这次没看清」；隐私三句逐字引用[真源](../../../docs/privacy-security.md)。Preview 仅自检，退出及时取消。

相关专题：[当前架构](../../../docs/architecture/ui.md)；按任务选择[测试 skill](../../../.agents/skills/testing-browser/SKILL.md)，只加载受影响内容。

## 验证

在仓库根执行：

```bash
pnpm verify:architecture
pnpm --filter @gazekeep/app exec ./gradlew :shared:testAndroidHostTest
```

当前 host task 覆盖许可文本搜索的纯逻辑，没有 UI 行为测试。需运行真实页面场景，不能将此任务成功当验收；本轮不新建 UI 测试依赖。
