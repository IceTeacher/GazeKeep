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

文案集中于 `src/data/zh.ts`、`en.ts`；隐私三句由脚本从共享 Compose 资源提取，并与产品隐私文档的中英文逐句校验。展示页面英文资源位于 App 的 `values-en`，需与默认资源覆盖相同的键和格式占位符。KMP 生成资源与隐私同步结果忽略提交。

Web 展示字体采用 Noto Sans CJK SC 的字符子集，重命名为 GazeKeep Showcase Sans，仅打入浏览器资源。OFL 许可随字体分发。更新展示字符时执行 `pnpm --filter web font:showcase /path/to/NotoSansCJK-Regular.ttc`（HarfBuzz hb-subset 子集化后改写 name 表），然后重新完整构建。

详细设计见[官网架构](../../docs/architecture/web/website.md)。

手部与保留摄影素材的来源、处理说明见 `public/media/credits.txt`。手机使用一个 KMP 内屏，统一逻辑尺寸 392×848（系统状态区 40、应用 784、导航区 24），通过机身相机投影整体缩放。

设备机身由屏幕与统一 12 单位边框推导为 416×872；外圆角 50、内圆角 38，四边及圆角边框等宽。

持机手部以用户实拍校正短拇指、虎口与腕掌连续轮廓，原始比例参考镜像的 unDraw Going live，参考控制点保留在 `grip-reference.ts`，轮廓统一维护于 `grip-geometry.ts`，由 `GripLayers.astro` 单次填充。掌部与指身采用后表面深度，指腹采用前表面深度；机身、玻璃和侧键的实际几何投影构成遮挡遮罩，屏幕采用分区保护：拇指不开放屏内覆盖，四指最多进入 4 个逻辑像素，掌根仅在屏高 45%–90% 的左侧最多进入 24 个逻辑像素。手腕曲线保持方向和曲率连续，拇指与掌部共用轮廓，四指没有独立色带。稳定阶段手机中心位于桌面视口 31%、移动端 47%，仅前臂延展到左侧边界外 24 CSS 像素。

滚动路由与设备姿态共用场景状态。展示桥接 `setScreen(route, revision?)` 接受可选请求编号，JS/Wasm 绘制后发送带加载尝试、路由和编号的 `gazekeep:rendered`；过期回执忽略。桌面手机由 CSS sticky 跟随并贯穿后端与专注范围；手机端三幕使用设备与正文分区的局部固定容器，后端与专注范围使用正常文档流设备槽。脚本不逐帧改写设备的文档纵坐标，避免慢速滚动回弹。短屏和减少动态保留连续正文。导航始终固定顶部，滚动后仅叠加模糊。
