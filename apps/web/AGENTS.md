# 官网工程规则 (`apps/web`)

继承根 [AGENTS.md](../../AGENTS.md)。本目录为基于 Astro 7、TypeScript、Tailwind CSS v4、Three.js、`shaders/js` 与 Compose Multiplatform Web（`wasmJs` / `js`）的静态双语官网。架构与视觉契约见 [官网与展示架构](../../docs/architecture/web.md)。

## 工程约束

- **零 React / 零孤岛水合**：保持纯 Astro 组件与原生 TypeScript 脚本调度，不引入 React/Vue/Svelte 运行时。
- **单一真源同步**：中英文数据位于 `src/data/{zh,en}.ts`；隐私三句由 `scripts/sync-kmp-strings.ts` 从 `apps/app/shared` 的 Compose 资源同步并与 [产品真源](../../docs/product.md#隐私文案真源) 校验；更新日志页由 `src/data/changelog.ts` 直接解析根 [docs/CHANGELOG.md](../../docs/CHANGELOG.md) 与 [docs/CHANGELOG.en.md](../../docs/CHANGELOG.en.md)。
- **构建产物与统计边界**：`public/kmp-showcase/` 与同步生成的隐私文件由 `pnpm --filter web build` 生成，禁止提交入库；保留 `apps/app/kotlin-js-store/` 下的 Yarn 锁文件。站点统计仅使用经 `edge-functions/stats/` 同站代理的自建 Umami，不启用 Partytown，不影响 App 零网络承诺。
- **样式入口**：`src/styles/tailwind.css` 是 `oxfmt` Tailwind 类名排序读取的唯一入口样式表，不得在其中引入其他局部样式文件。

## 验证门禁

测试规范见 [testing skill](../../.agents/skills/testing/SKILL.md) 与 [开发、测试与发布](../../docs/development.md#测试分层与入口)。`tests/` 目录严格镜像 `src/` 与 `scripts/`，整页交互与截图放 `tests/scenarios/`，目录守卫放 `tests/architecture/`：

```bash
pnpm --filter web test:unit              # Node 纯逻辑与构建脚本单测
pnpm --filter web test:browser:docker    # 锁定 Playwright 容器内的真实 Chromium 测试与截图基线校验
pnpm --filter web lint                   # oxlint 检查
pnpm --filter web check                  # astro check 类型检查
pnpm --filter web build                  # 完整构建（含 KMP Showcase 与隐私真源校验）
```
