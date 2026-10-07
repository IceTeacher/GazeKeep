# 开发规范

## 工具链与真源

Android 构建使用 Gradle wrapper、JDK 21（编译目标 JVM 11）、AGP 内置 Kotlin。当前 Gradle 9.5.1、AGP 9.1.1、Kotlin 2.4.20，compile/targetSdk 37、minSdk 26；wrapper 与 gradle/libs.versions.toml 是真源，不在模块脚本写版本。

开发工具独立于 APK：Node >=22.12、pnpm 11.7.0、Python >=3.11；本地安装路径不提交。Node 工具版本由 package.json/pnpm-lock.yaml 锁定；Ruff 在 requirements-dev.txt，ktlint 在版本目录。当前安装脚本面向 Linux/POSIX 环境，不宣称其他平台已验证。

```bash
pnpm install --frozen-lockfile
pnpm tools:setup
pnpm hooks:install
pnpm verify:hooks
```

仓库 .npmrc 禁止 pnpm 运行前隐式安装；commit-msg 直接调用已安装的本地 commitlint。安装操作显式执行，不在依赖 postinstall 偷装 Hook，不覆盖已有 Hook 配置；已有管理器冲突先报告具体配置。ktlint 下载校验 Maven 摘要；Hook 不联网安装缺失工具。

lefthook.yml 使用[官方的 lefthook 入口配置](https://lefthook.dev/configuration/lefthook/)强制调用本地锁定版本，避免 PATH 中旧全局版本抢占；本地工具缺失直接阻断，不回退其他安装方式。

## 提交与格式

标题格式为 type(scope): emoji 中文描述。type 为 feat/fix/docs/style/refactor/perf/test/chore；建议 emoji 为 ✨/🐛/📝/🎨/♻️/⚡️/✅/🔨，约束为 subject 以 emoji 与空格开头并含中文，映射仅作提示。

scope 从 Gradle 模块派生（冒号改为连字符，保留 androidApp），另有 docs/dev-workflow/ci-cd/release/deps；根 commitlint.config.cjs 为唯一格式配置。默认不重写历史；用户明确要求时先保存恢复引用，再核对逐提交的文件快照、作者、时间与父子关系。Git 自动生成的合并/回退信息沿用 commitlint 默认忽略规则。

Lefthook pre-commit 只对暂存文件运行 ktlint、Prettier（XML strict 空白语义）、Ruff、TOML 解析和 JS 语法检查，stage_fixed 更新格式化结果；部分暂存改动由 Lefthook 隐藏并恢复。冲突与工具缺失阻断提交，保留恢复材料，不通过跳过 Hook 掩盖失败。

完整构建、单测、lint 和实机验收不放进轻量 Hook。pnpm format:check/format 是显式全仓工具，归档与生成物排除；日常只格式化本次文件，不顺手制造全仓格式 diff。

## 本地质量门禁

```bash
pnpm verify:docs
pnpm verify:agent-assets
pnpm verify:architecture
pnpm test:tools
./gradlew :androidApp:assembleDebug :androidApp:lintDebug
```

按[测试规范](testing.md)选择模块测试；文档/工具任务不需要无理由执行全部 Android 测试。改 Android 依赖仍检查合并 Manifest 和 APK 权限，开发工具不能进入运行时依赖。

Release R8 与资源压缩当前未启用，配置仅保留混淆文件入口；发布前按[发布规范](release.md)完成验证，不能把 assembleDebug 当作 Release 验收。
