# 开发规范

本文只维护当前有效的工具链、开发命令与门禁。执行边界见[代理工作流](agent-workflow.md)。

## 开始开发

- IDE 打开 `apps/app`，这是完整 Gradle 工程；APK 输出在 `apps/app/android/build/outputs/apk/`。
- 使用 JDK 21、Android SDK、Node >=22.12 和根 package.json 指定的 pnpm。安装依赖后按下面的初始化命令配置本地工具。
- 日常运行 `pnpm build`、`pnpm lint`、`pnpm test`；完整主机门禁使用 `pnpm --filter @gazekeep/app verify`，shared 纯逻辑测试另运行 `:shared:testAndroidHostTest`。
- 按[架构总览](architecture/overview.md#按要改的功能定位)定位代码，按[测试规范](testing.md)选择验证，按[文档索引](index.md)加载专题。

## 工具链

仓库使用 pnpm workspace 与 Turborepo：Android 完整工程在 `apps/app`，官网预留在 `apps/web`，仓库工具在 `scripts/repo-tools`。官网当前没有 package、框架或构建任务。工程职责见[架构总览](architecture/overview.md)。

Android 使用 Gradle wrapper、JDK 21（编译目标 JVM 11）和 AGP 内置 Kotlin。当前 Gradle 9.5.1、AGP 9.1.1、Kotlin 2.4.20，compile/targetSdk 37、minSdk 26；wrapper 与 Android 版本目录是真源，不在模块脚本写版本。

开发工具独立于 APK：Node >=22.12、pnpm 11.7.0；版本由根 package.json 与 pnpm-lock.yaml 锁定。脚本使用 JavaScript ESM，测试使用 Node 内置 node:test，不需要 Python、venv、Ruff 或 TS 编译器。ktlint 版本仍在 Android 版本目录。当前本地安装和 Hook 面向 Linux/POSIX，不宣称其他平台已验证。

```bash
pnpm install --frozen-lockfile
pnpm tools:setup
pnpm hooks:install
pnpm verify:hooks
```

安装显式执行，不在 postinstall 安装 Hook，不覆盖已有管理器或自定义 hooksPath。ktlint 下载校验 Maven SHA-256，校验成功才原子替换；Hook 不联网安装缺失工具。仓库 .npmrc 禁止运行前隐式安装依赖。

## 命令与任务边界

根级 pnpm 提供 build、lint、test、verify 及仓库工具入口。Turbo 负责任务编排，当前所有任务关闭缓存，远程缓存禁用；Gradle 自身缓存继续启用。Android SDK/JDK 环境显式透传。

```bash
pnpm build
pnpm lint
pnpm test
pnpm verify
pnpm verify:docs
pnpm verify:agent-assets
pnpm verify:architecture
pnpm verify:apk --apk apps/app/android/build/outputs/apk/debug/android-debug.apk
pnpm test:tools
pnpm format:check
```

build 为 Android Debug 构建，lint 为 Android lint 和仓库 Node 语法检查，test 为现有 Android 主机测试与 Node 工具测试。verify 同时执行工具门禁与 Android 综合门禁，Android 使用一次 Gradle 调用。不要并发启动多个修改同一 Android 输出目录的 Gradle 任务；需要完整验证时使用 verify。

原始 Gradle 命令在 Android 目录执行，根级不保留 Gradle wrapper。也可从仓库根定向执行：

```bash
pnpm --filter @gazekeep/app exec ./gradlew :core:allTests
pnpm --filter @gazekeep/app verify
```

APK 位于 apps/app/android/build/outputs/apk/debug/。local.properties 和签名留在本地，不提交；IDE 打开 apps/app 作为 Gradle 工程。安装工具、安装 Hook、格式化直接调用 Node，不经过 Turbo。官网建立真实 package 后再注册其构建、开发与测试任务。

## 提交与格式

标题格式为 type(scope): emoji 中文描述。type 为 feat/fix/docs/style/refactor/perf/test/chore；建议 emoji 为 ✨/🐛/📝/🎨/♻️/⚡️/✅/🔨，subject 以 emoji 和空格开头并含中文。

scope 从 Android settings 的 Gradle 模块派生（冒号改为连字符），另有 docs/dev-workflow/ci-cd/release/deps/web。唯一提交配置为 scripts/repo-tools/config/commitlint.config.cjs；根 pnpm verify:commits 和 commit-msg 使用显式配置路径。默认不重写历史，Git 自动生成的合并/回退信息沿用 commitlint 默认忽略规则。

Lefthook 使用[官方入口配置](https://lefthook.dev/configuration/lefthook/)锁定根 node_modules 中的本地版本，不回退全局版本。pre-commit 仅对暂存文件运行 ktlint、Prettier、TOML 解析和 JS 语法检查；TS/TSX/JSX 使用 Prettier 解析。XML 保持 strict 空白语义，stage_fixed 更新格式结果；部分暂存隐藏与恢复由 Lefthook 负责。冲突与工具缺失阻断并保留恢复材料。

源码格式为 UTF-8、LF、空格缩进 2、行宽 120。Kotlin 与 Gradle Kotlin DSL 由 ktlint 按根 `.editorconfig` 执行；Markdown、JSON、YAML、JavaScript 与 XML 由 Prettier 执行。`proseWrap` 保持 `preserve`，不重排既有段落。

Prettier 配置在工具目录，Hook 使用显式路径，不向官网根级散落配置。完整构建、测试和实机验收不放进提交 Hook。format:check/format 是显式全仓工具，锁文件、wrapper 和生成物排除；日常只格式化本次文件，不制造无关源码 diff。

## 验证与发布边界

按[测试规范](testing.md)选择模块验证；目录迁移使用完整主机门禁，普通文档/工具任务不无理由执行全部 Android 测试。Node 工具测试包含真实临时 Git 提交、部分暂存冲突恢复、XML 文本与隐私文案、安装校验和仓库检查；不在真实索引中制造测试提交。

改 Android 依赖仍检查合并 Manifest 与 APK 权限，开发依赖不能进入 APK。Release 已启用 R8 与资源压缩；发布以[发布规范](release.md)为准，Debug 构建不代表 Release 或实机验收。

## APK 静态门禁

`pnpm verify:apk --apk <APK路径> [--root <仓库路径>]` 读取实际 APK，依赖 PATH 中 SDK `apkanalyzer` 与 ANDROID_HOME/ANDROID_SDK_ROOT 内稳定 build-tools 的 aapt2，不安装工具。检查最终双入口、最低/目标 API 102、各自作用域、关闭热重载、中文简介、版本、无参反射入口、禁止权限及 compileOnly 类未打包；图标引用与位图内容须匹配当前品牌资源，支持 R8 重命名资源路径以及 aapt2 的 RGB/RGBA 重编码、过滤和完全透明像素归零；可见颜色、透明度和尺寸仍严格比较。PNG 校验限定当前非交错 8 位 RGB/RGBA，其他格式或 CRC 损坏明确失败。门禁是产物检查，不代表框架或 ROM 已验收。

模块入口功能更新为 1.1.0（versionCode=2）；后续交付保持 versionCode 递增，避免同版本覆盖安装复用管理器图标缓存。Debug 与压缩 Release 分别运行 APK 门禁，不以源码声明代替产物检查。
