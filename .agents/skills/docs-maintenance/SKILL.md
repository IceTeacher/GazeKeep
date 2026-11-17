---
name: docs-maintenance
description: 维护 GazeKeep 文档、架构契约与引用；用于文档增删、修订或重命名。
---

# 文档维护

按根 [AGENTS.md](../../../AGENTS.md#工程拓扑与文档路由) 选读目标文档，局部修订只核对相关事实与引用，不预读无关文档。

## 内容归属与单一真源

- **代码即现状，文档即不变量与 ADR**：代码与静态守卫（`verify:architecture`、`verify:apk`）证明实现现状；文档只维护产品边界、安全红线、跨进程契约与真机实测取舍（ADR），不重复抄录字段列表或单测类名。
- **目录拓扑**：
  - [docs/index.md](../../../docs/index.md)：读者导航入口。
  - [docs/product.md](../../../docs/product.md)：产品定位、领域术语、中英隐私三句真源（与 Compose 资源、根 `README.md` 逐字一致）、十条安全红线与安装排障指南。
  - [docs/development.md](../../../docs/development.md)：工具链、命令、七条必守不变量、三层测试矩阵、CI 流水线与发布门禁。
  - `docs/architecture/{app,backends,web}.md`：分别维护 Android/KMP 核心架构、三大保亮后端跨进程契约与 Astro/KMP Wasm 官网架构。
  - [docs/CHANGELOG.md](../../../docs/CHANGELOG.md) & [docs/CHANGELOG.en.md](../../../docs/CHANGELOG.en.md)：双语版本更新日志与启用步骤真源（按 `# v<版本>` 分节）。
  - `docs/plans/`：[todo.md](../../../docs/plans/todo.md) 维护最新实测基线表与未完成事项（不累积历史调试流水）；版本迭代规划（如 `v1.1.7.md`）在对应版本发布合入后即可清理归并。

## 完成检查

重命名、删除或修改章节标题后，全局搜索并更新相对路径与锚点引用。交付前运行：

```bash
pnpm verify:docs
pnpm verify:agent-assets
pnpm verify:lsposed-repo
git diff --check
```

纯文档变更无需运行 Android 构建或新增占位测试。
