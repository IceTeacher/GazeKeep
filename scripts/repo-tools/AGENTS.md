# 仓库 Node 工具

继承根 [AGENTS.md](../../AGENTS.md)。脚本使用 JavaScript ESM `.mjs`，测试使用 `node:test` 与严格断言，不引入 TS 执行器。

文件与子进程操作使用显式仓库根、参数数组和 UTF-8，处理中文及空格路径。检查脚本接受 `--root`，临时 fixture 不依赖调用者的真实 Git 索引。文档检查保持索引、日期、路径与锚点校验，不设归档豁免；架构检查读取独立 Android 工程的十个模块。

Hook 只处理暂存文件，部分暂存隐藏和恢复继续交给 Lefthook。安装入口显式执行，不覆盖已有 Hook 或自定义 hooksPath；缺工具和下载摘要错误阻断，不联网补装。

运行 `pnpm test:tools`、`pnpm verify:docs`、`pnpm verify:agent-assets`、`pnpm verify:architecture` 和 `git diff --check`。行为改动读取[主机测试 skill](../../.agents/skills/testing-unit/SKILL.md)，先确认失败场景。
