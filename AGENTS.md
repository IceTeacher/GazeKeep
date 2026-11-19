# GazeKeep 协作规范

本文件是仓库级规则与文档路由的唯一入口。回答、注释、UI 文案和提交说明使用中文；代码标识符与日志 tag 使用英文。

## 工程拓扑与文档路由

仓库采用 pnpm workspace 与 Turborepo 管理三大工程及根级文档、Skills；任务开始时按修改范围选读对应工程 `AGENTS.md` 与专题文档，**不预读无关文档**：

| 范围 / 触发场景            | 规则与文档入口                                                                                                                                                                                                                                |
| -------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Android 与 KMP 应用**    | [apps/app/AGENTS.md](apps/app/AGENTS.md) · [应用与 KMP 架构](docs/architecture/app.md)                                                                                                                                                        |
| **三大保亮后端与系统桥接** | [三大保亮后端架构](docs/architecture/backends.md)（LSPosed / Shizuku / 无障碍跨进程契约与恢复）                                                                                                                                               |
| **Astro 双语官网与展示壳** | [apps/web/AGENTS.md](apps/web/AGENTS.md) · [官网与展示架构](docs/architecture/web.md)                                                                                                                                                         |
| **仓库工具、门禁与 Hook**  | [scripts/repo-tools/AGENTS.md](scripts/repo-tools/AGENTS.md)                                                                                                                                                                                  |
| **产品边界、术语与隐私**   | [产品、术语与隐私](docs/product.md)（领域语言、隐私三句真源、十条安全红线与多 ROM 排障）                                                                                                                                                      |
| **开发、测试与发布门禁**   | [开发、测试与发布](docs/development.md) · [更新日志](docs/CHANGELOG.md)（[EN](docs/CHANGELOG.en.md)）                                                                                                                                         |
| **当前实测基线与迭代规划** | [任务清单与实测基线](docs/plans/todo.md) · [v1.1.7 迭代规划](docs/plans/v1.1.7.md) · [v1.1.8 迭代规划](docs/plans/v1.1.8.md)                                                                                                                  |
| **专项工作流 Skills**      | [testing](.agents/skills/testing/SKILL.md) · [docs-maintenance](.agents/skills/docs-maintenance/SKILL.md) · [commit-message](.agents/skills/commit-message/SKILL.md) · [commit-message-groups](.agents/skills/commit-message-groups/SKILL.md) |

## 契约与不变量驱动准则

1. **代码即现状（What/How），文档即不变量与决策原因（Invariants/Why）**：
   - 代码类型系统与静态守卫（`verify:architecture`、`verify:apk`）是实现现状的唯一真源；文档只维护**安全不变量、跨进程契约与真机实测取舍（ADR）**，不抄录代码一眼可见的字段列表或单测类名。
   - 制定版本规划或技术方案前，必须先对照现有源码与 [实测基线](docs/plans/todo.md)（耗时、功耗、ROM 表现）核算真实收益与退化风险；可由代码与实测证伪的低收益或损准确率策略直接排除并说明依据。
2. **风险驱动 TDD 与最小充分验证**：
   - **纯规则、状态机（`EngineReducer`）、解析、跨进程协议与可自动复现的安全边界**：必须先写或调整最小失败测试（Red-Green），确认旧实现失败后再修改实现。
   - **UI、视觉、真实设备与重构**：先定义可观察验收场景，按受影响范围运行对应门禁（见 [testing skill](.agents/skills/testing/SKILL.md) 与 [开发、测试与发布](docs/development.md)），通过即停，不为文档或纯格式变更制造占位测试。
   - **证据诚实性**：主机测试、fake 或构建成功不能替代真机/ROM/硬件熄屏验收；未执行项或缺少运行条件必须如实记录原因，绝不把目标设计、跳过或旧版本结果写成当前已验证事实。
3. **安全、提交与 Skills 边界**：
   - 已授权的本地实现、测试、格式化与修复持续推进；设备安装、重启、系统设置修改与发布先核对当次授权与恢复方式。
   - 严禁提交本地配置、密钥、原始系统转储（`dumpsys`）或含人脸/序列号的数据。
   - 提交信息遵循 `type(scope): emoji 中文描述`（以 `scripts/repo-tools/config/commitlint.config.ts` 为准），默认不改写 Git 历史；只请求生成提交说明时以暂存区为准输出，不修改索引。
   - `.agents/skills/` 是仓库 Skill 唯一真源，修改时逐字同步 `.claude/skills/` 并运行 `pnpm verify:agent-assets`。

## 完成定义

- 目标行为已实现，相关接口、测试与架构契约保持一致；按风险跑通受影响工程门禁、`pnpm verify:docs`、`pnpm verify:agent-assets` 与 `git diff --check`。
- 交付报告清晰说明**改动**、**已验证**、**未执行/原因**与**剩余风险**；[docs/plans/todo.md](docs/plans/todo.md) 只维护最新有效基线与未完成事项，不累积历史调试流水。

<!-- BEGIN:turborepo-agent-rules -->

# This is NOT the Turborepo you know

Turborepo configuration, task behavior, and CLI commands can vary between installed versions and may differ from your training data. Resolve the `turbo` package from this file's directory or relevant workspace; in monorepos, it may not be visible from the repository root. For example, run `node -p "require.resolve('turbo/package.json')"` from a workspace that depends on `turbo`.

Read `docs/README.md` inside that installed package first, then read the relevant pages from its `docs/` directory before changing Turborepo configuration or commands. Heed deprecation notices. These bundled docs match the installed package version and are available without network access.

This block is written and re-added by `turbo` before repository-scoped commands when an AI agent is detected. In the Turborepo source repository, its template is defined in `crates/turborepo-cli/src/cli/agent_guidance.rs`. Removing the managed block while updates are enabled means a later qualifying invocation will add it again. Set `"agentGuidance": false` in the root `turbo.json` or `turbo.jsonc` to opt out; this does not remove an existing block. Keep the block committed with your work to avoid an uncommitted change on the next agent invocation.

<!-- END:turborepo-agent-rules -->
