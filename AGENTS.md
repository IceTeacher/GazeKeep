# AGENTS.md

给在本仓库工作的编码代理（以及人）的说明。回答与提交说明用中文；代码标识符、日志 tag 用英文。

## 项目是什么

注视保亮 GazeKeep：系统即将按超时熄屏时，用前置摄像头取 1～3 帧判断用户是否仍在看屏幕；在看就重置一次超时，否则放行熄屏。帧不落盘、不上云。目标机是小米 12 Pro（HyperOS），三种保亮后端：LSPosed、Shizuku、无障碍。

必读文档（改动前先读相关章节，冲突时以这两份为准）：

- `docs/architecture/GazeKeep-规划文档.md`：产品范围、里程碑、风险。
- `docs/architecture/GazeKeep-架构设计.md`：模块、接口、状态机、清单、测试。下文的 `§x` 都指这份文档。

## 当前状态

仓库已完成从 M0 到 M4 的主要模块演进与实机测试。当前阶段是 **M4 完成与整体验收**：已落地前摄三帧注视检测（`engine`）、三种保亮后端（`backend:{a11y,shizuku,lsposed}`）、LSPosed 安全加固（`xposed`）、全量 miuix 设置页（`shared`）、DataStore 单一配置存储与分步自检向导，并在小米 12 Pro（HyperOS）上完成了实机测试。

## 构建与测试

```bash
./gradlew :androidApp:assembleDebug          # 构建 APK
./gradlew :shared:testAndroidHostTest        # 当前无行为测试，NO-SOURCE 不代表 UI 验收
./gradlew :core:allTests                     # 纯逻辑测试（主力）
./gradlew :androidApp:lintDebug
```

- 环境：Gradle 9.5（wrapper）、AGP 9.1、Kotlin 2.4、compileSdk/targetSdk 37、JVM target 11。版本统一在 `gradle/libs.versions.toml`，不要在模块脚本里写死版本号。
- `minSdk` 目标是 26（已落地）。
- AGP 9 内置 Kotlin：Android application/library 模块不要再应用 `org.jetbrains.kotlin.android`。
- 不要使用 `applicationIdSuffix`：LSPosed 作用域和 Shizuku Provider 都绑定包名 `cn.rainafter.app.gazekeep`。

## 模块与依赖规则（§2）

```
androidApp ─▶ shared ─▶ core
androidApp ─▶ engine ─▶ core
androidApp ─▶ backend:{a11y,shizuku,lsposed} ─▶ core
backend:lsposed ─▶ bridge:protocol ◀─ xposed
androidApp ─▶ xposed（打包入口；hidden-api 存根尚未建立）
```

- `core`：KMP 纯 Kotlin，**不得依赖 Android**。状态机（reducer）、投票、门控判定、保亮上限、退避都放这里，用虚拟时钟测试。
- `shared`：KMP UI（miuix）。只依赖 `core` 和 `GazeKeepController` / `PlatformActions` 接口；`commonMain` 里不得 import `android.*`。
- `engine`：相机、分类、传感器、超时估算、配置、决策日志。不依赖任何具体后端。
- `bridge:protocol`：只放 LSPosed 协议常量与 AIDL；保留 `.xposed.protocol` 包名，不依赖 core、协程或 UI。
- `xposed`：运行在 `system_server`。**禁止协程、DataStore、Compose、相机、模型、文件 I/O**；只做「报告即将超时」和「执行 userActivity」。
- 包名根为 `cn.rainafter.app.gazekeep`，子包 `.core` / `.engine` / `.backend.a11y` / `.backend.shizuku` / `.backend.lsposed` / `.xposed` / `.ui`。

## 不可违反的约束

这些是产品承诺，不是风格偏好。代码评审遇到违反即打回。

1. **Unknown 等价于 Away。** 看不清、没脸、侧脸、多张脸、太暗、相机失败，一律不保亮。不得新增「失败也保持常亮」之类的配置项或分支。
2. **默认熄屏。** 任何链路中断（超时、异常、后端掉线、切换后端）结果都必须是系统按原超时熄屏。
3. **帧不出 `ImageAnalysis.Analyzer` 回调。** 不保存、不复制到长期存在的缓冲、不写文件、不进日志、不跨进程。唯一的预览只在自检向导页。
4. **禁止权限**：`INTERNET`、存储/相册类、`QUERY_ALL_PACKAGES`、`SYSTEM_ALERT_WINDOW`、`READ_PHONE_STATE`。新增依赖若经 Manifest 合并带入 `INTERNET`，用 `tools:node="remove"` 去掉。不接任何统计、崩溃上报、广告 SDK。
5. **日志只存枚举**：时间、包名、Attention、跳过/中止原因、后端、是否 extend、耗时。不存图像、人脸框、欧拉角、概率。调试日志默认关，开启后 24 小时滚动删除。
6. **单次最长保亮**（默认 30 分钟）到点必须熄一次。
7. **system_server 安全**：钩子方法逐候选解析，签名不匹配就不安装并记录，绝不抛异常到系统进程；钩子回调体整体 `try/catch(Throwable)`；PMS 锁内只做计算与 `Handler` 投递，不做 binder 调用、不取其他锁。
8. **不改用户设置**，除了 Shizuku 退路临时改 `screen_off_timeout`，且必须先把原值持久化到 `pendingTimeoutRestore`，并在服务重启 / binder 重连 / 息屏时写回（§9.2.3）。
9. **不静默失效。** 服务被杀、相机拿不到、后端掉线、无障碍被关，必须发一条通知（同类 10 分钟内最多一条）。检测到「在看」时不弹任何提示。
10. 无障碍服务保持 `canRetrieveWindowContent="false"`，不调用 `event.source`，不读取文本。

## 编码约定

- Kotlin 官方代码风格（`kotlin.code.style=official`）。注释与 UI 文案用中文；注释只写「为什么」，不复述代码。
- 状态与竞态：引擎只在单线程调度器上串行处理 `EngineEvent`，副作用结果回灌为事件（§4）。不要在别处直接修改引擎状态或绕过 reducer 调用后端。
- 新逻辑优先写成 `core` 里的纯函数并配单测；Android 侧只做采集与执行。
- 新增依赖先加到 `libs.versions.toml`，取最新稳定版并锁定。ML Kit 必须用 bundled 版 `com.google.mlkit:face-detection`，不要用 `play-services-mlkit-*`。
- UI 用 miuix 组件，信息架构遵循规划文档 §6；主界面只出现「在看 / 没在看 / 这次没看清」三种用户语言。字符串放 `composeResources`，不硬编码。
- 隐私三句话的文案与规划文档 §10 逐字一致，改动需同步两处文档。
- 标注【待验证】的设计点（隐藏 API 签名、HyperOS 行为等），实现时在代码里留 `// 已验证：<机型> <ROM 版本>` 或保留 TODO，不要把猜测当事实写进注释。

## 测试要求

- `core` 的以下不变量必须有单测（§14）：Unknown 不产生 Extend；采帧中用户触摸会取消且不 Extend；达到保亮上限下一轮必 Yield；ScreenOff 后释放且不再采帧；多脸非 Looking；切换后端期间不 Extend；过期 seq 的 extend 返回 STALE。
- 设备相关（相机、无障碍、Shizuku、LSPosed）没有自动化条件，按规划 §14 手工矩阵在小米 12 Pro 上验证，结果记到 `docs/testing/`。改分类器或阈值必须重跑 M0 计数表。
- 不要为了让测试通过而放宽「默认熄屏」相关断言。

## 维护导航

先看 `docs/maintenance/维护导航.md` 按功能定位入口、组件和验证；`scripts/check_architecture.py` 检查模块/源码边界。里程碑提案保留为历史分析，当前目录以维护导航和架构设计为准。

## 文档

- 设计变更先改 `docs/architecture/GazeKeep-架构设计.md`，再改代码；与规划文档冲突的决议写进架构设计 §0。
- 每次发布更新 `docs/compat/tested-systems.md`（机型、ROM、Android、LSPosed 版本、命中的钩点候选）。

## Git

- 提交信息用中文，首行概括改动（例如 `engine: 加入 AE 预热帧丢弃`），模块名作前缀。
- 不提交 `local.properties`、签名文件、dumpsys 原始输出（样本需裁剪到 power 段并去掉序列号）、任何含人脸的图片。
