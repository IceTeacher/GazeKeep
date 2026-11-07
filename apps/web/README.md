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

开发服务沿用本地 `dev` 配置；`dev`、`check` 和 `build:site` 会先校验并同步隐私资源。纯官网修改已有 KMP 产物时可运行 `pnpm --filter web run build:site`；生产部署使用完整 `build`。静态产物位于本目录 `dist/`。日常发布由 GitHub Actions 完成（预览、生产与冒烟规则见[官网架构](../../docs/architecture/web/website.md#六当前构建与后续验收)）；`pnpm --filter web run deploy -- -t "$(cat ~/.config/edgeone-token)"` 仅用于本地应急，完整构建、打包并直传到 `gazekeep` 项目的生产环境；令牌文件放在仓库外并设为 600 权限。

根路径 `/` 由 `edgeone.json` 的 `redirects` 在 EdgeOne 服务端 302 跳转到 `/zh/`；`astro preview` 不读取该配置，会改用 `index.astro` 的兜底页跳转。统计代理位于 `edge-functions/stats/`。

首次配置 EdgeOne Makers：

1. 在 EdgeOne Makers 控制台创建「直接上传」项目并选择加速区域（创建后不可更改），按需绑定自定义域名。
2. 在控制台「API Token」页创建带有效期的令牌，供 CI 使用。
3. 完成首次生产发布；EdgeOne 要求已有生产部署才能发布预览。以 `edgeone login` 的登录凭据部署时上传凭据接口会返回 `Project is not exist`，需用 API Token：

```bash
pnpm --filter web run deploy -- -t "$(cat ~/.config/edgeone-token)"
```

`pnpm --filter web run package:edgeone` 只把已构建的 `dist/` 与 `edge-functions/` 打包为 `.edgeone/`，并核对路由与环境泄漏；目录中存在 `.env` 或 `.env.local` 时拒绝打包，避免本地变量写入边缘函数包。完整构建依赖 JDK、Android SDK 与 Gradle，因此由 CI 构建后直传，不使用 EdgeOne 的 Git 集成与云端构建。

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
- 运行方式区只保留 `public/media/apps/` 中实际使用的 LSPosed、Shizuku 图标。KMP 演示仍发布 GazeKeep、LSPosed、Magisk、Shizuku、SIM Toolkit 与 Vector 图标，页面继续内联 Lucide；来源和许可证见随站点发布的 `public/media/credits.txt`。持机手部由 `src/scripts/grip-reference.ts` 与 `grip-geometry.ts` 维护，不再提交独立人物或手部图片。
- 页面架构、视觉约束、部署和验收边界见[官网架构](../../docs/architecture/web/website.md)；测试命令见上方“验证”。
