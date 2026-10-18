# GazeKeep 官网

Astro 7 静态官网，支持 `/zh/`、`/en/`，使用 Tailwind CSS v4、GSAP、功能展示区的原生 Scroll Snap 和 `shaders/js`。手机内屏由共享模块的真实 miuix 页面渲染，采用固定演示数据。

## 构建与预览

在仓库根运行：

```bash
pnpm install
pnpm --filter web build
pnpm --filter web dev --host 127.0.0.1
```

完整构建先运行 KMP Wasm/JS 分发任务，同步资源至 `public/kmp-showcase/`，然后校验隐私资源并生成静态页面。首次构建需要 JDK、Android SDK 与 Gradle/Node 依赖下载条件；Kotlin 内部 Yarn 与根 pnpm workspace 的检查冲突由构建脚本局部处理。

开发服务使用 Astro 后台模式；`dev`、`check` 和 `build:site` 会先校验并同步隐私资源。通过 `pnpm --filter web exec astro dev status`、`astro dev logs`、`astro dev stop` 管理。纯官网修改已有 KMP 产物时可运行 `pnpm --filter web run build:site`；生产交付使用完整 `build`。静态产物位于本目录 `dist/`，本轮不部署。

## 验证

```bash
pnpm --filter web test
pnpm --filter web check
pnpm verify:architecture
pnpm verify:docs
pnpm verify:agent-assets
```

浏览器检查双语、明暗、窄屏、键盘、滚动联动、FAQ、减少动态效果及加载恢复。KMP 展示壳只包含平台空实现，不申请相机或后端授权；演示与 Android 实机验收分别记录。

## 维护

文案集中于 `src/data/zh.ts`、`en.ts`；隐私三句由脚本从共享 Compose 资源提取，并与产品隐私文档的中英文逐句校验。展示页面英文资源位于 App 的 `values-en`，需与默认资源覆盖相同的键和格式占位符。KMP 生成资源与隐私同步结果忽略提交。

Web 展示字体采用 Noto Sans CJK SC 的字符子集，重命名为 GazeKeep Showcase Sans，仅打入浏览器资源。OFL 许可随字体分发。更新展示字符时，使用已安装的 fonttools 执行 `python3 apps/web/scripts/subset-showcase-font.py /path/to/NotoSansCJK-Regular.ttc`，然后重新完整构建。

详细设计见[官网架构](../../docs/architecture/web/website.md)。
