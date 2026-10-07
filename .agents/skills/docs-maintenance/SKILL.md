---
name: docs-maintenance
description: 按任务渐进加载 GazeKeep 活动文档，维护索引、计划日期、历史归档和引用；用于创建、修改、评审或迁移仓库文档。
---

# 文档维护与渐进式加载

先读根与目标模块 AGENTS，再读 [docs/index.md](../../../docs/index.md)，只加载命中的活动专题。archive 仅在追溯背景时读取，不默认扫全目录。

## 维护规则

- 活动文档更新、新增、删除或重命名，同步索引路径、触发条件、状态和复核日期。归档正文保留历史日期与结论，只在迁移时机械调整引用，后续不默默重写历史。
- 活动计划使用 YYYYMMDD-slug.md。顶部唯一“最后维护：YYYY-MM-DD”与文件名前缀、索引复核日一致，表示最后实质维护；纯机械迁移不推进日期。语义维护后改名并更新仓库引用。
- 根安全承诺不能被当前代码豁免。当前实现、目标设计、主机通过、实机通过和待验证分别表达，证据带版本，不能跨版本套用。
- 术语在根 CONTEXT.md；ADR 只解释已有或经确认的长期取舍，不把每次缺陷修复制造为决策。
- 隐私三句以 privacy-security.md 为真源，与 Compose 资源逐字一致；readme.md 服务使用者，AGENTS 服务代理，专题文档维护细节。

完成运行 pnpm verify:docs、pnpm verify:agent-assets、git diff --check；失败修复索引、日期、引用或真实内容，不删登记/放宽校验掩盖错误。任务结果追加 tasks/todo.md。
