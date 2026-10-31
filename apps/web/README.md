# GazeKeep 官网

Astro 7 静态官网，支持 `/zh/`、`/en/`，使用 Tailwind CSS v4、原生连续滚动、Three.js 立体设备与单色 SVG 持机手部，以及 `shaders/js`。手机内屏由共享模块的真实 miuix 页面渲染，采用固定演示数据。

## 构建与预览

在仓库根运行：

```bash
pnpm install
pnpm --filter web build
pnpm --filter web dev --host 127.0.0.1
```

完整构建先运行 KMP Wasm/JS 分发任务，同步资源至 `public/kmp-showcase/`，然后校验隐私资源并生成静态页面。首次构建需要 JDK、Android SDK 与 Gradle/Node 依赖下载条件；Kotlin 内部 Yarn 与根 pnpm workspace 的检查冲突由构建脚本局部处理。

开发服务沿用本地 `dev` 配置；`dev`、`check` 和 `build:site` 会先校验并同步隐私资源。纯官网修改已有 KMP 产物时可运行 `pnpm --filter web run build:site`；生产部署使用完整 `build`。日常发布由 GitHub Actions 完成（预览、生产与冒烟规则见[官网架构](../../docs/architecture/web/website.md#六当前构建与后续验收)）；`pnpm --filter web run deploy` 仅用于本地应急，登录 Wrangler 后构建并直传到 `gazekeep` 项目的 `main` 分支。静态产物位于本目录 `dist/`。根路径 `/` 由 `public/_redirects` 在 Cloudflare Pages 服务端 302 跳转到 `/zh/`；`astro preview` 不读取该文件，会改用 `index.astro` 的兜底页跳转，可用 `pnpm --filter web exec wrangler pages dev dist` 验证服务端跳转。

首次配置 Cloudflare Pages：

```bash
pnpm --filter web exec wrangler login
pnpm --filter web exec wrangler pages project create gazekeep --production-branch main --force
```

完整构建依赖 JDK、Android SDK 与 Gradle，因此由 CI 构建后使用 Cloudflare Pages Direct Upload。Direct Upload 项目不能原地切换为 Git 集成；如需 Cloudflare 自动从 Git 构建，需新建 Git 集成项目并提供 KMP 所需工具链。

## 验证

```bash
pnpm --filter web test:unit
pnpm --filter web test:browser:docker
pnpm --filter web check
pnpm verify:architecture
pnpm verify:docs
pnpm verify:agent-assets
```

浏览器检查双语、明暗、窄屏、键盘、滚动联动、FAQ、减少动态效果及加载恢复。KMP 展示壳只包含平台空实现，不申请相机或后端授权；演示与 Android 实机验收分别记录。

## 维护

- 页面文案集中于 `src/data/zh.ts` 和 `src/data/en.ts`；隐私三句由共享 Compose 资源同步并由 `dev`、`check` 与 `build:site` 校验。
- KMP Wasm/JS 展示资源由完整 `pnpm --filter web build` 生成，`public/kmp-showcase/` 和隐私同步产物不提交；纯官网修改已有产物时可运行 `pnpm --filter web run build:site`。
- 展示字体使用 Noto Sans CJK SC 字符子集；更新字符时运行 `pnpm --filter web font:showcase /path/to/NotoSansCJK-Regular.ttc`，再重新完整构建。
- 运行方式区只保留 `public/media/apps/` 中实际使用的 LSPosed、Shizuku 图标；来源和许可证见 `public/media/credits.txt`。持机手部由 `src/scripts/grip-reference.ts` 与 `grip-geometry.ts` 维护，不再提交独立人物或手部图片。
- 页面架构、视觉约束、部署和验收边界见[官网架构](../../docs/architecture/web/website.md)；测试命令见上方“验证”。
