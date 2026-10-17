---
name: docs-maintenance
description: 维护 GazeKeep 文档、索引与引用；用于文档增删、修订或重命名。
---

# 文档维护

根据根与目标路径的 `AGENTS.md`，通过 [文档路由](../../docs-index.md) 选读命中专题；已读且未变化的规则不重复加载。局部修订只核对相关内容及引用，不默认读取全部文档或任务清单。

## 内容归属

- README 与 docs/ 面向用户、贡献者和维护者；AGENTS 与 .agents/ 维护代理指令，任务与计划统一放在 docs/plans/。术语只在 CONTEXT.md；架构维护当前设计与取舍，代码和测试证明实现现状。
- `docs/` 根目录只保留 `index.md`；产品定义、使用与隐私安全归入 `docs/product/`，开发贡献、测试与发布归入 `docs/development/`，架构、任务计划和资源分别归入 `docs/architecture/`、`docs/plans/` 与 `docs/assets/`。
- 隐私三句以 [隐私与安全](../../../docs/product/privacy-security.md) 为真源，与资源逐字一致。当前实现、目标、主机通过、实机通过与未执行分别表达。
- 删除重复或过期内容前检查调用与引用，将仍有效的规则归入对应专题，未完成风险放入 [docs/plans/todo.md](../../../docs/plans/todo.md)。保留计划的完成状态和当前候选有效验收证据，不另建归档目录或占位计划。

## 索引与重命名

- 活动文档变化同步 `.agents/docs-index.md` 的路径、触发条件、状态和最后复核日，沿用五列表格与反引号相对路径。纯机械路径调整不推进复核日。`docs/index.md` 只维护面向读者的两类入口，架构专题由总览导航，不复制 Agent 登记表。
- 任务与计划统一放在 `docs/plans/`，不按进行中、已完成等状态拆分目录或索引栏目；状态在正文和索引中标注。任务清单固定使用 `todo.md`；专题计划使用 `YYYYMMDD-slug.md`，唯一的“最后维护：YYYY-MM-DD”与文件前缀、索引日期一致，表示最后实质维护日。
- 重命名或删除后搜索并更新全部路径与锚点引用。

## 完成检查

运行 `pnpm verify:docs`、`pnpm verify:agent-assets`、本次文件格式检查和 `git diff --check`。失败修复引用、登记、元数据或内容，不删登记或放宽检查。纯文档变更无需应用构建、设备验收或占位测试；涉及产品事实时仍须核对依据。
