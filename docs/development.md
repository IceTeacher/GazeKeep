# 开发、测试与发布规范

本文定义 GazeKeep 的本地开发环境、代码与提交规范、测试分层与必守不变量、CI 流水线及正式版发布门禁。

## 环境与常用命令

- **工具链真源**：JDK 21（Android 编译目标 JVM 11）、Android SDK（compile/targetSdk 37，minSdk 26）、Node >= 22.22 与根 `package.json` 锁定的 pnpm。Gradle 与 Kotlin/AGP/ktlint 版本唯一真源为 `apps/app/gradle/libs.versions.toml` 与 wrapper。
- **首次初始化**：
  ```bash
  pnpm install --frozen-lockfile
  pnpm tools:setup
  pnpm hooks:install
  pnpm verify:hooks
  ```
- **全仓与工程级门禁**：
  ```bash
  pnpm verify                              # 仓库工具门禁 + Android 综合门禁（单次 Gradle 调用）
  pnpm --filter @gazekeep/app verify       # Android 构建、lint、全部主机单测/UI 测试与 e2e 编译
  pnpm --filter web build                  # 完整构建官网（含 KMP Wasm/JS 展示包与隐私文案校验）
  pnpm --filter web test:unit              # 官网 Node 单元测试
  pnpm --filter web test:browser:docker    # 官网容器内真实 Chromium 交互与截图基线校验
  pnpm verify:docs                         # 全仓 Markdown 链接/锚点与双语 Changelog 结构检查
  pnpm verify:agent-assets                 # 核心 AGENTS.md 与 Skills（.agents ↔ .claude）同步检查
  pnpm verify:architecture                 # Android 十模块依赖方向、包名目录与越界 import 检查
  pnpm verify:lsposed-repo                 # LSPosed 模块仓库元数据与隐私真源一致性检查
  pnpm verify:apk --apk <APK> --abi <ABI>  # APK 静态门禁（权限、Xposed 登记、R8 保留、ABI 压缩）
  ```
  _注意：不要并发启动多个修改同一 Android 输出目录的 Gradle 任务。_

## 提交与代码格式

- **提交信息格式**：`type(scope): emoji 中文描述`（唯一配置：`scripts/repo-tools/config/commitlint.config.ts`）。
  - `type`：`feat` / `fix` / `docs` / `style` / `refactor` / `perf` / `test` / `chore`
  - `scope`：Android 模块名（冒号改连字符，如 `core`、`engine`、`shared`、`android`、`backend-shizuku`、`xposed`）或 `docs`、`dev-workflow`、`ci-cd`、`release`、`deps`、`web`。
  - 描述推荐采用「动词 + 名词 + 效果/目的」结构（如 `feat(android): ✨ 新增系统快捷设置磁贴以支持一键启停`）。
- **代码格式与静态检查**：UTF-8、LF、2 空格缩进、行宽 120。Kotlin 由 `ktlint` 按 `.editorconfig` 检查；TS/JS/Astro/CSS/HTML/MD/JSON/YAML 由根配置 `.oxfmtrc.json`（`oxfmt`）与 `.oxlintrc.json`（`oxlint`，警告即阻断）检查；XML 只验语法不格式化。仓库脚本使用 Node 原生类型剥离直接运行 `.ts`（只用可擦除语法，相对导入带 `.ts` 后缀）。Lefthook `pre-commit` 仅检查暂存文件。

## 测试策略与七条必守不变量

### 风险驱动验证原则

| 改动类型                         | 验证策略与停止条件                                                                     |
| -------------------------------- | -------------------------------------------------------------------------------------- |
| **文档、纯格式或无行为配置**     | 运行 `verify:docs`、`verify:agent-assets`、格式检查与 `git diff --check`；不跑应用构建 |
| **纯规则、状态机、解析、协议**   | **先写最小失败测试（Red-Green）**，确认旧实现失败后再修改代码，跑通受影响模块单测即停  |
| **UI 页面、视觉、滚动或截图**    | 定义用户可见场景，运行 `:shared:testAndroidHostTest` 或 `web test:browser:docker`      |
| **相机、传感器、ROM 或特权后端** | 主机单测隔离协作逻辑 + 实机套件 `test:device` 及下方实机矩阵；缺设备条件如实记录未执行 |

### 七条必守不变量（Invariants）

任何纯逻辑或协议改动必须由自动化测试覆盖以下不变量，评审绝不允许放宽断言：

1. **`Unknown` 不产生 `Extend`**（看不清、暗光、多脸、异常一律放行熄屏）。
2. **采帧期间真实触摸取消采集且不 `Extend`**。
3. **达到单次保亮上限（`MaxHold`）下一轮必 `Yield`**，且实机补证必须熄屏一次。
4. **`ScreenOff` 立即释放后端并停止采帧**，直到下一次 `ScreenOn`。
5. **多张人脸（`MultipleFaces`）绝不判为 `Looking`**。
6. **后端切换期间取消旧会话且不 `Extend`**。
7. **LSPosed 过期 `seq` 返回 `STALE` 且绝不调用系统 `userActivity`**。

## 测试分层与入口

| 层次         | 覆盖范围                                         | 框架与目录                                                                       | 执行命令                                                                 |
| ------------ | ------------------------------------------------ | -------------------------------------------------------------------------------- | ------------------------------------------------------------------------ |
| **Kotlin**   | `core`/`shared` 纯规则与各模块隔离协作           | `kotlin.test`、JUnit4、Robolectric；`commonTest`、`src/test`                     | `pnpm --filter @gazekeep/app test`                                       |
| **App UI**   | `miuix` 页面交互与 12 张 Roborazzi 截图基线      | Robolectric 4.17 原生图形、Compose UI、Roborazzi；`shared/src/androidHostTest`   | `pnpm --filter @gazekeep/app exec ./gradlew :shared:testAndroidHostTest` |
| **Web 单测** | 官网纯逻辑与构建脚本（目录镜像 `src`/`scripts`） | Vitest `unit` project；`apps/web/tests/{src,scripts,architecture}/`              | `pnpm --filter web test:unit`                                            |
| **Web 视觉** | 真实无头 Chromium 交互、滚动吸附与排版截图       | Vitest `browser` project + 官方锁定 Playwright 镜像；`apps/web/tests/scenarios/` | `pnpm --filter web test:browser:docker`（重录加 `--update`）             |
| **工具单测** | 文档、Hook、架构、APK 与 LSPosed 同步脚本        | `node:test` + 临时目录隔离 fixture；`scripts/repo-tools/tests/`                  | `pnpm test:tools`                                                        |
| **实机套件** | 独立进程 `com.android.test` 跨权限/强停/超时     | `androidx.test`、UiAutomator；`apps/app/e2e`                                     | `pnpm --filter @gazekeep/app test:device`（需先核对设备授权）            |

- **App 截图基线重录**：有意修改 UI 外观时运行 `./gradlew :shared:testAndroidHostTest -Pgazekeep.recordScreenshots=true` 并逐张核对 `apps/app/shared/src/androidHostTest/screenshots/`。
- **分类器/阈值改动硬指标**：正常光正视 10 次至少 9 次 `Looking`、转头 90° 十次均非 `Looking`、标准模式闭眼非 `Looking`，并覆盖眼镜、暗光、多脸与相机抢占。

## 实机完整验收矩阵

主机测试、fake 与构建通过不代表硬件兼容或实际熄屏；实机验收须记录代码/APK 标识、设备/ROM/Android、后端版本、系统超时与结果：

| 验收场景                              | 预期可观察结果                                                       |
| ------------------------------------- | -------------------------------------------------------------------- |
| 白名单内正视超过超时 / 转头离开       | 正视连续周期延后且不先降亮；转头或离开在本轮或下一轮按系统原超时熄屏 |
| 黑名单范围：名单内 / 名单外应用       | 名单内门控跳过（`Blacklisted`）不开相机；名单外正常检测              |
| 闭眼 >2 秒（标准模式）/ 多人 / 极暗   | 标准模式闭眼 `Away`；多人与极暗均为 `Unknown` 不保亮，连续暗光后退避 |
| 口袋、倒扣、省电模式、未充电低电量    | 门控跳过（`Pocket`/`FaceDown`/`PowerSaveMode`/`LowBattery`），熄屏   |
| 系统相机/扫码抢占、通话、视频自身保亮 | 立即让出摄像头不崩溃；通话与视频自身保亮期间不抢占系统逻辑           |
| 连续注视达到单次保亮上限（`MaxHold`） | 强制释放（尤其核对无障碍 `Hold` 窗口摘除）并真实熄屏一次             |
| 划掉任务、省电杀服务、撤销相机权限    | 发出限频异常通知或首页明确提示原因，释放资源并按原超时熄屏           |
| 无障碍关闭、后端切换或掉线            | 无 overlay 残留，切换期不 `Extend`，状态准确降级                     |
| Shizuku 重连与临时超时退路恢复        | `setprop debug.gazekeep.deny_user_activity 1` 下熄屏/关闭/重启均写回 |
| LSPosed API 102 / Legacy / 隐私指示器 | 管理器识别、重启桥接、旧 `seq` 拒绝；开启绿点过滤仅隐藏自身相机提示  |
| 页面导航、字体 1.3x、明暗、旋转、自检 | 布局无截断，自检离页/后台立即释放相机，冷启动配置回读一致            |

## 持续集成（CI/CD）与发布门禁

### 流水线拓扑（`.github/workflows/`）

1. **`pipeline.yml`**（PR、`dev`/`main` 推送，固定 `ubuntu-24.04`）：
   - `changes`（`ci-changes.ts`）：零外部依赖快速分析受影响范围，按 [android-modules.ts](../scripts/repo-tools/scripts/android-modules.ts) 模块依赖反向闭包生成精确 `app_tasks`，拆分官网静态/KMP/浏览器触发标记，并检测同一 Git tree 是否已由成功的 `push` 运行验证（`verified=true` 时跳过门禁构建；`main` 仍构建官网供生产发布）。
   - **五路并行门禁**（均仅依赖 `changes`）：
     - `repo`：并行执行 Hook、文档、Agent 资产、架构、LSPosed 元数据、`repo-tools` 单测（含 Turborepo 与 `ktlint` 缓存）、并行 `format:check` 及提交区间 `commitlint` + `git diff --check`。
     - `app`：仅执行受影响模块的 Gradle 构建、lint 与并行宿主测试，不拉起 JS/Wasm 工具链；仅改动 `:core` 或 KMP 构建配置时追加 `core-web-test`。
     - `web-quality`：启用 Turborepo 缓存并行执行官网 `lint`、`check` 与 `test:unit`，不依赖 JDK/Gradle。
     - `web-browser`：在锁定 Playwright Docker 容器中按 2 路 `shard` 并行运行浏览器与截图基线测试，不依赖 KMP 产物。
     - `web-build`：按输入哈希精确恢复 `public/kmp-showcase` 缓存；命中时跳过 JDK/Gradle/Kotlin-JS 直接由 Turborepo 缓存增量构建 `dist` 并上传 `web-dist`（仅 `dev`/`main` 推送写入共享缓存）。
   - `web-preview` / `web-production`：待以上全部门禁通过后下载 `web-dist`，经 `package:edgeone` 隔离环境变量打包后通过 `edgeone makers deploy` 直传（含最多 3 次重试）并执行 `check-web-deploy.ts` 线上冒烟。
2. **`release.yml`**（仅 `v*.*.*` tag 触发）：
   - `gate`：无安装直跑校验 `tag == v<versionName>`、提交位于 `main`、`docs/CHANGELOG.md` 与 `docs/CHANGELOG.en.md` 包含该版本非空章节，并等待 `main` 上同一提交的 `pipeline.yml` 成功。
   - `release-apk`：在受保护的 `release` 环境中注入正式签名密钥库（RSA 4096 `CN=GazeKeep`，SHA-256 指纹 `bbf8c24ed81ccf9856f290598a328e6046eff3a31c934423b4f51fe47514d94f`），构建 R8 压缩的四架构 Release APK（`arm64-v8a`、`armeabi-v7a`、`x86_64`、`universal`），并发执行各包原生 ZIP 解析 `verify:apk` 与 `apksigner` 指纹校验，提取 `docs/CHANGELOG.md` 对应版本章节并追加生成的「`## 安装包`」表格，发布为 GitHub Pre-release。
3. **`lsposed-repo.yml`**（GitHub Release 转为正式版 `released` 或手动指定正式版 tag 触发）：
   - 下载四个 APK 重新核对 SHA-256 与签名指纹，将 `apps/app/distribution/lsposed/{SCOPE,SOURCE_URL,SUMMARY}` 与由根 `README.md` 转换绝对链接生成的 `README.md` 推送至 `Xposed-Modules-Repo/cn.rainafter.app.gazekeep`，并以 `<versionCode>-<versionName>` 创建模块 Release（显式 `--draft=false` 触发官方 bot 索引）。

### 发布说明与 R8 打包规范

- **双语更新日志真源**：统一维护在 [docs/CHANGELOG.md](CHANGELOG.md) 与 [docs/CHANGELOG.en.md](CHANGELOG.en.md)，同时作为 GitHub Release 正文与官网 `/[lang]/changelog/` 的数据源。每个版本以 `# v<版本>` 一级标题起始，中文版依次包含 `## 更新日志` 与 `## 启用步骤`，英文版依次包含 `## Changelog` 与 `## Setup`；由 `verify:docs` 强制校验。
- **APK 静态与 R8 边界**：Release 启用 R8 full mode、资源压缩与原生库压缩（`useLegacyPackaging`）。应用 ProGuard 规则必须保留：ML Kit Registrar 无参构造器、`ModernHookFacade` 具名 `BeforeHooker`/`AfterHooker` 及其 `intercept(Chain)` 签名、`LegacyEntry`/`ModernEntry`/`ShellService` 反射入口，以及详细日志输出的引擎状态/门控/效果短类名。`hidden-api-stubs` 仅允许 `compileOnly`，严禁进入最终 DEX。
