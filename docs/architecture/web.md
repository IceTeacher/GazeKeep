# 官网与 KMP Wasm 展示架构 (`apps/web`)

GazeKeep 官网采用纯静态双语构建（`/zh/`、`/en/`），将产品工作流叙事、Three.js 实体手机三维交互与复用真实 `apps/app/shared` 代码编译的 Compose Multiplatform Web（`wasmJs` 优先，`js` 兜底）内屏实时演示融合在同一单页状态机中。

## 核心技术选型与单实例架构

| 架构层           | 技术方案                                               | 关键设计取舍                                                                                                    |
| ---------------- | ------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------- |
| **SSG 框架**     | **纯 Astro 7 + 原生 TypeScript**（严禁混用 React）     | 零前端框架水合运行时开销；WebGPU、Three.js 与 KMP Wasm 均属命令式 GPU/DOM 调度，由原生 TS 模块直接驱动          |
| **首屏背景**     | **`shaders/js`**（WebGPU）+ CSS 径向渐变降级           | 随深浅主题切换光场与指针折射；`IntersectionObserver` 在滚出首屏、后台或减少动态时立即 `pause()` 让出 GPU 算力   |
| **手机外壳**     | **Three.js 实体机身 + 单应矩阵（Homography）DOM 投影** | 统一逻辑屏幕 `392×848`（含状态栏 `40`、应用区 `784`、导航条 `24`）推导机身 `416×872×40`；WebGL 不可用时降级 CSS |
| **手机内屏渲染** | **同构 DOM 主页预览 → 单实例 KMP Wasm/JS Iframe 接管** | 首帧零等待展示与真源词条/版本号/色板一致的同构 DOM 主页，KMP 首帧 `gazekeep:rendered` 绘出后 150ms 平滑淡出接管 |
| **样式系统**     | **Tailwind CSS v4 + `tokens.css` 语义变量**            | `tailwind.css` 作为 `oxfmt` 类名排序唯一入口；品牌绿 `#14795e` 明暗共用，切换主题仅改变表面与背景色令牌         |

### KMP 自适应挂载与包体裁剪

- **网络感知错峰挂载**：`orchestrator.ts` 在高速或未知网络下于首帧机身绘出后经 `requestIdleCallback`（上限 700ms，或交互提前触发）挂载 KMP iframe；检测到 `Save-Data` 或 `2g`/`3g` 慢网且位于首屏顶部时，延迟至用户点击内屏、向下滚动（`scrollY > 24`）或点击演示卡片时按需挂载。
- **编译器级死代码消除（DCE）**：`shared` 的 `ShowcaseNavHost` 仅链接官网可达的 5 个页面（`Home` / `Backend` / `Apps` / `Settings` / `Privacy`），裁除 `SelfTest`、`Logs`、`About`、`Notices` 与正则依赖；关闭 Compose `includeSourceInformation` 与 `includeTraceMarkers`；`scripts/build.ts` 进一步过滤未引用的重复 `skiko.wasm`、source map 与无用图标，并为入口生成内容指纹 `shared.<hash>.js`。
- **Webpack 公共路径锁定**：`shared/webpack.config.d/public-path.js` 强制 `publicPath = "./"`，防止 module 脚本因 `currentScript` 为空而误取页尾第三方注入脚本的外部域名加载 `.wasm`。

## 连续滚动叙事与内屏交互桥接

### 滚动吸附与姿态状态机

- **原生滚动驱动**：不劫持滚轮或触摸，`story-scroll.ts` 与 `story-motion.ts` 以页面滚动进度为唯一时钟，正反方向共享同一插值映射，驱动手机三轴姿态、单色 SVG 持机手部（前景四指/掌根分区避让主要状态卡片）、区段文案透明度与内屏演示阶段。
- **分层 Scroll Snap**：桌面非紧凑布局在首屏、工作流标题、三幕叙事、运行方式、专注范围与设置区使用强制停靠（`scroll-snap-stop: always`），隐私区至页脚包裹在同一高于视口的吸附容器内实现自由连续滚动；移动端非紧凑布局仅对三幕叙事中的三个零高标记逐幕强制吸附；紧凑布局、减少动态与 `/[lang]/changelog/` 子页关闭吸附。

### 逆单应指针转发与自动演示脚本

- **透明命中层与移动端长按分流（`screen-touch.ts`）**：
  - 全站唯一的 `#kmp-frame` 保持 `inert` 与 `pointer-events: none`；上方覆盖随机身投影变形的 `#screen-touch`（`touch-action: pan-y pinch-zoom`），使滚轮与快速竖向滑动直接交给浏览器滚动页面。
  - 指针坐标经投影四角逆单应矩阵换算为 `392×784` 应用坐标（直接写入合成事件的 `offsetX`/`offsetY`，规避 Android Chrome 对 iframe 内合成事件重复除以 DPR 的偏差），以触摸类型转发进 `ComposeViewport` shadow root 画布。
  - 鼠标按下即刻转发并支持拖动滚动；触屏需**按住不动满 `250ms`** 才将按压交给内屏并拦截后续 `touchmove` 滚动内屏（兼顾快速划页与 Android Firefox 约 `500ms` 长按中断触摸流的窗口，长按菜单后 600ms 无后续事件自动补发 `pointercancel`）。
- **内屏自动演示与访客复位（`ShowcaseDirector`）**：
  - 各区段映射对应演示场景（工作流标题 `tour`、第一幕 `detect`、第二幕 `privacy`、后端区 `backends`、范围区 `apps`、设置区 `settings`）。`ShowcaseDirector` 读取 `shared/ui/showcase/ShowcaseScripts.kt` 中的纯数据脚本，以独立指针编号派发合成触点并驱动顶栏嵌套滚动折叠；设置区演示与页面右侧四张刻度环卡片实时双向同步。
  - 访客点按或拖动内屏后暂停当前自动脚本；当页面再次滚动超过 `24px` 或点击外部卡片时，调用 `setScreen(route, revision, true)` 丢弃临时改动并以返回转场复位至当前段落基线。
- **原地无刷语言与主题切换**：`locale-switch.ts` 复用 `reveal-transition.ts` 的圆形揭示视图过渡，原地更新 DOM 文案、`meta`/OG、URL（`history.pushState`）与 KMP 内屏语言，保活首屏 WebGPU 与 KMP iframe 实例；更新日志页直接构建期解析根目录 [docs/CHANGELOG.md](../CHANGELOG.md) 与 [docs/CHANGELOG.en.md](../CHANGELOG.en.md)。

## 构建优化、EdgeOne 部署与自建统计

- **Shader 注册表按需裁剪**：`scripts/build-performance.ts` 在打包期将 `shaders/js` 组件注册表裁剪为 `hero-shader.ts` 实际使用的图层；升级 `shaders` 导致结构变化时直接报错阻断构建，防止静默退回全量打包。
- **EdgeOne Makers 安全打包与直传**：
  - 完整构建由 GitHub Actions 完成，`scripts/package-edgeone.ts` 调用锁定版本的 `edgeone` CLI 生成 `.edgeone/`。
  - **环境变量隔离**：`edgeone makers build` 未传 `ENV_STR` 时会将整个宿主机环境变量打入边缘函数包；脚本强制传入空 `ENV_STR` 且子进程仅保留 `PATH`/`HOME`/`TMPDIR`/`CI`，并在上传前扫描产物确保不含任何父进程环境变量值及 `.env` 文件。
  - 部署使用 API Token（`-t`）直传并内置最多 3 次整体重试，发布后由 `check-web-deploy.ts` 携带临时令牌 Cookie 冒烟校验 `/`（302 至 `/zh/`）、`/zh/`、`/en/` 与 KMP 展示目录。
- **同站代理自建统计**：仅针对官网（App 零网络承诺不变）。页面以 `async` + `fetchpriority="low"` 加载 `/stats/script.js` 与 `/stats/recorder.js`，由边缘函数 `edge-functions/stats/[[path]].ts` 剥离官网 Cookie、将 `request.eo.clientIp` 写入 `X-Forwarded-For` 后转发至自建 Umami 实例。
