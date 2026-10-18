# GazeKeep 官网 (`../../../apps/web`) 完整架构与视觉规划文档

> **项目仓库**：`https://github.com/IceTeacher/GazeKeep`
> **目标路径**：`../../../apps/web`（纳入根目录 `pnpm-workspace.yaml` 统一管理）
> **视觉对标**：`andromeld.catchingnow.com`（滚动视差设备叙事） + `mc.rainafter.cn`（结构化特性卡片与布局基因）
> **核心技术栈**：纯血 Astro 7 (零 `.tsx` / 原生 TS) + `shader-effects-inc/shaders` (`shaders/js`) + GSAP `ScrollTrigger` + 原生 Scroll Snap + Tailwind CSS v4 + Compose Multiplatform Web (`wasmJs` + `miuix`)

---

## 一、 项目背景与核心目标

### 当前落地约定

官网保留 Astro 静态双语输出，在 `core/shared` 增加 Wasm 与 JS 浏览器目标；展示入口位于 shared 的 Web source set，复用真实页面与 controller/platform 契约，不连接相机或后端。桥接导出 `setScreen`、`setDemoStatus`、`setLocaleAndTheme`，首帧挂载后发送 `gazekeep:ready`，前端重放最新状态。构建按 KMP → 资源同步 → Astro 执行，生成资源忽略提交。默认无统计服务，不启用 Partytown。本轮只本地预览。性能数字为测量目标，文案以产品定义与隐私真源为准。

内屏使用同源 iframe 隔离 KMP Wasm/JS 加载与生命周期，内部保持单 Canvas，前端只挂载一个 iframe。Wasm 能力不足或启动失败时选择 JS，最终失败提供重试；`load` 事件只表示文档加载，实际就绪以 Compose 首帧与字体加载完成后的通知为准。浏览器语言设置仅作用于展示 iframe。手机外壳与叙事使用同一实例。外壳保持 18:9（高:宽），KMP 使用固定 392×784 逻辑视口，通过展示容器尺寸等比缩放，不随外壳宽度缩小排版视口。桌面保留跟随展示；移动端设备随正文自然滚动，不设置顶部固定展示区和遮罩。

展示状态只通过真实页面、外壳光效与机外说明表达，不额外在 App 上方插入状态文字栏。状态栏采用同主题前景色，依次显示时钟、Wi-Fi、移动信号和电池；摄像头采样环按顺时针先慢后快旋转。示例应用在 Web 平台适配层绘制阅读、浏览器、文档三类图标。内屏边缘通过同色底、裁剪与独立合成处理，避免 3D 倾斜时出现矩形接缝。

滚动仅在首屏、工作流、后端与白名单功能展示区采用 CSS Scroll Snap 停靠点，按视口补齐长区块的中间屏；从“目光留下。画面不留。”隐私模块起恢复自由滚动和内容自然高度，向上返回功能展示区时恢复吸附；滚轮/触控板惯性在一次手势内仅触发一个停靠点，触屏纵向滑动、PageUp/PageDown、方向键和锚点导航共用吸附逻辑。过渡期间使用 780ms 缓入缓出，暂停原生吸附以避免争抢；到位后恢复原生停靠。替换 Lenis 的自由平滑滚动，保留 GSAP 的手机姿态联动。短视口、FAQ 展开和尺寸变化重新计算停靠点，不跳过正文；减少动态效果时立即到位。Shader 保留现有颜色，使用更明显的噪声演化和渐变方向往返；CSS 背景同色动态降级，离屏、后台和减少动态效果时暂停。

Web 字体使用带 OFL 许可的 Noto CJK 字符子集，位于 showcaseMain，仅用于网页展示。官网构建过滤 source map；KMP 生成目录排除在 Astro 类型检查之外。

### 1.1 核心定位

为 **GazeKeep**（专为缺少系统级“注视不熄屏”的 Android 设备打造的端侧离线注视保亮工具）构建兼具**产品功能叙事**与**高规格前端视觉表现力**的静态官方网站。

### 1.2 核心架构约束

1. **首要目的（功能展示）**：清晰传达 GazeKeep 的核心工作流（临近熄屏前短时采样、检测到注视自动续命、看不清默认熄屏）、三种工作后端（LSPosed / Shizuku / 无障碍）、按应用白名单与超时保护上限、以及“零网络权限、纯端侧离线”的隐私承诺。
2. **次要目的（前端炫技）**：
   - 首屏采用 `https://github.com/shader-effects-inc/shaders` 构建 WebGPU 动态交互背景；
   - 滚动视差动画融入手机设备三维翻转、跟随展示与光学边框反馈；
   - **纯前端绘制手机边框 + KMP Web 实时渲染内屏**：手机外壳由纯 CSS/SVG 绘制，屏幕内部拒绝静态截图，直接挂载 GazeKeep `shared` 模块编译出的 KMP Web (`wasmJs`) 画布，实时渲染真实的 `miuix` UI（屏蔽指针交互，仅由页面滚动和外部卡片触发内部路由与状态切换）。
3. **无 CMS 与纯 SSG 构建**：不使用任何内容管理系统（CMS），不开启服务端渲染（SSR），100% 通过本地 TypeScript 强类型数据源文件（`src/data/{zh,en}.ts`）在构建期生成纯静态 HTML。
4. **国际化支持（i18n）**：支持中文（`/zh/`）与英文（`/en/`）双语静态路由构建，且与 `../../../apps/app/shared` 中的 `strings.xml` 保持单一真源对齐。

---

## 二、 最佳技术选型与架构决策

### 2.1 技术选型总表

| 架构维度             | 最终选型                                                      | 选型理由与工程收益                                                                                                                                                                                                                                                                                                |
| :------------------- | :------------------------------------------------------------ | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **底层 SSG 框架**    | **纯血 Astro 7 (v7.4+)**<br>_(严禁混用 React `.tsx`)_         | 1. **0 KB 前端框架运行时**：彻底抛弃 `.tsx` 孤岛水合，避免 42KB+ React 运行时税与跨孤岛通信割裂。<br>2. **极致构建性能**：基于 Vite 8 (Rolldown) + Rust 编译器 + Queued Rendering（队列渲染提速 2.4 倍）。<br>3. **原生 i18n 静态路由**：内置 `i18n` 路由配置，配合 TS 数据源直接输出 `/zh/` 与 `/en/` 静态产物。 |
| **首屏背景引擎**     | **`shaders/js`**<br>_(`shader-effects-inc/shaders`)_          | 使用官方一等公民原生 JS/TS 入口 `createShader`，无需包裹 React 组件即可直接驱动 WebGPU 管线；针对不支持 WebGPU 的浏览器提供纯 CSS 径向渐变平滑降级。                                                                                                                                                              |
| **滚动视差与动画**   | **GSAP (`ScrollTrigger`) + Scroll Snap**                      | Scroll Snap 提供逐屏停靠和手势边界，GSAP `ScrollTrigger` 控制手机壳 3D 姿态；`IntersectionObserver` 根据可见区块向 KMP 展示页发送路由和状态。                                                                                                                                                                     |
| **样式与手机壳绘制** | **Tailwind CSS v4 + 纯 CSS/SVG**                              | 利用多层 `box-shadow`、`conic-gradient`（钛金属高光边框）、`backdrop-filter` 与 SVG 滤镜绘制旗舰级 Android 手机外壳（含前置挖孔传感器脉冲灯效、微凸实体按键、屏幕 R 角防锯齿遮罩）。                                                                                                                              |
| **手机内屏渲染**     | **Compose Multiplatform Web**<br>_(`wasmJs` 优先，`js` 兜底)_ | 复用 `../../../apps/app/shared` 下基于 `miuix` 的 `commonMain` UI 代码，注入轻量级 Mock 状态契约，通过同源 iframe 的 `window.GazeKeepWebShowcaseBridge` 供前端原生 TS 调度。                                                                                                                                      |

### 2.2 为什么坚持“纯血 Astro 7（无 `.tsx`）”而非混合孤岛？

在包含 **WebGPU Shader + 全程跟随滚动的手机外壳 + KMP WASM 画布 + 交互式后端卡片** 的连续状态机页面中：

- 若拆分为多个 React `.tsx` 孤岛，跨模块联动（如点击第 3 模块的 Shizuku 卡片去切换第 2 模块固定的 KMP 手机画布）需要绕道全局事件总线，且仍需付出完整 React 运行时体积；
- WebGPU、Skiko WASM 和 GSAP 均为**命令式（Imperative）**操作 DOM 与 GPU 上下文，原生 Astro `<script>`（由 Vite 8 直接打包的 TypeScript 模块）能以零中间层开销直接调度它们，彻底规避 React 生命周期重渲染带来的 Canvas 闪烁或上下文重建。

### 2.3 GPU 资源与错峰挂载策略

1. **首屏与内屏的 GPU 算力接力 (`IntersectionObserver`)**：
   - 当用户位于首屏时，`shaders/js` 满帧运行；
   - 当用户向下滚动离开首屏时，`IntersectionObserver` 立即调用 `shaderInstance.pause()` 挂起首屏 WebGPU 渲染循环，减少首屏渲染与 KMP 手机内屏、视差动画的竞争。
2. **KMP WASM 错峰懒加载 (`requestIdleCallback`)**：
   - 首屏加载瞬间，手机屏幕内先展示与 `miuix` 浅色/深色背景一致的极简 SVG 骨架屏；
   - 通过 `requestIdleCallback` 在首屏渲染完成后的空闲窗口异步拉起 Skiko/KMP WASM 脚本，降低对首屏加载的影响。
3. **全站单实例 KMP Canvas 复用**：
   - 全站仅维护 **1 个** KMP 展示 iframe，设置外层容器 `pointer-events: none`（屏蔽鼠标点击与滚轮捕获，防止干扰网页滚动），所有模块的界面变化均通过 JS 桥接函数在同一实例内切换路由与状态。
4. **不启用 `COOP/COEP` 跨源隔离头**：
   - 保持标准静态托管响应头，避免开启 `Cross-Origin-Embedder-Policy: require-corp` 导致 GitHub Star 徽章、外部头像等第三方图片资源被浏览器拦截。

---

## 三、 全站 7 大模块与前端视觉效果规划

### 模块 1：Hero 首屏 —— 「目光所及，屏幕常明」 (The Gaze-Reactive Hero)

- **核心文案**：
  - 主标题（ZH）：_目光所及，屏幕常明_
  - 主标题（EN）：_Screen Stays Awake, As Long As You Look_
  - 副标题：专为缺少系统级“注视不熄屏”的 Android 设备打造的端侧离线注视保亮工具——临近熄屏前短时采样，看不清默认熄屏。
  - 信任徽章（Pills）：`100% 端侧离线` · `零网络权限` · `临近熄屏短时采样` · `三后端支持`
  - 行动按钮（CTA）：`下载最新 APK (GitHub Releases)` · `查看源码` · `向下探索工作流`
- **前端炫技效果**：
  - **WebGPU 背景 (`shaders/js`)**：深邃暗夜蓝紫底色流体（`LinearGradient` + `SimplexNoise`）叠加鼠标轨迹光学折射层（`CursorTrail`）。鼠标移动时背景如同“瞳孔对焦”一般产生柔和光晕跟随，呼应“注视检测（GazeKeep）”主题。
  - **手机半露入场**：首屏底部中央露出 45% 的纯 CSS 3D 旗舰手机上半身，前置挖孔摄像头四周带有呼吸态翠绿色微光环，手机内屏 KMP Web 实时渲染 `Home` 页（总开关开启、状态就绪）。

---

### 模块 2：核心工作流视差叙事 —— 「聪明采样，克制唤醒」 (Scrollytelling Phone Showcase)

> **参考对标**：`andromeld.catchingnow.com` 核心滚屏手机交互区。左侧为分步滚动文案，右侧（移动端跟随正文）为 Sticky 固定的纯 CSS 手机外壳 + KMP Web 实时状态联动。

| 叙事阶段                            | 展示内容与核心卖点                                                                           | 手机外壳纯前端光学效果 (`PhoneFrame.astro`)                                                                 | 手机内屏 KMP Web 状态 (`GazeKeepWebShowcaseBridge`)                                      |
| :---------------------------------- | :------------------------------------------------------------------------------------------- | :---------------------------------------------------------------------------------------------------------- | :--------------------------------------------------------------------------------------- |
| **Stage 2.1**<br>临近熄屏短时采样   | 拒绝 24 小时后台常驻开相机耗电。仅在系统即将熄屏前的短暂窗口期唤醒前置相机进行短时采样。     | 前置挖孔传感器触发 **翠绿色脉冲波纹扩散动效** (`camera-pulse`)，机身从 `rotateX(16deg)` 平滑扶正为 `0deg`。 | 路由：`home`<br>状态：`sampling`（显示“临近熄屏 · 正在采样检测”动态指示器）              |
| **Stage 2.2**<br>识别注视，延后熄屏 | 识别到人脸注视后立即重置系统亮屏计时器。阅读长文、查阅代码、看菜谱时再也不用频繁伸手戳屏幕。 | 手机钛金属边框四周泛起 **柔和青碧色环境辉光** (`screen-glow`)，屏幕亮度视觉拉满。                           | 路由：`home`<br>状态：`gaze_detected`（显示“已识别注视 · 亮屏时间已顺延”及最近检测摘要） |
| **Stage 2.3**<br>看不清？默认熄屏   | 手机放进口袋、倒扣桌面、逆光或纯黑环境下看不清时，绝不死扛亮屏，安全回退至系统默认熄屏策略。 | 手机内屏边缘浮现一层 **CSS 径向暗角与亮度衰减遮罩** (`screen-dim`)，模拟优雅进入熄屏预备态。                | 路由：`privacy`<br>状态：`dim_fallback`（展示真实隐私承诺页中的正常熄屏策略）            |

---

### 模块 3：三后端架构矩阵 —— 「无论 Root 与否，皆可优雅运行」 (Tri-Backend Matrix)

> **参考对标**：结合 `andromeld.catchingnow.com` 的多连接方式切换与 `mc.rainafter.cn` 的结构化特性卡片区。

- **展示内容**：对比 GazeKeep 支持的三种运行后端：
  1. **LSPosed 模式**：系统级 Xposed 模块钩子深度集成，接入系统熄屏时序（适合已 Root 玩家）。
  2. **Shizuku 模式**：免 Root 借用 ADB / 无线调试高权限接口，兼顾高可靠性与系统纯净度（适合进阶极客）。
  3. **无障碍（Accessibility）模式**：零门槛开箱即用，无需电脑与命令行折腾，普通用户安装即可启用。
- **前端炫技效果**：
  - **鼠标跟随聚光灯卡片 (Spotlight Cards)**：三张卡片支持鼠标移动时的径向高光边框折射（纯 CSS 变量 `--mouse-x` / `--mouse-y` 驱动）。
  - **卡片与 KMP 手机画布双向联动**：当用户点击或悬停任意一张后端卡片时，固定在视口一侧的 KMP Web 手机内屏立即切换到 `Backend` 页面，并自动高亮选中对应的后端模式（`lsposed` / `shizuku` / `accessibility`），实时展示对应的环境检测状态。

---

### 模块 4：精细化控制与白名单 —— 「只在你需要的应用里生效」 (Smart Whitelist & Limits)

- **展示内容**：
  - **按应用白名单启用**：在阅读器、浏览器、PDF 文档、终端 App 中开启注视保亮；在视频播放器、游戏或锁屏下自动静默。
  - **保护上限防彻夜亮屏**：支持设置单次最长连续保亮上限，防止夜间躺在床上看手机睡着后屏幕常亮到天亮。
- **前端炫技效果**：
  - **视差环绕应用图标轨 (Orbit / Marquee Stream)**：手机模型居中，背景层有多条水平/弧形视差轨道漂浮常见类别图标（阅读、文档、代码、漫画、浏览器）。当图标滑入手机检测区时触发高亮描边与“已纳入白名单”微标签；同时手机内屏 KMP Web 切换至 `Whitelist / Settings` 界面展示应用勾选列表。

---

### 模块 5：隐私与安全宣言 —— 「零网络权限的绝对坦诚」 (Zero-Network Privacy Vault)

- **展示内容**（严格对齐仓库 `docs/product/privacy-security.md` 与 `strings.xml` 的 `privacy_line_1 ~ 3`）：
  1. **应用无网络权限**：`AndroidManifest.xml` 中不包含 `android.permission.INTERNET`，应用本身不持有网络访问权限。
  2. **画面仅在内存判断**：前置相机帧在分析回调内处理，不落盘、不上传。
  3. **源码可查阅**：在 GitHub 提供源码与构建脚本，权限清单以实际构建产物为准。
- **前端炫技效果**：
  - **Bento Grid 便当盒布局**：
    - 左侧大卡片：展示带动态扫描线的 `AndroidManifest.xml` 权限对比代码块，其中 `<uses-permission android:name="android.permission.INTERNET" />` 带有醒目的红色删除线与 `[REMOVED AT BUILD]` 徽章。
    - 右侧卡片：展示从 `../../../apps/app/shared` 自动同步过来的三条黄金隐私准则（`privacy_line_1~3`），配合 CSS 3D 玻璃态安全盾牌动效。

---

### 模块 6：快速上手与常见问题 (Quick Start & Interactive FAQ)

- **展示内容**：
  - **3 步极速上手**：① 下载安装 APK -> ② 选择工作后端（无障碍 / Shizuku / LSPosed）并授予相机权限 -> ③ 按需勾选白名单应用并设置保护上限。
  - **精选 FAQ**（同步自 `docs/usage.md`）：解答耗电表现、暗光/戴眼镜识别率、后台保活设置、多后端切换优先级等核心疑问。
- **前端炫技效果**：
  - 步骤卡片之间带有随滚动填充进度的 SVG 发光连接线；
  - FAQ 采用基于 CSS `grid-template-rows: 0fr -> 1fr` 的物理阻尼平滑展开面板。

---

### 模块 7：极简致敬页脚与开源生态 (Footer & Open Source CTA)

- **展示内容**：GitHub Releases 下载直链、源码仓库入口、开源协议声明、文档链接、中英文切换开关（`/zh/` <-> `/en/`）及深浅色主题切换器（同步改变网页与 KMP 手机内屏的 `miuix` 深浅色模式）。

---

## 四、 Monorepo 目录结构与纯 TS 数据源工程设计

### 4.1 当前目录与数据流

`apps/web/src/components/Website.astro` 生成双语页面；`src/data/schema.ts` 约束 `zh.ts`、`en.ts` 的内容及演示路由。`src/styles/global.css` 实现布局、手机外壳和降级背景，`src/scripts/orchestrator.ts` 管理 Shader、GSAP、可见区块观察器和 KMP 桥接，`scroll-snap.ts` 与 `snap-policy.mjs` 管理功能区滚动。`scripts/build.mjs` 先构建 shared 的 Wasm/JS 展示资源，`sync-kmp-strings.mjs` 从 Compose 资源生成双语隐私文案，随后 Astro 静态构建；`dev` 和 `check` 也先同步隐私文案。

### 4.2 KMP 展示桥接

`apps/app/shared/src/showcaseMain/kotlin/.../Showcase.kt` 提供固定演示数据、路由和状态；JS/Wasm 平台入口把 `setScreen`、`setDemoStatus`、`setLocaleAndTheme` 挂在同源 iframe 的 `window.GazeKeepWebShowcaseBridge`。父页面等字体及首帧就绪事件后重放最新状态。每次 iframe 加载带独立尝试编号；旧加载的就绪或错误事件不会改变新加载状态，重复错误只处理一次。Wasm 加载失败转 JS，最终失败显示重试入口。采样时主页显示“检测中”，看不清阶段展示隐私承诺页；后端演示状态只在后端区锁定选项，其余阶段维持自动选择。

### 4.3 手机外壳与视觉调度

手机外壳由 `Website.astro` 与 `global.css` 绘制，内屏为一个 KMP 展示 iframe。`orchestrator.ts` 用 `IntersectionObserver` 选择叙事步骤和页面路由，GSAP 负责机身姿态。首屏 Shader 在离屏、页面隐藏或减少动态效果时暂停；不支持 WebGPU 或创建失败时由 CSS 背景降级。功能区滚动停靠及其后自然滚动的边界由 `scroll-snap.ts` 控制。

---

## 六、当前构建与后续验收

本轮已完成双语数据、七模块页面、CSS/SVG 设备、Shader/GSAP/Scroll Snap 调度、真实 miuix Wasm/JS 展示壳、语言/主题桥接、隐私资源校验和加载失败恢复。构建与维护命令见 [官网 README](../../../apps/web/README.md)，对应环境和验收结果见[任务清单](../../plans/todo.md#官网本次验证2026-10-18)。

后续验证集中于 WebGPU 实际渲染、120Hz 硬件表现、跨浏览器矩阵和 KMP 资源体积。静态托管部署与 CI/CD 留待上线任务配置；官网完成后再评估是否接入统计脚本及其隐私要求。本轮不集成统计脚本或 Partytown。
