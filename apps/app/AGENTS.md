# Android 与 KMP 工程规则 (`apps/app`)

本目录继承根 [AGENTS.md](../../AGENTS.md)，统一维护 Gradle 11 个模块（9 个运行模块、`hidden-api-stubs` 编译存根与 `e2e` 实机测试模块）的职责边界、安全红线与验证入口。架构与实测 ADR 见 [应用架构](../../docs/architecture/app.md) 与 [后端架构](../../docs/architecture/backends.md)。

## 十一个模块职责与不可变边界

模块依赖、包名目录一致性与越界 `import` 由 `pnpm verify:architecture` 强制守卫：

| 模块               | 核心职责与架构红线                                                                                                  | 定向验证命令（`pnpm --filter @gazekeep/app exec ./gradlew ...`）         |
| ------------------ | ------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------ |
| `core`             | `commonMain` 纯 Kotlin 模型、接口、`EngineReducer`、门控/投票/退避策略；**严禁依赖 `android.*`/`androidx.*`**       | `:core:allTests`                                                         |
| `engine`           | CameraX 采集、ML Kit bundled `face-detection`、传感器窗口、DataStore 配置与副作用运行器；不依赖具体后端或 UI        | `:engine:testDebugUnitTest`                                              |
| `shared`           | KMP `miuix` UI、`GazeKeepController`/`PlatformActions` 契约与 Web `Showcase`；`commonMain` 禁引 Android/engine/后端 | `:shared:testAndroidHostTest`                                            |
| `android`          | `AppGraph` 装配、`MainActivity`、`GazeService`、`GazeQuickTileService`、自检协调、应用列表、通知与语言              | `:android:testDebugUnitTest :android:assembleDebug :android:lintDebug`   |
| `backend:a11y`     | `GazeA11yService`、无障碍 `Hold` 窗口、Activity 前台解析与 `IdleClockOracle`                                        | `:backend:a11y:testDebugUnitTest`                                        |
| `backend:shizuku`  | `ShellService`（保持根包）、`PowerDumpOracle`、`NoChangeLights` 与 `pendingTimeoutRestore` 同步持久化恢复           | `:backend:shizuku:testDebugUnitTest`                                     |
| `backend:lsposed`  | App 侧 Binder 客户端与 `HiddenApiBypass` 进程内串行解析；不导入 `xposed` 内部实现                                   | `:backend:lsposed:testDebugUnitTest`                                     |
| `bridge:protocol`  | LSPosed 跨进程常量与 AIDL（保留 `.xposed.protocol` 包名，零业务/协程/UI 依赖）                                      | `:bridge:protocol:assembleDebug :xposed:testDebugUnitTest`               |
| `xposed`           | `system_server` 薄钩子（`ModernEntry`/`LegacyEntry`、电源调度、绿点过滤）；**禁引协程/DataStore/Compose/相机/IO**   | `:xposed:testDebugUnitTest`                                              |
| `hidden-api-stubs` | 隐藏 API 最小编译签名，无运行实现，**仅允许 `compileOnly`**，严禁进入 APK DEX                                       | `pnpm verify:architecture`                                               |
| `e2e`              | `com.android.test` 独立进程 UiAutomator 实机套件（`targetProjectPath = :android`）                                  | `:e2e:assembleDebug`（实机跑 `pnpm --filter @gazekeep/app test:device`） |

- **工程级配置约束**：依赖与版本唯一真源为 `gradle/libs.versions.toml`；Android 模块使用 AGP 内置 Kotlin，不应用 `org.jetbrains.kotlin.android`；不使用 `applicationIdSuffix`。`MainActivity`、`GazeService`、`GazeA11yService`、`ShellService`、`LegacyEntry`、`ModernEntry` 的类名与跨进程 AIDL/descriptor/字段顺序保持稳定。

## 产品安全承诺

违反以下任一条即在代码评审中直接阻断：

1. **`Unknown` 等价于 `Away`**：看不清、没脸、侧脸、多张脸、太暗、相机失败，一律不保亮；严禁新增「失败也保持常亮」的配置或分支。
2. **默认熄屏**：任何链路中断（超时、取消、异常、后端掉线、切换后端）结果都必须是系统按原超时熄屏。
3. **帧不出 `ImageAnalysis.Analyzer` 回调**：不保存、不复制到长期缓冲、不写文件、不进日志、不跨进程；唯一预览仅在自检向导页，离页立即释放。
4. **禁止敏感权限**：严禁 `INTERNET`、存储/相册类、`QUERY_ALL_PACKAGES`、`SYSTEM_ALERT_WINDOW`、`READ_PHONE_STATE`；合并 Manifest 若带入网络权限必须用 `tools:node="remove"` 剔除；零统计、崩溃上报或广告 SDK。
5. **日志只存脱敏枚举**：仅允许记录时间、包名、`Attention`、跳过/中止原因、后端、是否 `Extend`、耗时；严禁记录图像、人脸框、欧拉角、概率或原始 `dumpsys`。调试日志默认关，开启后 24 小时滚动删除。
6. **单次最长保亮（`MaxHold`）**：默认 30 分钟（支持自定义或设为无限制），未设为无限制时到点必须强制释放并熄屏一次。
7. **`system_server` 绝对安全**：钩子逐候选签名解析，失配不安装、不抛异常到系统进程；回调整体 `try/catch(Throwable)` 熔断；PMS 锁内只做计算与 `Handler` 投递，不做 Binder 调用、不取其他锁。
8. **不篡改用户系统设置**：唯一例外是 Shizuku 退路临时修改 `screen_off_timeout`，必须先同步 `commit` 持久化原值至 `pendingTimeoutRestore`，并在息屏、服务停止、重连或开机时写回确认（见 [后端架构](../../docs/architecture/backends.md#shizuku-与系统超时恢复状态机)）。
9. **不静默失效**：服务被杀、相机不可用、后端掉线或无障碍被关时限频发送通知（同类 10 分钟最多一条）；检测到「注视了屏幕」时绝不弹任何提示。
10. **无障碍零内容读取**：固定保持 `canRetrieveWindowContent="false"`，不调用 `event.source`，不读取屏幕文本。

## 验证与门禁

- 测试编写与评审加载 [testing skill](../../.agents/skills/testing/SKILL.md)；完整主机门禁运行 `pnpm --filter @gazekeep/app verify`；UI 外观变更按 [开发、测试与发布](../../docs/development.md#测试分层与入口) 核对 Roborazzi 截图基线；隐私三句与 [产品真源](../../docs/product.md#隐私文案真源) 逐字一致。
