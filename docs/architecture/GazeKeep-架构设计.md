# 注视保亮 GazeKeep 架构设计

| 项 | 内容 |
|---|---|
| 文档版本 | v1.1 |
| 上游文档 | [GazeKeep-规划文档.md](./GazeKeep-规划文档.md)（产品范围、里程碑、风险以它为准） |
| 本文定位 | 把规划落到模块、接口、线程、时序、清单和测试上，作为 M0–M4 的实现依据 |
| 状态 | M3 主链路回归通过。M0–M2 主链路已落地；LSPosed 设置页抢占回调已修复，60 秒超时下连续周期、退出设置页后保亮及转头后熄屏通过，钩点失配和禁用作用域安全矩阵待补齐 |
| 首要验证机 | 小米 12 Pro（HyperOS，Android 13/14） |
| 适用范围 | 没有系统级「注视不熄屏」能力的 Android 手机；机型适配以实测为准 |

> 约定：标记 **【待验证】** 的内容来自 AOSP 源码阅读或经验，必须在指定里程碑上用 12 Pro 实测或反编译目标 ROM 确认后，才能当作事实写进代码注释。

本设计的目标是为缺少系统级「注视不熄屏」能力的 Android 手机提供接近原生系统级的注视保亮体验。架构按跨厂商、跨 Android 版本的适配来组织，不能把某一款手机的系统行为写成通用前提；小米 12 Pro 仅作为首要验证基线，用于收敛首批相机、时序、功耗和特权后端的实现细节。

---

## 0. 与规划文档的差异与决议

| 项 | 规划文档 | 本文决议 | 理由 |
|---|---|---|---|
| 产品目标 | 原表述为面向小米 12 Pro 及同类老机 | **面向缺少系统级「注视不熄屏」能力的 Android 手机，提供接近原生系统级的体验；12 Pro 为首要验证基线** | 避免把验证设备误写成产品边界，便于后续扩展厂商与 Android 版本 |
| 包名 | `dev.gazekeep.app` | **`cn.rainafter.app.gazekeep`** | 现有工程已使用；LSPosed 作用域、Shizuku Provider authority 都绑定 applicationId，开工前定死，之后不改 |
| `minSdk` | 建议 26 | **26**（模板当前为 24，需改） | 与规划一致；开放问题「26 还是 28」维持 26，通知渠道差异成本很小 |
| 工程模块 | `composeApp/engine/backend-*/xposed/app` | 保留模板的 `androidApp`（application）与 `shared`（KMP UI），新增 `core`、`engine`、`backend/*`、`xposed`、`hidden-api` | 不推翻已生成的 KMP 脚手架；`backend-api` 并入 `core`（纯 Kotlin，UI 也要用这些类型） |
| `AttentionClassifier` 签名 | `analyze(frames: List<ImageProxy>)` | 拆成「逐帧分类」+「纯函数投票」 | 投票可在 commonTest 里测；支持提前结束（2 帧一致即停），少开一帧相机 |
| 无障碍保亮手段 | 挂 `FLAG_KEEP_SCREEN_ON` | 优先「脉冲」模式，退路「持有」模式（§9.3） | 让三种后端的语义都统一为「相当于用户碰了一下」 |
| M1 脉冲实测 | 以 300 ms `FLAG_KEEP_SCREEN_ON` 释放模拟一次用户活动 | **小米 12 Pro（2207122MC，HyperOS V816，Android 14）实测未重置完整熄屏周期；无障碍后端默认使用持有模式，保留脉冲实现供其他 ROM 验证** | 该 ROM 的 WMS 未表现出文档假设的 `ON_AFTER_RELEASE` 语义，持有模式才能兑现 M1 的默认熄屏与单周期检测约束 |
| 无障碍 M1 首个 deadline | 服务启动时直接启动空闲时钟 | **先向 reducer 提交后端就绪，再启动 `IdleClockOracle`** | oracle 启动会立即发出首个 deadline；顺序反过来会被 `Disabled` 状态丢弃，导致引擎永远不进入门控 |
| HyperOS 触摸信号 | 仅依赖 overlay 的 `ACTION_OUTSIDE` | **同时消费 `TYPE_TOUCH_INTERACTION_START`，仍只使用事件类型和包名** | 目标 ROM 对跨窗口 `ACTION_OUTSIDE` 的投递不稳定；无障碍触摸事件作为同链路补充 |
| M2 后端入口 | 仅在调试卡片中单次调用 `userActivity` | **持久化后端锁定值（Auto / Shizuku / Accessibility），M1 启动时由 `GazeService` 按该值选择并启动对应后端** | M2 必须能参与完整门控、采帧和保亮链路，单次测试不能代表实际运行 |
| 调试页入口 | 分别提供 M0 自检、M1 引擎测试和 M2 Shizuku 能力卡片 | **统一为后端选择页；选择无障碍时自动启动引擎并显示服务状态，未启用时提供系统设置入口；切换其他后端时停止当前引擎** | 后端选择本身就是运行配置，测试入口不应继续暴露为产品操作 |
| 白名单配置 | M1 测试页关闭白名单门控 | **正式白名单页接入前固定关闭白名单门控** | 当前没有可编辑的白名单，空列表若开启门控会让所有检测静默跳过 |
| HyperOS 电源字段 | 按 AOSP 解析 `mLastUserActivityTime=` | **同时兼容 `mLastUserActivityTime(excludingAttention)=`** | 小米 12 Pro / HyperOS V816 的括号后缀会导致 Shizuku 无法产生 deadline，必须按实机格式解析 |
| Shizuku deadline 轮询 | 每次读取 power 都发射当前 deadline | **同一 `ExtendToken` 只发射一次，系统活动时间变化后才发射新 deadline** | 进入采集窗口后重复事件会取消上一轮采帧，导致 `userActivity` 来不及执行 |
| M3 钩点签名 | 直接按 AOSP 猜测方法签名 | **12 Pro / HyperOS V816 的 `AttentionDetector.updateUserActivity(long,long):long`、`PowerManagerService.updateUserActivitySummaryLocked(long,int):void`、`scheduleUserInactivityTimeout(long):void` 已从 `/system/framework/services.jar` 确认；运行时仍逐候选解析，优先只安装 A，A 不存在才用 C** | A 候选在设备上持续产生有效 deadline；同一绝对熄屏时间只推进一次 seq，避免重复系统回调使采集令牌过期；PowerManager 在 Extend 时懒获取，避开 system_server 启动早期服务尚未注册 |
| LSPosed 降亮前检测 | 固定在熄屏前 5 / 8 秒检测 | **候选 A 保留 `screenDimAt` 和真实 `screenOffAt`，系统通知与 reducer 都在 `screenDimAt − lead` 调度；未知降亮时刻时仍用 `screenOffAt − lead`** | 长超时的降亮阶段可能超过提前量，只按熄屏调度会先变暗再恢复；失败仍放行原始降亮与熄屏，不更改系统设置。桥接升为 v2，升级后需重启使 system_server 加载新协议；修复效果待实机验证 |
| Shizuku / 无障碍降亮前检测 | 按熄屏时刻减提前量调度 | **Shizuku 用 power 的有效超时和 `dimDurationMs`；无障碍读 framework 的最大降亮时长 / 比例，以 `min(maxDuration, timeout × ratio)` 估算降亮时刻；共同交给 reducer 调度** | 不新增权限、不改亮度或熄屏设置；无障碍拿不到运行时覆盖值，仍是估算。任一降亮字段缺失 / 非法就回退原调度，不伪造已知时刻；12 Pro 当前资源 18000 ms / 约 30% 已通过只读查询确认，后端完整效果待回归 |
| 无障碍触摸事件顺序 | 先发新 deadline，再取消旧轮次 | **先向 reducer 提交 UserActivity，再让 IdleClockOracle 发布新 deadline** | SharedFlow 在主线程可能立即恢复收集器，反序会先 Armed 再取消，导致之后再无检测；顺序修复不延长系统计时，只取消旧采集并启动新的检测等待 |
| 重复前台启动 | 只在 onCreate 中 startForeground | **onCreate 和每次 onStartCommand 都立即调用同一前台注册方法** | 无障碍连接与页面就绪会重复启动同一服务；本轮 12 Pro 记录 ForegroundServiceDidNotStartInTimeException，已创建的服务需再次兑现前台启动要求，不能只依赖首次注册 |
| 息屏后停服务 | 息屏可停、亮屏再拉起 | **息屏不停前台服务，只让它完全空转** | Android 11+ 从后台启动的前台服务拿不到相机（while-in-use 限制）；亮屏广播拉起属于后台启动（§10.3） |
| 连续注视退避 | 跳过一轮 / 提前量改 4 秒 | 只缩提前量与帧数，**不跳轮** | `userActivity` 语义下跳一轮就等于熄屏；每个超时周期至少一次检测是物理下限（§7.5） |

---

## 1. 总体架构

### 1.1 分层

```
┌──────────────────────────────────────────────────────────────────────┐
│ UI 层  :shared (KMP, commonMain)                                     │
│   miuix 设置页 / 自检向导 / 隐私页 / 调试页                             │
│   SettingsViewModel ── 只依赖 GazeKeepController + PlatformActions 接口 │
└───────────────▲───────────────────────────────┬──────────────────────┘
                │ StateFlow<ControllerState>    │ 命令
┌───────────────┴───────────────────────────────▼──────────────────────┐
│ 应用装配层  :androidApp                                               │
│   AppGraph（手写 DI） / GazeService（前台服务）/ GazeA11yService        │
│   ControllerImpl / PlatformActionsImpl / Manifest 聚合                 │
└───────┬─────────────────────────┬──────────────────────────┬─────────┘
        │                         │                          │
┌───────▼──────────┐   ┌──────────▼───────────┐   ┌──────────▼─────────┐
│ :engine          │   │ :backend:*           │   │ :core (KMP 纯逻辑)   │
│ GazeEngine       │──▶│ lsposed / shizuku /  │   │ 模型、接口、状态机、  │
│ camera / classify│   │ a11y 的 App 侧实现    │   │ 投票、门控、保亮策略  │
│ gate / oracle    │   └──────────┬───────────┘   └────────────────────┘
│ config / log     │              │ binder
└──────────────────┘   ┌──────────▼───────────────────────────────────┐
                       │ 特权侧                                         │
                       │  :xposed      → 运行在 system_server（薄 Hook）│
                       │  ShellService → 运行在 Shizuku 拉起的 shell 进程 │
                       │  (无障碍没有特权侧，overlay 在 App 进程)          │
                       └──────────────────────────────────────────────┘
```

核心原则（任何改动不得破坏）：

1. **判断注视只在 App 进程。** system_server 与 shell 进程只做两件事：报告「快要超时了」、执行「重置超时」。
2. **默认熄屏。** 任何链路断掉（无响应、超时、异常、未知），结果都是系统按原超时熄屏。保亮必须是「正确完成一整条链路」的结果。
3. **帧不出 `ImageAnalysis.Analyzer` 回调。** 不复制到堆外、不写文件、不进日志、不跨进程。
4. **纯逻辑进 `:core`。** 状态机、投票、门控判定、保亮上限、退避都是纯函数，能在 JVM 上用虚拟时间测试。

### 1.2 进程视图

| 进程 | 组件 | 生命周期 |
|---|---|---|
| `cn.rainafter.app.gazekeep`（主进程） | UI Activity、`GazeService`（前台服务，类型 `camera`）、`GazeA11yService`、引擎、App 侧后端 | 用户开启总开关后常驻；息屏空转 |
| `system_server` | `:xposed` 的 `PowerHookCore`、一个 `HandlerThread("GazeKeep")` | 随系统；未注册客户端时完全被动 |
| `cn.rainafter.app.gazekeep:shell`（Shizuku UserService，uid 2000） | `ShellService` | Shizuku 后端启用时由 App 绑定；App 解绑即退出（非 daemon） |

无障碍服务与前台服务放在同一主进程，原因：overlay、触摸感知、引擎三者要共享状态，跨进程只会增加时序误差。

---

## 2. 工程结构

### 2.1 模块

```
GazeKeep/
├── androidApp/                 # com.android.application；唯一 APK
│   └── src/main/
│       ├── AndroidManifest.xml # 聚合：服务、Provider、权限、<queries>
│       ├── kotlin/.../app/     # GazeKeepApp, AppGraph, MainActivity
│       ├── kotlin/.../service/ # GazeService, GazeA11yService, BootRecoveryReceiver
│       ├── kotlin/.../platform/# ControllerImpl, PlatformActionsImpl, Notifier
│       ├── res/xml/            # accessibility_service_config.xml
│       └── resources/META-INF/xposed/   # 现代 LSPosed 入口（见 §9.1.6）
├── shared/                     # KMP UI（仅 android target）
│   └── src/
│       ├── commonMain/kotlin/.../ui/    # screens, components, nav, viewmodel
│       ├── commonMain/composeResources/ # 中文字符串（默认）、图标
│       └── androidMain/kotlin/          # 极少：Preview、平台 actual（若有）
├── core/                       # KMP，commonMain 纯 Kotlin，无 Android 依赖
│   └── src/commonMain/kotlin/.../core/
│       ├── model/              # Attention, BackendId, Readiness, GazeConfig, DecisionRecord…
│       ├── backend/            # KeepAwakeBackend, TimeoutOracle 接口
│       ├── policy/             # AttentionVoter, GatePolicy, HoldPolicy, LeadBackoff
│       └── engine/             # EngineState, EngineEvent, EngineEffect, EngineReducer
├── engine/                     # com.android.library
│   └── src/main/kotlin/.../engine/
│       ├── GazeEngine.kt       # 事件循环，执行 reducer 产出的 Effect
│       ├── camera/             # FrameSource (CameraX 实现)，CaptureLifecycleOwner
│       ├── classify/           # FrameClassifier, MlKitFrameClassifier, LumaProbe
│       ├── gate/               # SensorProbe, CameraAvailability, CallState, ScreenState
│       ├── oracle/             # IdleClockOracle, OracleSelector
│       ├── foreground/         # ForegroundAppSource 及其实现
│       ├── config/             # ConfigRepository (DataStore)
│       └── log/                # DecisionLog（内存环形缓冲 + 可选 24h 文件）
├── backend/
│   ├── a11y/                   # A11yKeepAwakeBackend, OverlayController, TouchSensor
│   ├── shizuku/                # ShizukuKeepAwakeBackend, ShellService, PowerDumpParser, AIDL
│   └── lsposed/                # LsposedKeepAwakeBackend, SystemBridgeClient（App 侧）
├── xposed/                     # 运行在 system_server 的代码 + 双方共享的协议
│   └── src/main/kotlin/.../xposed/
│       ├── protocol/           # BridgeProtocol 常量、AIDL（IGazeKeepClient）
│       ├── entry/              # ModernEntry (libxposed), LegacyEntry (XposedBridge)
│       ├── hook/               # HookFacade, HookPlan, DeadlineResolver
│       └── core/               # PowerHookCore（与 Hook 框架无关的逻辑）
└── hidden-api/                 # compileOnly 存根：IPowerManager, ServiceManager 等
```

依赖方向（只允许向下）：

```
androidApp ─▶ shared ─▶ core
androidApp ─▶ engine ─▶ core
androidApp ─▶ backend:{a11y,shizuku,lsposed} ─▶ core
backend:lsposed ─▶ xposed(protocol 包)
backend:shizuku, xposed ─compileOnly▶ hidden-api
```

- `shared` 不依赖 `engine`/`backend`：UI 只认识 `core` 的类型与 `GazeKeepController` 接口。
- `engine` 不依赖任何具体后端：它只拿到 `KeepAwakeBackend` 与 `TimeoutOracle`。
- `xposed` 不依赖 `engine`/`core`：它运行在 system_server，类越少越好，不拉 kotlinx-coroutines。**`xposed` 模块里禁止使用协程、DataStore、Compose。** Kotlin 标准库可以用，但注意它会被打进同一 APK，由模块 ClassLoader 加载，不污染系统。

### 2.2 Gradle 约定

- AGP 9 内置 Kotlin 支持（模板里 `androidApp` 已不再单独应用 `kotlin-android`）。新增 Android library 用 `com.android.library`；`core` 与 `shared` 用 `kotlin-multiplatform` + `com.android.kotlin.multiplatform.library`。
- 在 `libs.versions.toml` 增加 `androidLibrary` 插件别名。
- `minSdk` 改为 26，`compileSdk`/`targetSdk` 保持模板的 37。
- JVM target 统一 11（与模板一致）。
- `backend:shizuku`、`xposed` 打开 `buildFeatures { aidl = true }`。
- release 开 R8，keep 规则见 §13.3。

版本目录新增项（版本号开工时取最新稳定版并锁定，不在本文写死）：

| 别名 | 坐标 | 用于 |
|---|---|---|
| `kotlinx-coroutines-android` | `org.jetbrains.kotlinx:kotlinx-coroutines-android` | engine、backend |
| `kotlinx-coroutines-test` | `org.jetbrains.kotlinx:kotlinx-coroutines-test` | core 测试 |
| `androidx-camera-{core,camera2,lifecycle}` | `androidx.camera:*` | 快门 |
| `androidx-camera-view` | `androidx.camera:camera-view` | 仅自检向导预览 |
| `mlkit-face-detection` | `com.google.mlkit:face-detection`（**bundled 版**，不是 `play-services-mlkit-face-detection`） | 分类 |
| `androidx-datastore-preferences` | `androidx.datastore:datastore-preferences` | 配置 |
| `androidx-lifecycle-service` | `androidx.lifecycle:lifecycle-service` | `GazeService : LifecycleService` |
| `shizuku-api`、`shizuku-provider` | `dev.rikka.shizuku:api`、`dev.rikka.shizuku:provider` | Shizuku 后端 |
| `libxposed-api`（compileOnly） | `io.github.libxposed:api` | 现代 LSPosed 入口。**【待验证】** 发布渠道（历史上需自行构建或走 JitPack），M3 第一天确认 |
| `libxposed-service` | `io.github.libxposed:service` | App 侧探测框架与作用域 |
| `xposed-api`（compileOnly） | `de.robv.android.xposed:api:82` | 旧式入口兜底 |
| `hiddenapibypass` | `org.lsposed.hiddenapibypass:hiddenapibypass` | App 进程访问 `ServiceManager` |
| `miuix-ui` | `top.yukonga.miuix.kmp:miuix-ui`（及可选 `miuix-preference`） | UI |

### 2.3 包名约定

根包 `cn.rainafter.app.gazekeep`，各模块子包：`.core`、`.engine`、`.backend.a11y`、`.backend.shizuku`、`.backend.lsposed`、`.xposed`、`.ui`。namespace 与子包一致（例如 `cn.rainafter.app.gazekeep.engine`）。

---

## 3. 核心模型（`:core`）

### 3.1 基础类型

```kotlin
package cn.rainafter.app.gazekeep.core.model

/** 一轮检测的最终结论。Unknown 在保亮决策上等价于 Away，但日志与门控要区分。 */
enum class Attention { Looking, Away, Unknown }

/** 单帧结论，供投票与调试页使用。 */
sealed interface FrameVerdict {
    data object Looking : FrameVerdict
    data class NotLooking(val reason: Reason) : FrameVerdict
    enum class Reason { NoFace, MultipleFaces, FaceTooSmall, HeadTurned, EyesClosed, TooDark, ClassifierError }
}

enum class BackendId { Lsposed, Shizuku, Accessibility }

sealed interface Readiness {
    data object Ready : Readiness
    data class NeedsSetup(val step: SetupStep) : Readiness
    data class Unavailable(val cause: String) : Readiness
}

enum class SetupStep {
    InstallFramework, EnableModuleScope, Reboot,          // LSPosed
    StartShizuku, GrantShizukuPermission,                 // Shizuku
    EnableAccessibilityService,                           // 无障碍
}

enum class Sensitivity { Standard, Loose }
enum class ClassifierKind { MlKit, MediaPipe }
enum class BackendLock { Auto, Lsposed, Shizuku, Accessibility }
```

### 3.2 配置

```kotlin
data class GazeConfig(
    val enabled: Boolean = false,
    val backendLock: BackendLock = BackendLock.Auto,
    val leadMillis: Long = 8_000,              // 允许值：5_000, 8_000
    val sensitivity: Sensitivity = Sensitivity.Standard,
    val whitelistEnabled: Boolean = true,
    val packages: Set<String> = emptySet(),    // 默认空，由自检向导引导勾选
    val maxHoldMinutes: Int = 30,              // 允许值：10, 30, 60
    val classifier: ClassifierKind = ClassifierKind.MlKit,
    val pauseWhenCameraBusy: Boolean = true,   // UI 上展示但不建议关
    val skipAfterDarkFailures: Boolean = true,
    val pauseOnLowBattery: Boolean = false,    // 规划：电量策略做开关，默认关
    val debugLogEnabled: Boolean = false,
)
```

「Unknown 当作 Away」**不是配置项**，代码里不存在对应字段，也不允许以后加。

### 3.3 后端与超时预言接口

```kotlin
package cn.rainafter.app.gazekeep.core.backend

interface KeepAwakeBackend {
    val id: BackendId
    val readiness: StateFlow<Readiness>

    /** 相当于「用户刚碰了一下屏幕」。只在一轮判定为 Looking 后调用一次。 */
    suspend fun extend(token: ExtendToken): ExtendResult

    /** 撤销本后端为保亮持有的一切（overlay flag、临时超时值）。必须幂等。 */
    suspend fun release()

    /** 引擎启停时调用；后端在这里建立/断开 binder、挂/摘 overlay。 */
    suspend fun attach(); suspend fun detach()
}

/** LSPosed 下携带系统给的序号，用于防止「过期的 extend」；其他后端为 None。 */
sealed interface ExtendToken { data object None : ExtendToken; data class Seq(val value: Long) : ExtendToken }
sealed interface ExtendResult { data object Done : ExtendResult; data object Stale : ExtendResult; data class Failed(val cause: Throwable) : ExtendResult }

/** 超时预言：以「推送」为主。LSPosed 精确推送，其他后端用估算推送。 */
interface TimeoutOracle {
    val source: OracleSource                    // System, PowerDump, IdleClock
    /** 每次系统空闲计时被重置或重新估算时发一次；deadline 为 SystemClock.uptimeMillis() 基准。 */
    val deadlines: Flow<TimeoutDeadline>
    /** 规划中的拉取式接口仍保留，供调试页显示。未知返回 null。 */
    fun millisUntilScreenOff(): Long?
}

data class TimeoutDeadline(
    val screenOffAtUptime: Long,
    val screenDimAtUptime: Long? = null, // 已知降亮时刻；未知则按熄屏时刻调度
    val token: ExtendToken,
    val foregroundPackage: String?,     // LSPosed 由 system_server 给出；其他为 null，交给 ForegroundAppSource
    val lastExternalActivityUptime: Long?, // 最近一次「真实用户活动」，用于保亮上限计数
)
```

> 已知有效降亮时刻时，检测基准为 `screenDimAtUptime`，否则为 `screenOffAtUptime`；`wakeAt = 检测基准 − lead`。采集超时与最小可用窗口仍按真实熄屏时刻计算。

> 规划中 `millisUntilScreenOff()` 的拉取模型改为推送为主：引擎需要「在 deadline − lead 时醒来」，拉取模型会迫使引擎自己轮询。拉取接口保留用于调试显示。

---

## 4. 引擎状态机（`:core/engine` + `:engine`）

引擎采用「纯 reducer + 副作用执行器」结构：

```
         EngineEvent                     EngineEffect
外部世界 ───────────▶ EngineReducer ───────────────▶ GazeEngine 执行
   ▲                (纯函数, :core)                     │
   └────────────────────── 结果再变成 EngineEvent ◀──────┘
```

`EngineReducer.reduce(state, event, now): Transition(state, effects)`。`GazeEngine` 在单线程调度器（`Dispatchers.Default.limitedParallelism(1)`）上串行消费一个 `Channel<EngineEvent>`，执行副作用，副作用的结果以事件形式回灌。这样所有竞态（触摸与快门同时发生、后端掉线与 extend 同时发生）都在一个线程里被线性化，并且可以用虚拟时钟穷举测试。

### 4.1 状态

```kotlin
sealed interface EngineState {
    data object Disabled : EngineState                         // 总开关关 / 无可用后端 / 缺相机权限
    data object ScreenOff : EngineState                        // 息屏或锁屏：空转
    data class Armed(val deadline: TimeoutDeadline, val wakeAt: Long) : EngineState   // 等待检测基准 − lead
    data class Gating(val deadline: TimeoutDeadline) : EngineState
    data class Capturing(val deadline: TimeoutDeadline, val abortAt: Long) : EngineState
    data class Extending(val deadline: TimeoutDeadline) : EngineState
    data class Yielded(val reason: YieldReason) : EngineState  // 本轮放行熄屏，等下一个 deadline 或 ScreenOff
}
```

附带的跨轮次上下文（`EngineContext`，也是 reducer 的输入/输出）：

| 字段 | 用途 |
|---|---|
| `holdStartUptime: Long?` | 本段连续保亮的起点（第一次 extend 时设置） |
| `lastExternalActivityUptime` | 真实用户活动时间；变化时清空 `holdStartUptime` |
| `lookingStreak: Int` | 连续 Looking 轮数，驱动退避 |
| `darkUnknownStreak: Int` | 连续因 TooDark 得出 Unknown 的轮数，驱动暗光门控 |
| `forcedYieldPending: Boolean` | 达到保亮上限后，下一轮强制 Yield |

### 4.2 事件

```kotlin
sealed interface EngineEvent {
    data class ConfigChanged(val config: GazeConfig) : EngineEvent
    data class BackendSelected(val backend: BackendId?) : EngineEvent      // null = 无可用后端
    data object ScreenOn : EngineEvent
    data object ScreenOff : EngineEvent
    data object KeyguardDismissed : EngineEvent
    data class DeadlineUpdated(val deadline: TimeoutDeadline) : EngineEvent
    data class UserActivity(val uptime: Long) : EngineEvent                // 触摸/按键（无障碍触摸感知、LSPosed 外部活动）
    data object WakeTimerFired : EngineEvent
    data class GateResult(val result: GateDecision) : EngineEvent
    data class CaptureFinished(val attention: Attention, val frames: List<FrameVerdict>, val latencyMs: Long) : EngineEvent
    data class CaptureAborted(val reason: AbortReason) : EngineEvent      // 相机被抢、超时、权限丢失
    data class ExtendFinished(val result: ExtendResult) : EngineEvent
    data object CameraPermissionLost : EngineEvent
}
```

### 4.3 副作用

```kotlin
sealed interface EngineEffect {
    data class ScheduleWake(val atUptime: Long) : EngineEffect
    data object CancelWake : EngineEffect
    data class RunGates(val deadline: TimeoutDeadline) : EngineEffect
    data class StartCapture(val plan: CapturePlan) : EngineEffect
    data object CancelCapture : EngineEffect
    data class Extend(val token: ExtendToken) : EngineEffect
    data object Release : EngineEffect
    data class Record(val record: DecisionRecord) : EngineEffect
    data class Notify(val alert: Alert) : EngineEffect                    // 仅掉线、权限被拒、无障碍被关
}

data class CapturePlan(val maxFrames: Int, val frameIntervalMs: Long, val abortAtUptime: Long, val sensitivity: Sensitivity)
```

### 4.4 主转移表

| 当前 | 事件 | 条件 | 下一状态 | 副作用 |
|---|---|---|---|---|
| 任意 | `ConfigChanged(enabled=false)` / `BackendSelected(null)` / `CameraPermissionLost` | — | Disabled | CancelCapture, CancelWake, Release |
| ScreenOff | `ScreenOn` / `KeyguardDismissed` | 未锁屏 | Yielded(WaitingDeadline) | 清 `holdStartUptime` |
| 任意亮屏态 | `ScreenOff` | — | ScreenOff | CancelCapture, CancelWake, Release |
| Yielded / Armed | `DeadlineUpdated(d)` | `(d.screenDimAt ?: d.screenOffAt) − lead > now` | Armed(d, wakeAt = 检测基准 − lead) | ScheduleWake |
| Yielded / Armed | `DeadlineUpdated(d)` | 已在 lead 内且 > 最小可用窗口（2.5 s） | Gating(d) | RunGates |
| Yielded / Armed | `DeadlineUpdated(d)` | 剩余 < 2.5 s | Yielded(TooLate) | Record |
| Armed | `WakeTimerFired` | — | Gating | RunGates |
| Armed / Gating / Capturing | `UserActivity` | — | Yielded(WaitingDeadline) | CancelCapture, CancelWake；更新 `lastExternalActivity`，清 hold |
| Gating | `GateResult(Skip(r))` | — | Yielded(Gate(r)) | Record |
| Gating | `GateResult(Proceed)` | `forcedYieldPending` | Yielded(MaxHold) | Record；清 `forcedYieldPending` |
| Gating | `GateResult(Proceed)` | 否则 | Capturing | StartCapture(plan) |
| Capturing | `CaptureFinished(Looking)` | — | Extending | Extend(token) |
| Capturing | `CaptureFinished(Away/Unknown)` | — | Yielded(NotLooking) | Release, Record；`lookingStreak = 0` |
| Capturing | `CaptureAborted(r)` | — | Yielded(Aborted(r)) | Release, Record |
| Extending | `ExtendFinished(Done)` | — | Yielded(WaitingDeadline) | Record；`lookingStreak++`；按 `HoldPolicy` 置 `forcedYieldPending` |
| Extending | `ExtendFinished(Stale/Failed)` | — | Yielded(ExtendFailed) | Record；Failed 连续 2 次 → 触发后端降级 |

说明：

- `Yielded(WaitingDeadline)` 后，后端会因为 extend（或用户活动）产生新的 `DeadlineUpdated`，于是自然进入下一轮。引擎从不主动「补拍」：**屏幕已经灭了就不为补拍亮屏**。
- 无障碍「持有」模式下 extend 不会让系统产生新 deadline，此时 `IdleClockOracle` 自己基于 extend 时间推出下一个 deadline（§9.3.3）。

### 4.5 时序（LSPosed 候选 A，系统超时 60 s，降亮阶段 12 s，lead 8 s）

以下时刻仅为计算示例，实际降亮时刻由系统参数给出，不固定假设 ROM 的降亮比例。

| 相对时间 | 动作 |
|---|---|
| 0 s | 最后一次触摸；系统给出 dim@48、off@60 |
| 40 s | system_server 推送 PreTimeout；reducer 立即门控，不再等到 52 s |
| 40.2 s | 门控通过，开相机 |
| 41.3 s | 两帧 Looking，关闭相机 |
| 41.35 s | extend；在原降亮时刻前重置超时 |
| 48 / 60 s | 若本轮 Away / Unknown / 失败，不重置，仍按系统原时刻降亮 / 熄屏 |

无障碍路径用 framework 资源估算降亮时刻，读不到时回退熄屏前提前量；保留 §9.3.4 所述误差。

---

## 5. 门控（`:core/policy/GatePolicy` + `:engine/gate`）

门控在开相机之前执行，按成本从低到高短路。采集与判定分离：`:engine/gate` 只采集 `GateInput`，`GatePolicy.decide(input, ctx, config)` 是纯函数。

```kotlin
data class GateInput(
    val interactive: Boolean,
    val keyguardLocked: Boolean,
    val foregroundPackage: String?,
    val audioMode: Int,                 // AudioManager.getMode()，免权限判断通话
    val frontCameraAvailable: Boolean,  // 排除自己占用
    val proximityNear: Boolean?,        // null = 无传感器或超时
    val lux: Float?,
    val faceDown: Boolean?,
    val batteryPercent: Int, val powerSave: Boolean,
)

sealed interface GateDecision {
    data object Proceed : GateDecision
    data class Skip(val reason: SkipReason) : GateDecision
}
enum class SkipReason { ScreenOff, Locked, NotWhitelisted, ForegroundUnknown, InCall, CameraSensitiveApp,
                        CameraBusy, Pocket, FaceDown, TooDarkRepeatedly, LowBattery }
```

判定顺序与规则：

| # | 检查 | 规则 | 采集方式 |
|---|---|---|---|
| 1 | 亮屏/锁屏 | `!interactive` 或 `keyguardLocked` → Skip | `PowerManager.isInteractive`，`KeyguardManager.isKeyguardLocked` |
| 2 | 前台包名 | 白名单开启且包名为 null → Skip(ForegroundUnknown)；不在白名单 → Skip | `ForegroundAppSource`（§5.1） |
| 3 | 相机敏感应用 | 前台是已知相机/扫码包（内置短表 + 声明 `android.media.action.STILL_IMAGE_CAMERA` 的启动器应用）→ Skip | 静态表 + `<queries>` |
| 4 | 通话 | `audioMode in {MODE_IN_CALL, MODE_IN_COMMUNICATION, MODE_RINGTONE}` → Skip | `AudioManager`，无需 `READ_PHONE_STATE` |
| 5 | 相机占用 | 前摄不可用且不是自己占用 → Skip | 服务启动时注册的 `CameraManager.AvailabilityCallback`，维护 `unavailableIds - ownedIds` |
| 6 | 电量 | `pauseOnLowBattery && battery < 15` → Skip | 粘性广播 `ACTION_BATTERY_CHANGED` |
| 7 | 传感器（并行，总超时 150 ms） | 接近=近 → Pocket；`z < −7 m/s²` → FaceDown；`lux < 3 && darkUnknownStreak ≥ 2` → TooDarkRepeatedly | `SensorProbe` 一次性注册，拿到首个事件即注销 |

传感器采集细节：

- 接近与光线是 on-change 传感器，注册后系统会立刻投递当前值；加速度取首帧。三者 `withTimeoutOrNull(150)` 并行，超时得 null，null 不构成 Skip。
- 不常驻注册任何传感器。

视频/导航为什么不在门控里：应用自己挂了 `FLAG_KEEP_SCREEN_ON` 时，系统根本不会走到超时，LSPosed 与 Shizuku 的 deadline 不会被触发或会被持续推后（§9.2.4 读取 wake lock 摘要）。只有无障碍空闲时钟会误触发，靠白名单兜底，并在 §9.3.4 用 `AudioManager.isMusicActive()` 作额外启发。

### 5.1 前台应用来源

| 后端 | 来源 | 实现 |
|---|---|---|
| LSPosed | system_server 在 PreTimeout 里直接给出 | 钩子侧通过 `ActivityTaskManager` 内部接口查询焦点任务 **【待验证】**，失败退回 `ActivityManager.getRunningTasks(1)`（system uid 可用） |
| 无障碍 | `TYPE_WINDOW_STATE_CHANGED` 的 `packageName` | 过滤系统 UI、输入法、自身 overlay |
| Shizuku | `UsageStatsManager.queryEvents` 最近的 `ACTIVITY_RESUMED` | Shizuku 首次就绪时用 shell 身份执行 `appops set <pkg> GET_USAGE_STATS allow`，无需用户去设置里找 |

`ForegroundAppSource` 是接口，`OracleSelector` 选后端时一并选来源；多个可用时按上表顺序取第一个非 null。

---

## 6. 采帧与分类（`:engine/camera`、`:engine/classify`）

### 6.1 FrameSource

```kotlin
interface FrameSource {
    /** 打开前摄，按 plan 逐帧回调；回调返回 false 表示够了，立刻关闭。全过程不得超过 plan.abortAtUptime。 */
    suspend fun capture(plan: CapturePlan, onFrame: (ImageProxy) -> Boolean): CaptureOutcome
}
```

CameraX 实现要点：

- `CaptureLifecycleOwner`：自建的 `LifecycleOwner`，只在 `capture()` 期间处于 RESUMED，结束即 DESTROYED；`ProcessCameraProvider.bindToLifecycle(owner, DEFAULT_FRONT_CAMERA, imageAnalysis)`。`ProcessCameraProvider` 在服务启动时预热获取一次（初始化很慢），之后复用。
- `ImageAnalysis`：`ResolutionSelector` 目标 640×480（4:3），`STRATEGY_KEEP_ONLY_LATEST`，`OUTPUT_IMAGE_FORMAT_YUV_420_888`，analyzer 跑在引擎专用的单线程 `Executor`。
- **AE 预热**：丢弃打开后前 2 帧或前 200 ms 的帧（先到者为准），否则暗光下首帧必然 TooDark。
- 帧间隔：两次被采用的帧之间至少 `frameIntervalMs`（默认 150 ms），中间的帧直接 `close()`。
- 中止：`abortAtUptime` 到达、`CameraState` 报 `ERROR_CAMERA_IN_USE`/`ERROR_MAX_CAMERAS_IN_USE`/`ERROR_CAMERA_DISABLED`、或协程取消 → 立即 `unbindAll`，返回 `CaptureOutcome.Aborted(reason)`。前台应用打开相机时，系统按进程优先级把我们踢掉，这条路径就是「立刻让出」。
- 唤醒锁：`capture()` 期间持有 `PARTIAL_WAKE_LOCK`，`acquire(3_000)` 带超时，`finally` 中释放。这是整个 App 唯一持有唤醒锁的地方。
- 若 M0 测得 CameraX 打开延迟 > 800 ms，换 `Camera2FrameSource`（直接 `CameraDevice` + `ImageReader`），接口不变。

时间预算（目标，M0 实测后回填）：

| 阶段 | 预算 |
|---|---|
| 门控 | ≤ 200 ms |
| 相机打开 + AE 预热 | ≤ 800 ms |
| 2–3 帧 × 150 ms | ≤ 450 ms |
| 推理（ML Kit FAST，每帧） | ≤ 60 ms |
| **合计** | **≤ 1.6 s**；硬中止点 `abortAt = screenOffAt − 1_500` |

### 6.2 逐帧分类

```kotlin
interface FrameClassifier : Closeable {
    /** 只能在 analyzer 线程调用；不得持有 image 引用到返回之后。 */
    suspend fun classify(image: ImageProxy, sensitivity: Sensitivity): FrameVerdict
}
```

`ClassifyingPipeline`（每帧）：

1. `LumaProbe`：对 Y 平面按 8 像素步长采样求均值，`< 25`（0–255）直接 `NotLooking(TooDark)`，不调 ML Kit。O(数千像素)，< 1 ms。
2. `InputImage.fromMediaImage(image.image!!, image.imageInfo.rotationDegrees)`。
3. ML Kit `FaceDetectorOptions`：`PERFORMANCE_MODE_FAST`、`CLASSIFICATION_MODE_ALL`、`LANDMARK_MODE_NONE`、`CONTOUR_MODE_NONE`、`setMinFaceSize(0.15f)`、不开 tracking。`FaceDetector` 在服务生命周期内单例复用。
4. 判定（阈值来自 `ClassifierThresholds`，M0 冻结后写成常量）：

```kotlin
data class ClassifierThresholds(
    val maxYawDeg: Float = 25f,
    val maxPitchDeg: Float = 25f,
    val minEyeOpen: Float = 0.6f,
    val minFaceWidthRatio: Float = 0.18f,    // 脸框宽 / 旋转后画面宽
)

fun judge(faces: List<Face>, frameWidth: Int, s: Sensitivity, t: ClassifierThresholds): FrameVerdict = when {
    faces.isEmpty() -> NotLooking(NoFace)
    faces.size > 1 -> NotLooking(MultipleFaces)          // 旁边有人：不保亮
    else -> with(faces[0]) {
        when {
            boundingBox.width() < frameWidth * t.minFaceWidthRatio -> NotLooking(FaceTooSmall)
            abs(headEulerAngleY) > t.maxYawDeg || abs(headEulerAngleX) > t.maxPitchDeg -> NotLooking(HeadTurned)
            s == Sensitivity.Loose -> FrameVerdict.Looking
            (leftEyeOpenProbability ?: 0f) < t.minEyeOpen || (rightEyeOpenProbability ?: 0f) < t.minEyeOpen
                -> NotLooking(EyesClosed)                // 概率缺失视为闭眼
            else -> FrameVerdict.Looking
        }
    }
}
```

`judge` 放在 `:engine`（依赖 ML Kit 的 `Face`），但它只做比较；为方便 JVM 测试，再抽一层 `FaceFeatures(yaw, pitch, leftEye, rightEye, widthRatio, count)` → `judgeFeatures()` 放进 `:core/policy`。

- `MultipleFaces` 计数：只数宽度比 ≥ `minFaceWidthRatio × 0.6` 的脸，避免背景里极小的脸误伤。
- MediaPipe 精确模式不进 v1，接口已能容纳：`MediaPipeFrameClassifier : FrameClassifier`。

### 6.3 投票（`:core/policy/AttentionVoter`）

```kotlin
/** 最多 3 帧，2 帧 Looking 即 Looking；2 帧非 Looking 即判定结束。 */
class AttentionVoter(private val maxFrames: Int = 3, private val need: Int = 2) {
    fun offer(v: FrameVerdict): VoteState   // Continue | Decided(Attention)
}
```

- `Decided(Looking)`：Looking 计数达到 `need`。
- `Decided(Away)`：非 Looking 计数达到 `maxFrames − need + 1`，且非 Looking 原因多数是 `HeadTurned/FaceTooSmall/EyesClosed/NoFace`。
- `Decided(Unknown)`：同上但多数原因是 `TooDark/ClassifierError/MultipleFaces`，或帧没采够就被中止。
- 只有 1 帧可用时（中止）：一律 Unknown。

提前结束：前两帧都 Looking 就停（最常见路径只开相机约 1 s）。

---

## 7. 保亮策略（`:core/policy`）

### 7.1 HoldPolicy：单次最长保亮

- 每次 `ExtendFinished(Done)`：若 `holdStartUptime == null` 则设为 now。
- `now − holdStartUptime ≥ maxHoldMinutes` → `forcedYieldPending = true`，下一轮门控通过后直接 Yield，屏幕按系统超时熄一次。
- 清零条件：`ScreenOff`、或检测到真实用户活动（`lastExternalActivityUptime` 前进）。

> 规划只写了「用户再亮屏后重新计数」。真实触摸也清零是本文补充：用户在操作，画面在变化，不属于烧屏风险场景。

### 7.2 「真实用户活动」的识别

| 后端 | 方法 |
|---|---|
| LSPosed | system_server 侧记录我们自己 `userActivity` 时传的 `eventTime`；`lastUserActivityTime` 不等于它即为外部活动，随 PreTimeout 下发 |
| Shizuku | `PowerDumpParser` 读到的 `mLastUserActivityTime` 不在我们 extend 调用时刻 ±50 ms 内 → 外部活动 |
| 无障碍 | `TouchSensor`（§9.3.2）的 `ACTION_OUTSIDE` 与用户型无障碍事件 |

### 7.3 LeadBackoff：连续注视退避

`userActivity` 语义下，每个超时周期必须检测一次，否则一定熄屏，所以退避只压缩单次成本：

| `lookingStreak` | lead | maxFrames |
|---|---|---|
| 0–1 | 配置值（8 s 或 5 s） | 3 |
| 2–4 | max(配置值 − 3 s, 4 s) | 3 |
| ≥ 5 | 4 s | 3（提前结束使大多数轮次只用 2 帧） |

任何一轮非 Looking 或外部活动，streak 归零。

规划中「每分钟 ≤ 2 次开相机」在 30 s 超时下恰好等于每周期一次；系统超时设得越长，频率越低。不提供「跳轮」，也不提供在 LSPosed 下「连续 extend 两次」的捷径。

### 7.4 失败策略（不可配置）

- Unknown → 不 extend。
- 连续 `darkUnknownStreak ≥ 2` → 下一轮门控直接 Skip(TooDarkRepeatedly)，直到光线 > 10 lux 或外部活动。
- 相机打开失败 → Unknown；连续 3 轮失败 → Notify(CameraUnavailable)。

### 7.5 无障碍「持有」模式下的特例

持有模式（§9.3.3）里 extend 会让屏幕无限期常亮，直到 `release()`。因此在持有模式下：

- Looking 后挂 flag，并由 `IdleClockOracle` 推出「下一检查点 = now + 系统超时 − lead」；
- 下一轮非 Looking → `release()`，系统随后按 `ON_AFTER_RELEASE` 语义再给一个完整超时周期才熄屏（**【待验证 M1】**）。这就是规划里「本轮或下一轮熄屏」的「下一轮」。

---

## 8. 后端选择（`BackendSelector`，位于 `:androidApp/platform`）

```
readiness 流（三个后端各一条）+ config.backendLock
        │
        ▼
 select(): 锁定值若 Ready 则用它；否则按 Lsposed → Shizuku → Accessibility 取第一个 Ready
        │
        ├─ 结果变化且旧后端曾经 Ready → Notify(BackendDegraded(old, new))（每次降级一次通知）
        └─ 发 EngineEvent.BackendSelected，并切换 TimeoutOracle / ForegroundAppSource
```

- 切换顺序：旧后端 `release()` → `detach()` → 新后端 `attach()`。切换期间引擎处于 Disabled，宁可本轮熄屏。
- 用户锁定的后端掉线：不自动降级到别的后端（用户明确选择了），只通知；`Auto` 才降级。
- 恢复：高优先级后端重新 Ready（例如 Shizuku 重启后）时，`Auto` 模式自动升回，不通知。

`OracleSelector` 映射：

| 选中后端 | TimeoutOracle | ForegroundAppSource |
|---|---|---|
| Lsposed | `SystemOracle`（system_server 推送） | 随 PreTimeout 下发 |
| Shizuku | `PowerDumpOracle`；若无障碍也开着，用 `TouchSensor` 作为额外的重新估算触发 | UsageStats |
| Accessibility | `IdleClockOracle` | 无障碍窗口事件 |

---

## 9. 三种后端实现

### 9.1 LSPosed

#### 9.1.1 职责边界

system_server 侧（`:xposed`）只做：

1. 计算每次空闲计时的降亮与熄屏时刻，在 `(screenDimAt ?: screenOffAt) − lead` 时把 `PreTimeout(seq, screenOffAt, screenDimAt, topPackage, lastExternalActivity)` 推给已注册的 App 客户端。候选 A 的 `nextScreenDimming` 必须传到 App；不能只提前系统通知，否则 reducer 会再次等待熄屏窗口。
2. 收到 App 的 `extend(seq)` 后，若 `seq` 仍是当前序号，以 system 身份调用一次 `userActivity`。
3. 应答 `ping`，报告钩子状态。

不开相机、不跑模型、不读写文件、不持久化任何状态、不改设置项。

#### 9.1.2 钩点：DeadlineResolver

AOSP 中熄屏时刻在 `PowerManagerService.updateUserActivitySummaryLocked(long now, int dirty)` 中计算，候选钩点（按优先级尝试，第一个解析成功的生效）：

| 候选 | 钩子 | 拿到什么 | 备注 |
|---|---|---|---|
| A | `com.android.server.power.AttentionDetector#updateUserActivity(long nextScreenDimming, long dimDurationMillis)` 的 after | `screenDimAt = nextScreenDimming`，`screenOff = nextScreenDimming + dimDurationMillis` | **已验证：小米 12 Pro / HyperOS V816 / Android 14 亮屏时持续调用并可驱动完整 deadline → Looking → Extend 链路** |
| B | `PowerManagerService#updateUserActivitySummaryLocked(long, int)` 的 after | 反射读最近活动时间 + 调 `getScreenOffTimeoutLocked`/`getScreenDimDurationLocked` 推算 | Android 13+ 最近活动时间移到 `PowerGroup`，字段/方法名随版本变化，按版本表尝试 |
| C | `PowerManagerService#scheduleUserInactivityTimeout(long)` 的 before | 下一次超时消息时刻（可能是 dim 时刻或 off 时刻） | 需结合当前 summary 区分 dim/off |

规则：

- 解析在模块加载时一次完成：逐个 `findMethod`，找不到或参数类型不符就跳到下一个；全部失败 → 不安装任何钩子，`HookStatus = Unsupported(tried = [...])`，打日志。**绝不因签名不匹配抛异常到 system_server。**
- 每个钩子回调体整体 `try { … } catch (Throwable) { log; }`，并带熔断：同一钩子累计 5 次异常 → 自我禁用。
- 钩子回调运行在 PMS 持锁上下文（`mLock`）里。回调体内**只允许**：读参数、读字段、计算、`handler.sendMessageAtTime`。禁止 binder 调用、禁止获取其他锁、禁止分配大对象、禁止日志 I/O 以外的阻塞操作。
- M3 第一天：`adb pull /system/framework/services.jar`（以及 `/system_ext`、`/system/framework/oat` 下的 HyperOS 补丁），jadx 确认 A/B/C 在 12 Pro 当前 ROM 上的真实签名，结果写进 `docs/compat/` 的已测系统表。

#### 9.1.3 PowerHookCore（与 Hook 框架无关）

```kotlin
internal class PowerHookCore(private val systemContext: Context) {
    private val thread = HandlerThread("GazeKeep").apply { start() }
    private val handler = Handler(thread.looper)
    @Volatile private var client: ClientRecord? = null      // 单客户端：只有本 App
    @Volatile private var currentSeq = 0L
    @Volatile private var ourLastEventTime = -1L

    /** 在 PMS 锁内被钩子调用：只做计算与投递。 */
    fun onDeadlineComputed(screenOffAt: Long, lastActivityTime: Long, screenDimAt: Long? = null) {
        val c = client ?: return
        val seq = ++currentSeq
        handler.removeMessages(MSG_PRE_TIMEOUT)
        val external = if (lastActivityTime != ourLastEventTime) lastActivityTime else null
        val at = (screenDimAt ?: screenOffAt) - c.leadMillis
        handler.sendMessageAtTime(Message.obtain(handler, MSG_PRE_TIMEOUT, PreTimeout(seq, screenOffAt, screenDimAt, external)), at)
    }

    /** handler 线程：查询前台包名，然后 oneway 回调 App。 */
    private fun dispatchPreTimeout(p: PreTimeout) { /* topPackage(); client.callback.onPreTimeout(...) */ }

    /** binder 线程：App 请求 extend。 */
    fun extend(callingUid: Int, seq: Long): Int {
        if (!isOurUid(callingUid)) return RESULT_DENIED
        if (seq != currentSeq) return RESULT_STALE
        val token = Binder.clearCallingIdentity()
        try {
            val now = SystemClock.uptimeMillis()
            ourLastEventTime = now
            powerManager.userActivity(now, USER_ACTIVITY_EVENT_OTHER, 0)   // 隐藏 API，system_server 内无限制
        } finally { Binder.restoreCallingIdentity(token) }
        return RESULT_OK
    }
}
```

- `userActivity` 走公开的 `PowerManager#userActivity(long, int, int)`（`@SystemApi`，需要 `DEVICE_POWER`，system 身份天然具备），不直接调 PMS 内部方法，避免签名适配。事件类型默认 `USER_ACTIVITY_EVENT_OTHER`；若实测 HyperOS 对它有特殊处理，可换 `USER_ACTIVITY_EVENT_ATTENTION`（Android 10+）。
- 不调用时 `flags` 为 0，不要带 `USER_ACTIVITY_FLAG_NO_CHANGE_LIGHTS`（否则暗屏不会恢复）。
- `attentive timeout`（Android 10+ 的「长时间无交互强制息屏」，大多数 ROM 默认关闭）**【待验证】**：若目标 ROM 开启，非触摸事件可能不重置它；遇到时在已测系统表里记录，不做绕过。

#### 9.1.4 App ↔ system_server 通道

不新增系统服务（SELinux 不允许 `addService` 自定义名字）。复用 `power` 服务的 binder，加自定义 transaction code：

```kotlin
object BridgeProtocol {
    const val DESCRIPTOR = "cn.rainafter.app.gazekeep.bridge"
    const val TX_BASE = 0x475A_4B00                 // 'G''Z''K' 00，远高于 IPowerManager 的方法编号
    const val TX_PING = TX_BASE + 1                 // → (protocolVersion, hookStatus, resolvedCandidate)
    const val TX_REGISTER = TX_BASE + 2             // (IGazeKeepClient binder, leadMillis) → ok
    const val TX_EXTEND = TX_BASE + 3               // (seq) → OK | STALE | DENIED
    const val TX_UNREGISTER = TX_BASE + 4
    const val PROTOCOL_VERSION = 2
}
```

- 钩 `android.os.IPowerManager$Stub#onTransact(int, Parcel, Parcel, int)` 的 before：`code` 不在 `TX_BASE+1..TX_BASE+0xFF` 立即返回（零开销放行原逻辑）；命中则 `data.enforceInterface(DESCRIPTOR)`，处理，`reply.writeNoException()`，`setResult(true)` 截断原方法。
- 鉴权：`UserHandle.getAppId(Binder.getCallingUid()) == appIdOf("cn.rainafter.app.gazekeep")`。appId 在首次请求时经 `PackageManager.getPackageUid` 解析后缓存；包重装时失效重查。
- 回调方向用 AIDL（`xposed/protocol/IGazeKeepClient.aidl`）：

```aidl
oneway interface IGazeKeepClient {
    void onPreTimeout(long seq, long screenOffAtUptime, long screenDimAtUptime, String topPackage, long lastExternalActivityUptime);
    void onHookStatus(int status);
}
```

- v2 回调新增 `screenDimAtUptime`，未知值传 `-1`；App 只接受正数且不晚于真实熄屏的值。
- `linkToDeath`：App 进程死亡 → 清空 `client`，取消待发消息。此后系统完全按原逻辑运行。
- App 侧拿 `power` binder：`HiddenApiBypass.addHiddenApiExemptions("Landroid/os/ServiceManager;")` 后 `ServiceManager.getService("power")`。
- 协议版本不一致（App 升级但未重启）：`ping` 返回旧版本 → readiness = `NeedsSetup(Reboot)`。

#### 9.1.5 App 侧 `LsposedKeepAwakeBackend`

设置页仅调用 `checkReadiness()` 执行 `TX_PING`，不得注册或注销客户端。只有 `GazeService` 的后端实例执行 `attach()` / `detach()`；否则设置页刷新会覆盖单客户端回调，页面退出会切断运行链路。2026-10-05 的单次 Extend 成功记录不代表连续周期稳定，需补充系统活动时间变化与多周期验收。

readiness 判定：

1. `libxposed-service` 的 `XposedServiceHelper` 未连上 → `NeedsSetup(InstallFramework)`（或 Unavailable，取决于是否检测到 root 迹象，不做 root 检测时统一 NeedsSetup）。
2. 作用域不含系统框架 → `NeedsSetup(EnableModuleScope)`。
3. `TX_PING` 无响应（transaction 落到原 `onTransact` 返回 false/异常）→ `NeedsSetup(Reboot)`。
4. `ping` 返回 `Unsupported` → `Unavailable("该系统版本未适配")`。
5. 否则 `Ready`，随即 `TX_REGISTER`。

`extend(Seq(n))` → `TX_EXTEND`；`STALE` 映射为 `ExtendResult.Stale`（用户在判定期间碰了屏，本来就不需要 extend）。`release()` 为空操作（LSPosed 不持有任何东西）。

#### 9.1.6 打包：同一 APK 承载模块

- 现代入口（预留）：`androidApp/src/main/resources/META-INF/xposed/` 保留 `scope.list` 和 `module.prop`；`java_init.list` 要等 libxposed 入口 API 在目标 LSPosed 版本上确认后再加入，避免把不兼容的类声明为入口。
- 旧式入口（兜底）：`assets/xposed_init` 指向 `LegacyEntry`，Manifest `<meta-data>`：`xposedmodule=true`、`xposedminversion=93`、`xposeddescription`、`xposedscope=@array/xposed_scope`。
- 旧式入口只做一件事：构造 `HookFacade` 适配器 → 交给 `HookPlan.install(facade, classLoader)`；`ModernEntry` 保留同一适配点供后续 libxposed 入口转发。包名不是 `android` 的加载回调一律直接返回。
- 入口适配器用静态标志保证 `PowerHookCore` 只初始化一次。
- **M3 第一天验收**：同一 debug APK 既能进启动器、又能被 LSPosed 识别并在勾选系统框架后生效。不过 → 拆 `:xposedApp` 独立 APK（`:xposed` 模块原样复用），App 与模块通过同一 binder 协议通信，配置格式不变。

#### 9.1.7 冷启动恢复

Android 15 起 `BOOT_COMPLETED` 不能直接拉起 `camera` 类型前台服务。LSPosed 路径下，system_server 侧在收到第一次 `onDeadlineComputed` 且无客户端时，以 system 身份 `startForegroundService` 拉起 `GazeService`（每次开机最多一次，且需 App 在上次运行时写入的「已启用」标志——通过 Settings.Global 下的私有键名读取，键名带包名前缀）。**【待验证 M3】** system 身份启动的前台服务是否获得相机 while-in-use 豁免；不行则退回 §10.3 的通知点击恢复。

### 9.2 Shizuku

#### 9.2.1 组成

```
App 进程                                   shell 进程 (uid 2000)
ShizukuKeepAwakeBackend ── bindUserService ─▶ ShellService : IGazeKeepShell.Stub
PowerDumpOracle         ◀─── AIDL ─────────── userActivity / readPower / settings
```

```aidl
interface IGazeKeepShell {
    int version();
    void userActivity(long uptime);                 // IPowerManager.userActivity
    PowerSnapshot readPower();                      // 解析后的结构，不传原始 dumpsys 文本
    int getScreenOffTimeout();
    void setScreenOffTimeout(int millis);           // 仅退路使用
    void grantUsageStats(String pkg);
    void allowBackgroundExemption(String pkg);      // deviceidle whitelist +pkg，需用户在 UI 同意
    void destroy() = 16777114;                      // Shizuku 约定的销毁 transaction
}

parcelable PowerSnapshot { long lastUserActivityUptime; int screenOffTimeoutMs; int dimDurationMs;
                           boolean hasScreenWakeLock; boolean interactive; }
```

#### 9.2.2 绑定与生命周期

- `Shizuku.addBinderReceivedListenerSticky` / `addBinderDeadListener` 驱动 readiness：binder 不在 → `NeedsSetup(StartShizuku)`；`checkSelfPermission() != GRANTED` → `NeedsSetup(GrantShizukuPermission)`；`ShellService.version()` 不匹配 → 重新绑定。
- `UserServiceArgs(ComponentName(pkg, ShellService::class.java.name)).daemon(false).processNameSuffix("shell").version(BuildConfig.VERSION_CODE)`。
- Manifest 声明 `rikka.shizuku.ShizukuProvider`（authority `${applicationId}.shizuku`，`exported=true`，`multiprocess=false`，`permission=android.permission.INTERACT_ACROSS_USERS_FULL`）。
- 重启后 Shizuku 未启动 → binder 不来 → `Auto` 模式降级到无障碍并通知一次「Shizuku 未运行」。

#### 9.2.3 保亮

1. 首选：`ShellService.userActivity` 在 shell 进程内 `IPowerManager.Stub.asInterface(ServiceManager.getService("power"))`，按参数个数反射选择 `userActivity(int displayId, long, int, int)`（Android 12+）或 `userActivity(long, int, int)`。shell 具备 `DEVICE_POWER` **【待验证 M2：12 Pro 的 Shell 包权限表】**。
2. 退路（首选抛 `SecurityException` 时自动启用，设置页标注「兼容模式」）：
   - 判定 Looking：`setScreenOffTimeout(original + 系统超时)`；下一轮非 Looking、或 `release()`、或熄屏：写回 `original`。
   - **崩溃安全**：改值前把 `original` 写入 App 的 DataStore `pendingTimeoutRestore`；`GazeService.onCreate`、Shizuku binder 重连、`ScreenOff` 时检查该键并写回。写回成功才清键。
   - 永不在 `original` 未知时改值。

#### 9.2.4 PowerDumpOracle

shell 进程内对 `power` binder 调 `dump(fd, args)`，在 shell 侧用 `PowerDumpParser` 解析为 `PowerSnapshot` 再返回。解析器以正则容错多版本：

| 字段 | dumpsys 线索（AOSP） |
|---|---|
| `lastUserActivityUptime` | `mLastUserActivityTime=`（Android 13+ 在 PowerGroup 段内） |
| `screenOffTimeoutMs` | 优先 `Screen off timeout:` 有效计算值，缺失才用 `mScreenOffTimeoutSetting=` |
| `dimDurationMs` | `Screen dim duration:` |
| `hasScreenWakeLock` | Wake Locks 段中存在 `SCREEN_BRIGHT_WAKE_LOCK`/`FULL_WAKE_LOCK`，或 summary 含 `WAKE_LOCK_STAY_AWAKE`/`WAKE_LOCK_SCREEN_BRIGHT` |

调度（「追 deadline」轮询，每周期约 1 次 dump）：

```
loop while interactive:
    s = readPower()
    if s.hasScreenWakeLock: 下一次 = now + 系统超时; 不发 deadline（应用自己常亮，引擎休眠）
    else screenOffAt = s.lastUserActivityUptime + s.screenOffTimeoutMs
         screenDimAt = screenOffAt − s.dimDurationMs（缺失或不在 0..timeout 内则未知）
         发 DeadlineUpdated(screenOffAt, screenDimAt)
         下一次读取 = (screenDimAt ?: screenOffAt) − lead − 1_000（在门控前再确认一次）
    若 TouchSensor 可用：任何触摸立即触发重读
```

解析器单元测试覆盖 Android 12/13/14/15 的 dumpsys 样本（样本存放 `backend/shizuku/src/test/resources/dumpsys/`，**只保留 power 段且去掉设备序列号**）。

### 9.3 无障碍

#### 9.3.1 服务配置

`res/xml/accessibility_service_config.xml`：

```xml
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/a11y_description"
    android:accessibilityEventTypes="typeWindowStateChanged|typeViewClicked|typeViewLongClicked|typeViewScrolled|typeViewTextChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:notificationTimeout="100"
    android:canRetrieveWindowContent="false"
    android:isAccessibilityTool="false"
    android:accessibilityFlags="flagDefault" />
```

- 不取 `event.getSource()`，不读文本，只用 `eventType`、`packageName`、`eventTime`。
- `a11y_description` 写明：「仅用于在你注视屏幕时延长亮屏，并感知前台应用以决定是否检测；不读取屏幕内容。」
- `onServiceConnected` → readiness Ready；`onUnbind`/`onDestroy` → 立刻摘 overlay、`Unavailable`，并发通知（规划要求「不残留 overlay」）。

#### 9.3.2 Overlay 与触摸感知（同一个窗口）

```kotlin
val lp = WindowManager.LayoutParams(
    1, 1,
    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
    FLAG_NOT_FOCUSABLE or FLAG_WATCH_OUTSIDE_TOUCH or FLAG_LAYOUT_NO_LIMITS,
    PixelFormat.TRANSLUCENT,
).apply { gravity = Gravity.TOP or Gravity.START; alpha = 0f }
```

- 1×1 透明窗口，亮屏期间常挂，不带 `FLAG_KEEP_SCREEN_ON` 时对系统零影响。
- `FLAG_WATCH_OUTSIDE_TOUCH`：任何落在窗口外的按下都会给我们一个 `ACTION_OUTSIDE`（跨 uid 时坐标被清零，我们只要时间戳）。这是无特权下的补充「用户碰了屏幕」信号；服务同时消费 `TYPE_TOUCH_INTERACTION_START`，两者都只发出 `UserActivity`，不读取 `event.source` 或文本。**【待验证 M1】** 在 HyperOS / Android 14 上 `ACTION_OUTSIDE` 仍会投递。
- 不能加 `FLAG_NOT_TOUCHABLE`（会连 `ACTION_OUTSIDE` 一起收不到）。1 像素的可触摸区域放在左上角状态栏内，实际不影响操作。
- 不需要 `SYSTEM_ALERT_WINDOW`。

#### 9.3.3 保亮：脉冲模式与持有模式

| 模式 | extend | release | 依据 |
|---|---|---|---|
| 脉冲（首选） | 加 `FLAG_KEEP_SCREEN_ON` → 等 300 ms → 去掉 | 空操作 | WMS 的屏幕常亮锁带 `ON_AFTER_RELEASE`，释放时会触发一次用户活动，等价 `userActivity` **【待验证 M1】** |
| 持有（退路） | 加 flag 并保持 | 去掉 flag | 规划原方案；需要 §7.5 的特例处理 |

M1 用「系统超时 30 s、脉冲一次后计时 30 s 熄屏」验证脉冲模式。成立则默认脉冲；不成立则持有，`A11yKeepAwakeBackend` 内部 `mode` 字段决定，引擎通过 `KeepAwakeBackend` 的扩展属性 `extendsSystemTimer: Boolean` 知道是否要由 `IdleClockOracle` 自行推下一个检查点。

#### 9.3.4 IdleClockOracle

```
lastActivity = max(ScreenOn 时刻, KeyguardDismissed 时刻, TouchSensor 最近一次,
                   最近一次用户型无障碍事件, 我们最近一次 extend 完成时刻)
timeout      = Settings.System.SCREEN_OFF_TIMEOUT（ContentObserver 监听变化）
deadline     = lastActivity + timeout
dimDuration  = min(config_maximumScreenDimDuration, timeout × config_maximumScreenDimRatio)
screenDimAt  = deadline − dimDuration（资源缺失 / 非法时未知）
```

- 每次 `lastActivity` 前进就发新的 `DeadlineUpdated`。
- `typeViewScrolled`/`typeViewTextChanged` 可能由应用自身产生（自动滚动、计时器），只有在 1 s 内伴随 `ACTION_OUTSIDE` 或 `typeViewClicked` 时才算用户活动。
- 启发式休眠：`AudioManager.isMusicActive()` 且前台在白名单里 → 本轮不检测（视频播放器通常自己常亮）。
- framework 资源经 `Resources.getIdentifier` + `getInteger` / `getFraction` 读取，资源缺失、异常或非法返回未知，不写死厂商比例。计算放在 `core` 的纯函数并测试。
- 已知误差：系统运行时 dim / 超时覆盖、应用自身的 `FLAG_KEEP_SCREEN_ON`、按键事件（音量键不产生无障碍事件）。无障碍仍可能早几秒拍，或晚到已经开始变暗。
- 估算公式依据 [AOSP framework 配置](https://android.googlesource.com/platform/frameworks/base/+/android16-qpr2-release/core/res/res/values/config.xml)，不将 AOSP 公式视为所有 ROM 的保证。

---

无障碍回归诊断：`GazeService.dump` 只输出开关、后端、状态 / 原因枚举及时间戳，供 ADB 显式查询；不输出帧、分类概率或人脸信息，不写入持久日志。

## 10. Android 组件与清单

### 10.1 Manifest（`androidApp`）

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
          xmlns:tools="http://schemas.android.com/tools">

    <uses-feature android:name="android.hardware.camera.front" android:required="true" />

    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CAMERA" />     <!-- API 34+ -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />            <!-- API 33+ -->
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.PACKAGE_USAGE_STATS"
                     tools:ignore="ProtectedPermissions" />                              <!-- 仅 Shizuku 授予 appop 时生效 -->

    <!-- 白名单列表：只看启动器可见应用，不申请 QUERY_ALL_PACKAGES -->
    <queries>
        <intent><action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" /></intent>
        <intent><action android:name="android.media.action.STILL_IMAGE_CAMERA" /></intent>
        <package android:name="moe.shizuku.privileged.api" />
    </queries>

    <application android:name=".app.GazeKeepApp" android:allowBackup="false" ...>
        <activity android:name=".app.MainActivity" android:exported="true"> ... </activity>

        <service android:name=".service.GazeService"
                 android:exported="false"
                 android:foregroundServiceType="camera" />

        <service android:name=".service.GazeA11yService"
                 android:exported="true"
                 android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
            <intent-filter><action android:name="android.accessibilityservice.AccessibilityService" /></intent-filter>
            <meta-data android:name="android.accessibilityservice"
                       android:resource="@xml/accessibility_service_config" />
        </service>

        <provider android:name="rikka.shizuku.ShizukuProvider"
                  android:authorities="${applicationId}.shizuku"
                  android:exported="true" android:multiprocess="false" android:enabled="true"
                  android:permission="android.permission.INTERACT_ACROSS_USERS_FULL" />

        <receiver android:name=".service.BootRecoveryReceiver" android:exported="false">
            <intent-filter><action android:name="android.intent.action.BOOT_COMPLETED" /></intent-filter>
        </receiver>

        <!-- 旧式 Xposed 元数据（§9.1.6） -->
        <meta-data android:name="xposedmodule" android:value="true" />
        <meta-data android:name="xposeddescription" android:value="@string/xposed_description" />
        <meta-data android:name="xposedminversion" android:value="93" />
        <meta-data android:name="xposedscope" android:resource="@array/xposed_scope" />
    </application>
</manifest>
```

- **禁止出现**：`INTERNET`、`READ/WRITE_EXTERNAL_STORAGE`、`READ_MEDIA_*`、`QUERY_ALL_PACKAGES`、`SYSTEM_ALERT_WINDOW`、`READ_PHONE_STATE`。CI 用 `aapt2 dump permissions` 校验（§13.2）。
- 第三方库可能经 Manifest 合并带入 `INTERNET`（ML Kit 的 bundled 版通常不带，但要查）：显式 `<uses-permission android:name="android.permission.INTERNET" tools:node="remove" />`。
- `allowBackup="false"`：配置里有包名白名单，不进云备份。

### 10.2 GazeService

```kotlin
class GazeService : LifecycleService() {
    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, notifier.serviceNotification(),
            if (SDK_INT >= 30) FOREGROUND_SERVICE_TYPE_CAMERA else 0)
        graph.pendingTimeoutRestorer.restoreIfNeeded()      // Shizuku 退路的崩溃恢复
        graph.engine.start(lifecycleScope)                  // 注册亮灭屏/锁屏广播、相机可用性回调、预热 CameraProvider
    }
    override fun onStartCommand(...) = START_STICKY
    override fun onDestroy() { graph.engine.stop(); graph.backends.releaseAll(); super.onDestroy() }
}
```

- 亮灭屏：动态注册 `ACTION_SCREEN_ON/OFF`、`ACTION_USER_PRESENT`。
- 息屏时：引擎进入 ScreenOff，取消唤醒定时器、注销所有一次性传感器、不持锁、不开相机。服务本身不停。
- 前台通知：低重要性渠道 `service`，文案「注视保亮运行中 · Shizuku」，点击进设置页。不显示判定结果（规划：检测到在看不打扰）。
- `onTimeout`（Android 15 对部分类型的时长限制）不适用于 camera 类型，但仍实现：收到即停止并通知。

### 10.3 后台限制与恢复

| 情形 | 问题 | 方案 |
|---|---|---|
| 用户打开总开关 | — | Activity 可见时 `startForegroundService`，此时获得相机 while-in-use |
| 被 HyperOS 杀进程后 `START_STICKY` 重启 | 后台启动的前台服务访问相机会被拒（Android 11+） | 捕获 `CameraAccessException`/CameraX `CAMERA_DISABLED` → 引擎 Disabled，通知「点此恢复」（通知点击启动属于豁免） |
| 开机 | Android 15 起 `BOOT_COMPLETED` 不能拉起 camera 前台服务 | `BootRecoveryReceiver` 只发「点此恢复」通知；LSPosed 路径另见 §9.1.7 |
| 无障碍已开启 | 系统绑定无障碍服务会拉起进程 | **【待验证 M1】** 此时从 `GazeA11yService` 启动 camera 前台服务能否访问相机；若能，无障碍用户免去手动恢复 |
| Shizuku 可用 | — | 可用 shell 身份执行 `am start-foreground-service` 拉起服务 **【待验证 M2】**；并在 UI 征得同意后 `deviceidle whitelist +pkg` |

**任何静默失效都视为 bug**：服务停了、相机拿不到、后端掉了，用户必须收到一条通知（渠道 `alerts`，默认重要性）。

### 10.4 通知渠道

| 渠道 | 重要性 | 内容 |
|---|---|---|
| `service` | LOW | 前台服务常驻通知 |
| `alerts` | DEFAULT | 后端降级/掉线、相机权限被撤销、无障碍被系统关闭、需要点击恢复、Hook 未适配 |

同一类告警 10 分钟内最多一条。

---

## 11. 配置与日志

### 11.1 ConfigRepository

- DataStore Preferences，文件 `gazekeep_config`。键与 `GazeConfig` 字段一一对应，枚举存 `name`。
- 读取时校验允许值（`leadMillis ∈ {5000, 8000}`、`maxHoldMinutes ∈ {10, 30, 60}`），非法值回落默认，防止手改文件。
- 另有 `internal_state` 文件：`pendingTimeoutRestore`、`lastBackend`、`selfTestPassedAt`、`onboardingDone`。
- 跨进程：只有主进程读写 DataStore。system_server 不读配置，所需的 `leadMillis` 在 `TX_REGISTER` 时传入；shell 进程不读配置。

### 11.2 DecisionLog

```kotlin
data class DecisionRecord(
    val epochMillis: Long,
    val foregroundPackage: String?,
    val attention: Attention?,           // 未进入采帧时为 null
    val frameReasons: List<FrameVerdict.Reason>,
    val skip: SkipReason?,
    val abort: AbortReason?,
    val backend: BackendId?,
    val extended: Boolean,
    val captureLatencyMs: Long?,
)
```

- 始终保留内存环形缓冲（最近 50 条）供隐私页显示「最近一次判定：在看 / 没在看 / 这次没看清」。
- `debugLogEnabled` 时追加到 `filesDir/decisions/yyyyMMdd.jsonl`，每次写入时删除 24 小时前的文件。
- **只存枚举、包名、时间与耗时。** 不存图像、人脸框、欧拉角、概率。调试页可以实时显示欧拉角与概率（自检向导用），但这些值不进入 `DecisionRecord`。

### 11.3 运行日志

- App 进程：`GkLog`（封装 `android.util.Log`，tag 前缀 `GazeKeep/`），release 只保留 `w`/`e`。
- system_server：只通过 Hook 框架的日志接口（`XposedModule.log` / `XposedBridge.log`），每类消息限频；不做崩溃上报。
- 不接任何第三方统计/崩溃 SDK（也就不需要网络权限）。

---

## 12. UI 架构（`:shared`）

### 12.1 边界

```kotlin
// commonMain
interface GazeKeepController {
    val state: StateFlow<ControllerState>
    suspend fun updateConfig(transform: (GazeConfig) -> GazeConfig)
    suspend fun setEnabled(enabled: Boolean): EnableResult   // 未通过自检/缺权限时拒绝
    val liveVerdicts: Flow<LiveVerdict>                      // 仅自检/调试页订阅期间产生
}

interface PlatformActions {
    fun requestCameraPermission(onResult: (Boolean) -> Unit)
    fun requestNotificationPermission(onResult: (Boolean) -> Unit)
    fun openAccessibilitySettings()
    fun requestShizukuPermission()
    fun openShizukuApp()
    fun requestIgnoreBatteryOptimizations()
    fun openAutoStartSettings()                               // HyperOS 自启动页，失败回落应用详情
    fun loadLaunchableApps(): List<AppEntry>                  // 名称 + 包名 + 图标 ImageBitmap
}

data class ControllerState(
    val config: GazeConfig,
    val activeBackend: BackendId?,
    val backends: Map<BackendId, Readiness>,
    val cameraGranted: Boolean,
    val lastDecision: UserFacingVerdict?,                     // 在看 / 没在看 / 这次没看清
    val serviceRunning: Boolean,
)
```

`androidApp` 实现两者并在 `MainActivity` 中 `App(controller, actions)`。`commonMain` 里不得 import `android.*`。

### 12.2 页面与导航

- 自写极简回退栈：`sealed interface Screen { Home; Backend; Detection; Scope; AppPicker; Protection; Privacy; SelfTest; Debug }`，`SnapshotStateList<Screen>` + 平台返回键处理。不引入导航库。
- 视觉统一使用 miuix 组件（`Scaffold`、`TopAppBar`、`SuperSwitch`、`SuperDropdown`、`SuperArrow`、`Card` 等），信息架构严格按规划 §6 的树。
- 主界面状态只用三种用户语言；欧拉角与概率仅出现在 `SelfTest` 实时面板和 `Debug` 页。`Debug` 入口：关于里连点版本号 7 次。
- 字符串放 `shared/src/commonMain/composeResources/values/strings.xml`（中文默认）；英文以后加 `values-en`。隐私三句话作为单独的字符串资源，与规划逐字一致。

### 12.3 自检向导

1. 选后端：列出三种后端及 readiness，推荐项置顶（有 LSPosed → LSPosed；否则 Shizuku；否则无障碍）。
2. 授权：按所选后端依次引导相机、通知、Shizuku/无障碍、忽略电池优化、HyperOS 自启动。
3. 自检：`SelfTestScreen` 使用 CameraX `Preview`（仅此页有预览，Activity 生命周期绑定）+ 同一个 `FrameClassifier`。要求连续完成「正视 → 判为在看」与「转头 → 判为不在看」各一次，通过后写 `selfTestPassedAt`。
4. 勾选应用：默认建议「最近使用的那一个」（来自无障碍/UsageStats，可用时），列表为空也允许完成，但总开关打开时提示「白名单为空，不会生效」。

`setEnabled(true)` 在 `selfTestPassedAt == null` 或相机未授权时返回 `EnableResult.NeedsSelfTest` / `NeedsCamera`。

---

## 13. 构建、发布与质量门禁

### 13.1 构建变体

- `debug`：`applicationIdSuffix` **不使用**（LSPosed 作用域与 Shizuku authority 都基于包名，后缀会导致模块与 App 身份分裂）。debug 与 release 不能共存安装，这是有意的。
- `release`：R8 + 资源压缩，签名配置从 `local.properties`/环境变量读取，不入库。

### 13.2 静态检查

- `./gradlew :androidApp:lintRelease`。
- 权限白名单脚本：构建后 `aapt2 dump permissions androidApp/build/outputs/apk/release/*.apk`，出现 §10.1 禁止项即失败。
- 依赖检查：`./gradlew :androidApp:dependencies --configuration releaseRuntimeClasspath` 中不得出现网络、统计、广告类库。

### 13.3 R8 keep

```
# Xposed 入口与 system_server 侧代码：旧式类名写在 xposed_init 里；现代入口清单待 API 确认后加入
-keep class cn.rainafter.app.gazekeep.xposed.entry.** { *; }
-keep class cn.rainafter.app.gazekeep.xposed.core.** { *; }
# AIDL 与 Shizuku UserService（由 Shizuku 按类名反射构造）
-keep class cn.rainafter.app.gazekeep.backend.shizuku.ShellService { <init>(...); }
-keep class * implements android.os.IInterface { *; }
# libxposed / xposed api 均为 compileOnly
-dontwarn io.github.libxposed.api.**
-dontwarn de.robv.android.xposed.**
```

### 13.4 已测系统

每次发布更新 `docs/compat/tested-systems.md`：机型、ROM 版本号、Android 版本、LSPosed 发行版与 API 版本、`DeadlineResolver` 命中的候选（A/B/C）、三条手工用例结果。

---

## 14. 测试策略

| 层 | 范围 | 方式 | 位置 |
|---|---|---|---|
| 纯逻辑 | `EngineReducer`、`AttentionVoter`、`GatePolicy`、`HoldPolicy`、`LeadBackoff`、`judgeFeatures` | kotlin-test + 虚拟时钟，表驱动；覆盖规划 §14 手工矩阵里能抽象成事件序列的全部用例 | `core/src/commonTest` |
| 解析 | `PowerDumpParser` | 多版本 dumpsys 样本 | `backend/shizuku/src/test` |
| 协议 | `BridgeProtocol` 编解码、鉴权分支、`seq` 过期 | JVM 测试，`PowerHookCore` 依赖接口注入 | `xposed/src/test` |
| 配置 | 非法值回落、`pendingTimeoutRestore` 流程 | Robolectric 或 host test | `engine/src/test` |
| 设备 | 相机、无障碍、Shizuku、LSPosed | 手工矩阵（规划 §14），12 Pro 必测 + 一台 Android 14 非小米 | 结果记 `docs/testing/` |
| 阈值 | 分类器阈值 | M0 计数表冻结；改分类器或阈值必须重跑 | `docs/testing/m0-thresholds.md` |

必须有单测的不变量（缺一不可合入）：

1. 任何 `Unknown` 结论都不产生 `Extend` 副作用。
2. `UserActivity` 在 Capturing 中到达 → `CancelCapture` 且不 `Extend`。
3. 达到 `maxHoldMinutes` 后的下一轮必定 Yield。
4. `ScreenOff` 在任何状态下都产生 `Release` 且不再产生 `StartCapture`，直到下一次 `ScreenOn`。
5. 多脸 → 非 Looking。
6. 后端切换期间不产生 `Extend`。
7. `extend(seq)` 对过期 seq 返回 STALE 且不调用 `userActivity`。

---

## 15. 里程碑对应的交付物

| 里程碑 | 新增/改动模块 | 关键交付 | 验证（与规划 §13 一致） |
|---|---|---|---|
| M0 | `core`（model、voter、judgeFeatures）、`engine/camera`、`engine/classify`、`shared` 调试页 | 前摄三帧 + 「在看/没在看/没看清」+ 耗时；`m0-thresholds.md` | 正视 9/10 Looking；转头 0/10；闭眼不 Looking；记录 CameraX 打开延迟决定是否换 Camera2 |
| M1 | `core/engine`（reducer 全量）、`engine/gate`、`engine/oracle`、`backend/a11y`、`GazeService` | 第一个每天可用版本 | 30 s 超时盯 2 分钟不灭；转头后下一轮熄；打开系统相机不崩溃不占用；验证脉冲模式、`ACTION_OUTSIDE`、无障碍拉起相机前台服务 |
| M2 | `backend/shizuku`、`hidden-api` | `userActivity` 优先，改超时退路 + 崩溃恢复 | 同 M1 三条且无 overlay；重启后 Shizuku 掉线有通知 |
| M3 | `xposed`、`backend/lsposed` | 第一天：同 APK 打包实验 + 反编译确认钩点 | 重启后作用域只有系统框架即生效；故意改错方法名仍能开机且显示未适配 |
| M4 | `shared` 全量 miuix 设置页、自检向导、`BackendSelector` 自动降级 | 可给别人装的版本 | 规划 §14 全矩阵 |

---

## 16. 关键风险的技术对策汇总

| 风险 | 落点 |
|---|---|
| 隐私舆情 | §1.1 原则 3、§10.1 禁止权限 + CI 校验、§11.2 日志字段白名单 |
| 误常亮烧屏 | §7.1 HoldPolicy、§7.4 不可配置失败策略、§6.2 多脸不保亮、§14 不变量 1/3 |
| HyperOS 杀后台 | §10.3 恢复路径、§10.4 告警渠道、§12.3 引导自启动与电池优化 |
| PMS 签名变化 / bootloop | §9.1.2 DeadlineResolver 逐候选解析 + 失败 no-op + 熔断、§13.4 已测系统 |
| 钩子拖慢系统 | §9.1.2 锁内回调限制、独立 HandlerThread、oneway 回调 |
| Shizuku 退路残留超时值 | §9.2.3 `pendingTimeoutRestore` 崩溃恢复 |
| 无障碍 overlay 残留 | §9.3.1 服务解绑即摘 overlay |
| KMP 与 LSPosed 打包冲突 | §9.1.6 M3 第一天验收 + 拆 APK 预案（`:xposed` 模块不变） |
| 相机抢占 | §5 门控 #5、§6.1 中止路径 |

---

## 附录 A：术语

| 术语 | 含义 |
|---|---|
| deadline | 系统本次计划熄屏的时刻（`uptimeMillis` 基准） |
| lead | 提前量，在 deadline 前多久开始检测 |
| extend | 以「用户活动」的语义重置系统超时一次 |
| Yield | 本轮放行，让系统按原逻辑熄屏 |
| 外部活动 | 不是由本 App extend 产生的用户活动（触摸、按键等） |
| 脉冲模式 / 持有模式 | 无障碍后端两种保亮实现（§9.3.3） |

## 附录 B：待验证清单（按里程碑）

| 里程碑 | 项 |
|---|---|
| M0 | CameraX 前摄打开延迟；ML Kit bundled 在 HyperOS 上的推理耗时；AE 预热帧数；`minFaceWidthRatio` 实际值 |
| M1 | 无障碍 overlay 上 `FLAG_KEEP_SCREEN_ON` 生效；`ON_AFTER_RELEASE` 脉冲等价 userActivity；`ACTION_OUTSIDE` 投递；无障碍拉起的相机前台服务能否用相机 |
| M2 | shell 是否具备 `DEVICE_POWER`；`IPowerManager.userActivity` 签名；dumpsys 字段；shell 拉起前台服务的相机豁免 |
| M3 | libxposed API 发布渠道与回调名；scope 中系统框架的写法；钩点 A/B/C 在 12 Pro ROM 上的签名；system 身份拉起前台服务的相机豁免；同 APK 打包 |
