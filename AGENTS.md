# GazeKeep AI 协作规范

本文件是仓库级规则的唯一入口。任务开始时从根目录沿目标路径逐层读取各级 `AGENTS.md`；目录规则补充所在工程或模块的职责、架构和验证要求。回答、注释、UI 文案和提交说明用中文；代码标识符、日志 tag 用英文。

## 交付边界

- 仓库使用 pnpm workspace 与 Turborepo，维护应用、仓库工具，以及根级文档、任务和 Skills。
- 应用工程见 [apps/app/AGENTS.md](apps/app/AGENTS.md)，官网预留目录见 [apps/web/AGENTS.md](apps/web/AGENTS.md)，仓库工具见 [scripts/repo-tools/AGENTS.md](scripts/repo-tools/AGENTS.md)。各工程的技术约束与验证入口由其目录规则维护。
- 产品描述以[产品定义](docs/product.md)和[隐私与安全](docs/privacy-security.md)为依据，产品事实与安全承诺约束所有交付物；不能把目标设计写成已实现或已验证能力。当前缺口见[任务清单](tasks/todo.md)。

## 任务规则

1. 先检查工作树、确认任务范围，再逐层读取目标路径的目录规则，搜索真实入口、接口、配置和调用方；保留用户已有改动，不按旧设计猜测现状。
2. 只修改任务范围内文件；设计变更先更新相关活动架构，再同步实现、调用方和测试。长期取舍记入对应架构，术语只维护在 [CONTEXT.md](CONTEXT.md)。
3. 选择相关文档和门禁：文档任务先加载 [docs-maintenance](.agents/skills/docs-maintenance/SKILL.md)，再按[文档索引](docs/index.md)加载命中的页面；开发流程见[开发规范](docs/development.md)，测试见[测试规范](docs/testing.md)，安全见[隐私与安全](docs/privacy-security.md)。
4. 本地主机测试、隔离 fixture、静态检查和构建可以直接运行；失败先核对实现、断言、调用栈与环境，再修复并重跑受影响检查。
5. 完成前核对行为、接口、测试、文档、`git diff --check` 和工作树；不能运行的门禁说明缺失条件与剩余风险。

## 测试决策与渐进式加载

- 新增或修复行为时，先写或调整最小测试并确认旧实现失败，再修改代码；硬件/UI 先定义可复现的验收场景，条件不足明确记录未执行。文档、格式、纯重命名和无行为配置变更可记录例外。
- 覆盖率是风险诊断信号，不是测试数量目标；禁止无价值用例、弱化安全断言、无理由 skip 或空测试收集。
- 主机测试验证可隔离的规则、解析、协议、配置与工具行为；页面验收验证交互、权限和生命周期；完整旅程验证实际运行环境与外部依赖。具体场景和门禁按工程目录规则选择。
- 按任务与目录规则加载 [testing-unit](.agents/skills/testing-unit/SKILL.md)、[testing-browser](.agents/skills/testing-browser/SKILL.md) 或 [testing-e2e](.agents/skills/testing-e2e/SKILL.md)，不为文档任务引入测试框架。
- 文档只加载索引命中的当前专题，不默认读取整个 docs，不维护历史归档。
- fake、主机测试或构建成功不能证明真实环境验收通过；空测试、缺少运行条件和旧版本结果都不能写成当前验收通过。

## 安全与决策边界

- 已授权的本地实现、测试、格式化和修复持续推进，不为每一步重复请求确认。安装、重启、外部环境配置或发布先核对本次授权与影响；超出授权时先完成可审查的准备与验证，再说明具体动作及恢复方式。
- 根规则维护通用协作边界，各级目录规则补充工程与模块约束，相关活动架构维护当前设计；代码与测试证明实现现状，不能豁免安全承诺。发现差距时核对源码并登记待办，不能把目标写成已实现能力。
- 不提交本地配置、签名文件、密钥、原始系统转储或含个人信息的数据；测试样本只保留验证所需字段并脱敏。
- 新提交使用 `type(scope): emoji 中文描述`，type/scope 以 `scripts/repo-tools/config/commitlint.config.cjs` 为准。生成单条或分组提交信息分别加载 [commit-message](.agents/skills/commit-message/SKILL.md)、[commit-message-groups](.agents/skills/commit-message-groups/SKILL.md)。
- 默认不改写 Git 历史；用户明确要求时，先保留恢复引用，限定范围并验证文件快照与作者信息不变。Lefthook 只处理暂存文件的格式和轻量检查，完整构建与测试按风险执行。
- 执行边界与交付报告见[代理工作流](docs/agent-workflow.md)。

## 完成定义

- 目标行为已实现，相关接口、配置、测试和文档保持一致；正常、边界、预期失败、权限、安全及恢复路径按风险验证。
- 相关工程或模块门禁、文档/资产检查和 `git diff --check` 通过，或如实记录缺失条件、替代检查与剩余风险；不把未执行或跳过写成通过。
- Diff 中没有敏感信息、临时产物或无关修改；[tasks/todo.md](tasks/todo.md) 只保留未完成事项与本次验证、未运行项和风险，不累积历史流水。
- 发布说明记录对应版本的验证环境、依赖版本与实际验收结果，不能从构建或隔离测试推断兼容性。
