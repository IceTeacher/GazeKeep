# KMP miuix UI

本文件只补充根 [AGENTS.md](../AGENTS.md)，不复制共同安全承诺。

## 职责与边界

入口：ui/contract/navigation/screens/components/text。

commonMain 不 import android.*、engine 或具体后端。通过 GazeKeepController/PlatformActions 协作；文案进入 composeResources，隐私三句逐字引用真源。Preview 仅自检，退出及时取消。

相关专题：[当前架构](../docs/architecture/ui.md)；按任务选择[测试 skill](../.agents/skills/testing-ui/SKILL.md)，只加载受影响内容。

## 验证

在仓库根执行：

```bash
python3 scripts/check_architecture.py
./gradlew :shared:testAndroidHostTest
```

当前 host task 为 NO-SOURCE，没有 UI 行为测试。需运行真实页面场景，不能将此任务成功当验收；本轮不新建 UI 测试依赖。
