# 仓库 Node 工具 (`scripts/repo-tools`)

继承根 [AGENTS.md](../../AGENTS.md)。本目录维护文档、Agent 资产、Android 模块架构、Git Hook、CI 变更检测、APK 静态门禁与 LSPosed 模块仓库同步脚本。

## 工程约束

- **Node 原生 TypeScript 执行**：所有脚本均为 ESM `.ts`，由 Node 原生类型剥离直接运行；只允许可擦除语法（禁用 `enum`、`namespace`、参数属性），相对导入必须写 `.ts` 扩展名。测试使用内置 `node:test` 与严格断言，不引入第三方 TS 执行器或测试框架。
- **路径与隔离安全**：文件与子进程调用显式接收 `--root` 仓库根、使用参数数组与 UTF-8，正确处理中文与空格路径。测试使用 `mkdtempSync` 临时目录隔离 fixture，绝不污染调用者的真实 Git 索引。
- **Hook 边界**：Lefthook 只处理暂存文件格式与语法校验；安装脚本显式执行，不覆盖用户自定义 `hooksPath`，工具缺失或 SHA-256 校验失败立即阻断，不隐式联网补装。

## 验证门禁

行为修改先按 [testing skill](../../.agents/skills/testing/SKILL.md) 在 `tests/` 建立失败断言，再修改实现并运行：

```bash
pnpm test:tools
pnpm --filter @gazekeep/repo-tools lint
pnpm verify:docs
pnpm verify:agent-assets
pnpm verify:architecture
pnpm verify:lsposed-repo
git diff --check
```
