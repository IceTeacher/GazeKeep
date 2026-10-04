# 注视保亮 GazeKeep 产品与技术规划

| 项 | 内容 |
|---|---|
| 产品名 | 注视保亮 |
| 英文名 | GazeKeep |
| 建议包名 | `dev.gazekeep.app` |
| LSPosed 模块名 | GazeKeep |
| 文档版本 | v1.0 |
| 状态 | 规划，未开工 |
| 目标机 | 小米 12 Pro（HyperOS，无原生「检测注视」）及同类老机 |

一句话：超时前用前置摄像头看一眼。你还在看屏幕，就不熄屏。帧不落盘、不上云。

---

## 1. 背景与问题

阅读、菜谱、导航讲解、修车手册这类场景里，用户眼睛在屏幕上，手不一定在点。系统超时一到就熄屏，再亮又要解锁。把超时拉到 10 分钟或「永不」又浪费电，锁屏也变慢。

厂商已经做过这件事，但覆盖是碎的：

| 厂商 | 功能 | 覆盖 |
|---|---|---|
| 三星 | Smart Stay / 观看时保持屏幕开启 | Galaxy 上很早、很广 |
| Pixel | Screen Attention | 主要在 Pixel |
| 小米 HyperOS | 主动视觉感知 → 检测注视 | 较新旗舰常见，不是全系 |

小米官方口径：检测到你在看屏幕则不锁定；不做面部特征分析，人脸信息不存设备、不上云。同版本 HyperOS 下，有的机有「主动视觉感知」，有的机没有。小米 12 Pro（2022）走 MIUI 14 → HyperOS，系统里通常没有这套开关。2020–2023 的中旗舰（12 系列、K50、Find X5、一加 10 等）正好卡在「前摄够用、系统没给这个开关」。

商店里的常亮 App（Wakey、Caffeine、KinScreen）按应用、充电或体感保亮，几乎没有「注视检测 + 系统级重置超时 + 多特权后端」这一套。老库 Prevent-Screen-Off、ISeeYou 已停更，而且只绑在自己的 Activity 上。LSPosed 仓库里多的是反向模块（禁止应用常亮、强制 AOD），没有现成的注视保亮模块。

## 2. 产品定义

### 2.1 要解决的事

在任意前台应用里，系统即将按超时熄屏时，做一次极短的前摄检测。判定用户仍在注视，则重置这次超时；判定离开、失败或不可信，则放行熄屏。

### 2.2 不是什么

- 不是眼动仪，不估计瞳孔落在哪个像素。
- 不是「永不熄屏」。离开、口袋、暗光失败都必须熄。
- 不是无障碍读屏。无障碍后端只用于保亮和感知亮灭屏，不读取窗口内容。
- 不是后台录像。一次检测 1～3 帧，推理完立刻关相机。
- 不是云端人脸识别。模型与帧都在端上，帧不写相册、不写日志。

### 2.3 成功标准（用户能感知的）

- 阅读长文时，盯着屏幕超过系统超时仍不灭。
- 把头转开或放下手机，在一个超时周期内正常熄屏。
- 微信扫一扫、相机 App 打开时，本功能立刻让出摄像头，不抢占。
- 设置页看起来像 HyperOS 设置，而不是又一个常亮工具箱。

## 3. 用户与场景

### 3.1 用户

| 人群 | 特征 | 主路径 |
|---|---|---|
| 进阶小米用户 | 会开无障碍或会装 LSPosed，讨厌超时调太长 | 三种后端都可能 |
| Root 用户 | Magisk / KernelSU / APatch + LSPosed | LSPosed |
| 不 Root 的老机用户 | Android 11+，愿意配对一次无线调试 | Shizuku |
| 只愿意点权限的用户 | 接受无障碍 + 相机 | 无障碍降级 |

不会为这个功能 Root 的普通用户是最大盘，但也是体验最容易「有时灵有时不灵」的一档。对外不要承诺「所有老机都达到系统级」。

### 3.2 场景

**要生效**

- 浏览器、阅读器、微信长文、备忘录、菜谱、PDF。
- 屏幕亮着、无触摸、距超时还有数秒、脸正对屏幕且双眼睁开。

**要休眠，不要抢系统**

- 通话、导航、视频播放（系统或应用自己会 `KEEP_SCREEN_ON`）。
- 前台是相机、扫码、人脸解锁相关进程。
- 锁屏、息屏、口袋（接近传感器或姿态判断）、强逆光连续失败。

**白名单优先于全局。** 第一版默认不全局开启，只对用户勾选的应用生效，避免游戏和银行 App 上的悬浮窗冲突变成差评。

## 4. 市场判断

需求真实，但付费盘不大，信任比功能更先决定生死。

- 可触达存量是「2020 年后仍在网、非三星、非最新小米旗舰」。装得上的是会开无障碍或会装模块的人，量小、口碑传播快。
- 差异化就四句：超时前才拍；三种安装形态；给老机补上新机才有的检测注视；本地推理，对齐官方隐私口径。
- 不要做成广告 App。相机权限加广告等于信任崩盘。买断，或以后把「暗光增强 / 按应用规则 / 更重的注视模型」做成一次性解锁。
- 渠道：LSPosed 模块走 modules.lsposed.org 与酷安；完整 App 走酷安与 GitHub。Google Play 对「无障碍 + 相机」审查严，上架版应能在无 LSPosed 时独立运行，并写清无障碍用途是延长亮屏，不是读取屏幕。
- 最大产品风险：隐私舆情、误常亮导致 AMOLED 烧屏（12 Pro 是 AMOLED）、MIUI/HyperOS 杀后台导致时灵时不灵。

## 5. 范围

### 5.1 v1 要做

- 总开关、灵敏度、检测提前量、应用白名单、隐私说明。
- 端上注视判定（默认 ML Kit，可选增强模型）。
- 三种保亮后端，启动时自动探测，设置页标明推荐 / 可用 / 不可用。
- 相机让出、暗光与口袋失败则熄屏、连续注视后拉长下一次检测间隔。
- 小米风格设置 UI（KMP + miuix）。

### 5.2 明确不做

- 真·视线落点、眼控滚动、隔空手势。
- 人脸注册、谁在看的身份识别。
- 把推理跑进 `system_server`。
- iOS。
- 云同步配置（第一版本地即可）。

## 6. 体验

信息架构对齐 HyperOS「主动视觉感知」，不要堆工具箱。

```
注视保亮
├── 总开关
├── 当前后端          LSPosed / Shizuku / 无障碍 / 未就绪
├── 检测
│   ├── 提前量        超时前 5 秒 / 8 秒
│   ├── 灵敏度        宽松（有正脸即可）/ 标准（正脸 + 睁眼）
│   └── 失败策略      不可信就熄屏（默认，不可关）
├── 范围
│   ├── 仅白名单应用  默认开
│   └── 应用列表
├── 保护
│   ├── 相机占用时暂停
│   ├── 暗光连续失败则本轮熄屏
│   └── 单次最长保亮  防止误判烧屏，例如 30 分钟后强制走系统超时
└── 隐私
    └── 帧不保存、不上传；可看最近一次判定的文字结果，不看图
```

状态只有三种用户语言：在看、没在看、这次没看清。不要把 Euler 角和概率露到主界面。调试页可以有，默认关。

交互原则：

- 检测到「在看」不弹 Toast，不闪图标。静默重置超时。
- 只有后端掉线、相机被拒绝、无障碍被系统关掉时才通知。
- 首次开启用三步：选后端 → 授权 → 用前置摄像头对着自己做一次「在看 / 转头」自检。自检通过才算开启成功。

## 7. 架构

拆成两层。判断注视，任何有相机权限的进程都能做。在第三方 App 前重置系统超时，才需要特权。

```
┌─────────────────────────────────────────────┐
│  UI（KMP + miuix）                           │
│  总开关 / 白名单 / 后端状态 / 隐私             │
└──────────────────────┬──────────────────────┘
                       │ 本地配置
┌──────────────────────▼──────────────────────┐
│  GazeEngine（本 App 进程，前台服务）           │
│  TimeoutOracle → 快门 1～3 帧 → Classifier   │
│  相机占用检测、光线/接近/姿态门控              │
└──────────────────────┬──────────────────────┘
                       │ Looking / Away / Unknown
        ┌──────────────┼──────────────┐
        ▼              ▼              ▼
   LSPosed        Shizuku         无障碍
   Hook PMS       userActivity    Overlay
   userActivity   或临时改超时     FLAG_KEEP_SCREEN_ON
```

三个接口固定，后面所有后端只实现最后一个：

```kotlin
enum class Attention { Looking, Away, Unknown }

interface AttentionClassifier {
    suspend fun analyze(frames: List<ImageProxy>): Attention
}

interface TimeoutOracle {
    /** 距离系统本次熄屏还剩多少毫秒；未知时返回 null，引擎不拍。 */
    fun millisUntilScreenOff(): Long?
}

interface KeepAwakeBackend {
    val id: BackendId          // Lsposed, Shizuku, Accessibility
    val readiness: Readiness   // Ready, NeedsSetup, Unavailable
    suspend fun extend(reason: String)
    suspend fun release()
}
```

探测顺序：LSPosed 作用域可用 → Shizuku binder 活着 → 无障碍已开启。高优先级后端掉线时自动降级，并通知一次。用户也可以手动锁定某一个后端。

进程边界：

- 管理 UI 与推理都在 App 进程。
- LSPosed 只做两件事：把「即将超时」告诉 App，以及在 `system_server` 里调用 `userActivity`。不在系统进程里跑模型，不在系统进程里开相机。
- 帧的生命周期停在 `ImageAnalysis` 回调里。分析完 `close()`，不 `Image.capture` 到文件。

### 7.1 时序

默认系统超时 30 秒、提前量 8 秒时：

1. 亮屏且无触摸，空闲到约 22 秒。
2. 若前台包名不在白名单、或正在通话/导航/相机占用，本轮结束，让系统自己熄。
3. 否则拉起前摄，取 1～3 帧（间隔约 150 ms，避开单帧眨眼）。
4. 判定：
   - `Looking`：后端 `extend()`，关闭相机。下一次检测间隔拉长（例如跳过一轮，或提前量改为 4 秒），避免盯读时每 20 秒拍一次。
   - `Away`：`release()`，让系统超时熄屏。
   - `Unknown`（太暗、没脸、侧脸、眼镜反光）：**当作 Away**。连续未知不保亮。这是防烧屏的默认策略，不是可选项。
5. 用户中途触摸，取消本轮相机，把节奏交还系统。
6. 单次连续保亮达到上限（建议 30 分钟，可调 10 / 30 / 60），下一轮强制 `Away`，屏幕按系统超时熄一次。用户再亮屏后重新计数。

## 8. 注视判定

目标是「人是否在看这块屏」，不是注视点估计。

### 8.1 默认：ML Kit Face Detection

对每一帧：

- 检测到恰好一张脸（多张脸：不保亮，避免把旁边的人当成用户）。
- 脸宽相对画面不低于阈值（太远等于没在看手机）。
- `|headEulerAngleY|` 与 `|headEulerAngleX|` 较小。分类可靠区间大约 ±18°，产品阈值可以放到约 ±25°，超出直接 `Unknown`。
- `leftEyeOpenProbability` 与 `rightEyeOpenProbability` 都高于阈值。标准灵敏度约 0.6；宽松模式可以只要求「有正脸」，但默认不要宽松，闭眼打瞌睡不该保亮。
- 三帧里至少两帧为 Looking，才输出 Looking。单帧眨眼不应灭屏，也不应单帧误保亮。

ML Kit 开合分类只对近似正面脸有效。侧脸不要硬判睁眼，直接 Unknown → 熄屏。

依赖：`com.google.mlkit:face-detection`（bundled，不走 Play Services 动态下载，老机和国行更稳）。CameraX `ImageAnalysis`，分辨率能看清脸即可（建议分析流 640×480 或更低），不要用拍照流的全尺寸。

### 8.2 增强：MediaPipe Face Landmarker

478 点 + `eyeBlinkLeft` / `eyeBlinkRight` blendshape + 虹膜相对眼眶。暗光、眼镜、轻微侧脸比 ML Kit 稳，模型更大。作为设置里的「精确模式」，不作为默认。minSdk 24 够用，但要单独量 12 Pro 上的耗时，单次三帧应控制在几百毫秒内，不能拖过剩余超时。

### 8.3 不做的模型

L2CS-Net、iTracker 一类注视方向模型留给以后。它们要标定，眼镜和光线都脆，对「防误熄」的收益不够付这个复杂度。

### 8.4 门控（在开相机之前就做）

能用传感器挡住的，就不要开相机：

- 接近传感器：贴脸或在口袋 → 不拍，放行熄屏。
- 光线：极暗且前几轮已经 Unknown → 本轮不拍。
- 加速度/陀螺：屏幕朝下 → 不拍。
- 相机被占用（`CameraManager.AvailabilityCallback`）→ 不拍。
- 省电模式、低电量（例如 <15%，可关）→ 可选暂停，默认仍检测，因为这正是用户在乎的场景；电量策略做成开关，默认关。

## 9. 三种保亮后端

### 9.1 LSPosed（能力完整）

条件：Root（Magisk Zygisk / KernelSU / APatch）+ LSPosed 或仍在维护的兼容叉。

做法：

- 作用域：`android`（system_server）必选；管理 App 自己可选。不要勾一堆第三方 App。
- 在 `PowerManagerService` 即将处理用户活动超时、调用熄屏之前，给 App 一个短窗口去检测。判定 Looking 后，在 system_server 上下文调用 `userActivity`，等价于「用户刚碰了一下屏幕」。不要用改 `screen_off_timeout` 这种会改设置项数字的办法。
- 备选钩点是 WindowManager 已有的 user-activity timeout override。以能在 12 Pro 当前 HyperOS 上编过、重启后不 bootloop 为准，钩子尽量少。
- 模块与 App 可以是同一个 APK：`assets/xposed_init` 或现代 LSPosed 入口指向一个很薄的 Hook 类，UI 仍是普通 Activity。

版本：按所选 LSPosed 发行版，实务上 Android 8.1 到 15/16。小米 12 Pro 在这个区间内。ROM 一改 `PowerManagerService` 方法签名就要发适配版，所以 Hook 必须对签名失败打日志并安全 no-op，不能抛到系统进程外面。

### 9.2 Shizuku（无 Root 的半系统）

Shizuku 提供的是 ADB 级权限，不是相机特权。相机仍要用户授权。

保亮手段，按干净程度：

1. 通过 `IPowerManager` 调用 `userActivity`。不改设置项，不需要悬浮窗。优先。
2. 退路：`settings put system screen_off_timeout` 临时拉长，检测结束或判定离开后立刻写回原值。会在系统设置里闪一下数字，只做退路。

版本：

- Shizuku 本体大约 Android 6+。
- 无线调试配对是 Android 11+。小米 12 Pro 走这条。
- 重启后 Shizuku 默认要再启动，除非用户用的是 Root 模式 Shizuku。后端掉线要降级，不要静默失效。

### 9.3 无障碍（保底）

无障碍拿不到 `DEVICE_POWER`，不能合法调用 `PowerManager.userActivity`。

做法：

- `AccessibilityService` 使用 `TYPE_ACCESSIBILITY_OVERLAY`。
- 一层几乎全透明、不拦截触摸的窗口，加上 `FLAG_KEEP_SCREEN_ON`。
- Looking 时挂上 flag，Away / Unknown / 服务关闭时拿掉。
- 服务同时提供亮灭屏与前台包名，用来决定要不要开相机。`canRetrieveWindowContent` 保持关闭。

限制：全屏游戏、部分银行 App、折叠外屏可能把 overlay 挤掉或自己清掉常亮。这是降级路径，设置页要写明「可能对部分全屏应用无效」。

版本：服务本身 API 21+ 就有；实用建议 API 26+。后台相机在 Android 11 起明显收紧，Android 14 起前台服务必须声明 `foregroundServiceType="camera"`。所以无障碍路径的「能跑」和「能稳」不是同一个版本号：API 26 能挂 overlay，API 34 才是相机前台服务的完整形态。第一版 `minSdk` 建议直接定在 **26**，相机相关清单按 34 的类型声明写好，低版本走不带 type 的前台服务。

### 9.4 对照

| 后端 | 保亮手段 | 相机 | 实用系统版本 | 用户成本 | 体验 |
|---|---|---|---|---|---|
| LSPosed | system_server 内 `userActivity` | App 进程前台服务 | 8.1+，看框架 | Root + 模块 + 重启 | 最好，全局 |
| Shizuku | `IPowerManager.userActivity`，退路改超时 | 同上 | 11+ 最顺 | 无线调试配对一次 | 接近系统，无悬浮窗 |
| 无障碍 | 无障碍悬浮窗 + `FLAG_KEEP_SCREEN_ON` | 同上；14+ 要 camera FGS | 8.0+ 能挂窗，11+ 相机才稳，14+ 类型完整 | 无障碍 + 相机 + 忽略电池优化 | 日常阅读够用，全屏应用可能失效 |

小米 12 Pro：三条都能做。默认推荐顺序仍是 LSPosed > Shizuku > 无障碍。没有 Root 时，设置页把 Shizuku 放在无障碍前面，并写清「配对一次，之后不用悬浮窗」。

## 10. 权限与清单

| 权限 / 能力 | 用途 | 何时要 |
|---|---|---|
| `CAMERA` | 超时前的 1～3 帧 | 始终。运行时申请，拒绝则功能不可用 |
| 前台服务 | 亮屏期间守着超时点 | 始终。类型在 API 34+ 包含 `camera` |
| 忽略电池优化 | 对抗 HyperOS 杀后台 | 引导，不强制；杀后台是 MIUI 上的第一支持问题 |
| 无障碍 | 仅无障碍后端 | 该后端启用时 |
| Shizuku | 仅 Shizuku 后端 | 该后端启用时 |
| 悬浮窗 | 尽量不要 | 无障碍 overlay 用无障碍窗口类型，不单独要 `SYSTEM_ALERT_WINDOW` |
| 通知 | 后端掉线 | Android 13+ 的通知权限 |

不要申请：存储、相册、网络（模型 bundled）、`QUERY_ALL_PACKAGES`（白名单用启动器可见应用列表加用户搜索）、无障碍的窗口内容。

隐私文案固定三句，和检测结果页放在一起：

1. 只在快要熄屏时打开前置摄像头，看完立刻关闭。
2. 画面只在内存里判断「在不在看」，不保存、不上传。
3. 看不清、没看清、旁边有别人，都会让屏幕正常熄灭。

## 11. 工程结构

UI 用 Kotlin Multiplatform + [miuix](https://github.com/compose-miuix-ui/miuix)（`top.yukonga.miuix.kmp`）。理由很窄：设置页要像小米系统设置，而这个库就是 Compose Multiplatform 的 MIUI 风格组件。业务不要写进 commonMain 的 UI 层之外还假装跨平台——相机、PowerManager、LSPosed、Shizuku 全是 Android。

```
gazekeep/
├── composeApp/                 # KMP UI，androidMain 接后端
│   └── src/commonMain/         # 设置页、文案、纯状态
│   └── src/androidMain/        # 导航、权限、服务绑定
├── engine/                     # Android library
│   ├── camera/                 # CameraX 快门，用完即关
│   ├── classify/               # MlKitClassifier，以后加 MediaPipe
│   ├── oracle/                 # 读超时、估算剩余时间
│   └── gate/                   # 接近、光线、朝向、相机占用
├── backend-api/                # Attention / Backend 接口
├── backend-a11y/
├── backend-shizuku/
├── xposed/                     # 薄 Hook，同一 applicationId 或独立 dex
└── app/                        # application 模块，聚合 manifest
```

如果 KMP 让 LSPosed 打包变麻烦，UI 可以退回纯 Android Compose，仍然用 miuix 的 Android artifact。不要为了 KMP 牺牲 Hook 的可维护性。第一周就验证：同一个 debug APK 既能进启动器，又能被 LSPosed 识别。验证不过，UI 与模块拆成两个 APK，配置用同一套文件格式。

配置（本地，DataStore）：

- `enabled: Boolean`
- `backendLock: Auto | Lsposed | Shizuku | A11y`
- `leadMillis: 5_000 | 8_000`
- `sensitivity: Standard | Loose`
- `whitelistEnabled: Boolean` 默认 true
- `packages: Set<String>`
- `maxHoldMinutes: 10 | 30 | 60` 默认 30
- `classifier: MlKit | MediaPipe` 默认 MlKit

不要存图像、人脸向量、检测帧。调试日志只存枚举：时间、包名、Attention、后端、是否 extend。默认关，开了也 24 小时滚动删除。

### 11.1 TimeoutOracle

无特权时，App 读得到 `Settings.System.SCREEN_OFF_TIMEOUT`，但读不到「这次触摸之后已经空闲了多久」的系统内部时钟。无障碍可以近似：最后一次触摸/按键/滚动手势的时间戳自己记，用 `timeout - idle` 估算。误差来自系统 dim 阶段和应用自己的 `FLAG_KEEP_SCREEN_ON`。

因此：

- 无障碍 / 纯 App：自己的空闲时钟，只是近似。检测到前台窗口带常亮 flag（若拿不到这个信息）就本轮休眠。
- Shizuku：可以 dumpsys 或读 power 状态，尽量用系统空闲时间，而不是自己猜。
- LSPosed：不猜。钩在真正要超时的那一下，检测窗口由系统事件触发，这是这条路径最值钱的地方。

无特权路径要接受「可能早几秒拍，或晚到已经开始变暗」。变暗开始仍算来得及。屏幕已经灭了就不要为了补拍再亮屏。

## 12. 功耗与烧屏

12 Pro 的算力不是瓶颈，开关相机的次数才是。

预算（目标，不是承诺）：

- 空闲亮屏、用户在看：平均每分钟不超过 2 次开相机，每次 ≤3 帧，相机打开时间 <1 秒。
- 连续 Looking 后指数退避，上限到「每个系统超时周期最多 1 次」。
- 检测进程空闲时不能持有 partial wake lock。只在真正开相机的那一秒持有。
- 息屏后服务可停，亮屏广播再拉起。不要为了「时刻准备」常驻相机。

烧屏：

- `maxHoldMinutes` 默认 30，到点必须熄一次。
- Unknown 不保亮。
- 不提供「失败也保持常亮」开关。

## 13. 里程碑

每一段都要在小米 12 Pro 上用「阅读器 + 转头 + 打开相机」三条手工用例收口，再进入下一段。

### M0 自检（3–5 天）

纯 App，无特权。一个页面：打开前摄，实时显示 Looking / Away / Unknown。验证 ML Kit bundled 在 12 Pro HyperOS 上的延迟、眼镜、暗光、侧脸。没有这一步，后面的后端都是在猜阈值。

退出标准：室内正常光，正视 10 次至少 9 次 Looking；转头 90° 10 次全部不是 Looking；闭眼不判 Looking。

### M1 无障碍后端（1 周）

白名单 + overlay 保亮 + 超时前快门。这是第一个能每天自己用的版本。

退出标准：系统超时 30 秒，阅读器里盯着超过 2 分钟不灭；转头后下一轮熄屏；打开系统相机时本 App 不崩溃、不占着相机。

### M2 Shizuku（1 周）

`userActivity` 优先，改 timeout 作退路。去掉悬浮窗。重启后的掉线提示。

退出标准：与 M1 相同的三条用例，且状态栏没有本 App 的常亮 overlay。

### M3 LSPosed（1–2 周）

薄 Hook，签名对不上就 no-op。同一 APK 或拆分的决定在这一段开头落地。

退出标准：重启后模块启用，作用域只有系统框架；微信/浏览器/阅读器都生效；故意改一个错误的方法名时手机仍能开机，模块日志说明未挂钩。

### M4 设置与保护（数天）

miuix 设置页、自检向导、30 分钟上限、退避、隐私页、后端自动选择。到这里才是可给别人装的版本。

### 以后，不排进 v1

MediaPipe 精确模式、按应用不同灵敏度、暗光模型、折叠屏、Play 上架裁剪版。

## 14. 测试

手工矩阵（12 Pro 为必测，再找一台 Android 14 非小米）：

| 用例 | 期望 |
|---|---|
| 白名单内正视超过超时 | 不灭 |
| 转头、离开座位 | 本轮或下一轮熄屏 |
| 闭眼超过 2 秒 | 不保亮 |
| 旁边有第二张脸 | 不保亮 |
| 口袋、扣在桌面 | 不开相机，正常熄 |
| 极暗 | Unknown，熄屏，不连拍 |
| 打开相机 / 扫码 | 立即让出，无崩溃 |
| 通话、全屏视频 | 引擎休眠，不抢系统逻辑 |
| 连续注视 30 分钟 | 强制熄一次 |
| 划掉最近任务、HyperOS 省电 | 有明确的「服务已停」通知，而不是静默失效 |
| LSPosed 钩子签名不匹配 | 能开机，功能显示未就绪 |
| 拒绝相机权限 | 无法开启，文案说明原因 |
| 无障碍被系统关掉 | 降级或通知，不残留 overlay |

没有自动化相机测试的条件。阈值用 M0 的计数表冻结，改分类器必须重跑那张表。

## 15. 发布

- 版本号：`1.0.0` 起，Hook 适配单独记「已测系统」，例如 HyperOS 版本号，而不是假装全版本通用。
- 渠道顺序：自己每天用的 M1 → 酷安 / GitHub 的 M4 → LSPosed 仓库。Play 不在 v1。
- 每个 Release 附：已测机型、未测声明、隐私三句话、Root 与非 Root 的安装步骤分开写。
- 崩溃：Hook 进程的崩溃是 bootloop 风险，不上第三方统计进 system_server。App 进程可以以后再加，v1 用本地日志。

## 16. 风险

| 风险 | 对策 |
|---|---|
| 被理解成监控 | 无身份、无落盘、无网络；设置页不提供查看画面；名称用「注视保亮」不用「我看见你」 |
| 误常亮烧屏 | Unknown 即熄；最长保亮；多脸不保亮 |
| HyperOS 杀后台 | 三条后端；掉线必通知；文档写清自启动与省电无限制 |
| PMS 签名随系统更新变化 | Hook 白名单签名、失败 no-op、已测系统写进 Release |
| 无障碍审核与用户不信任 | 默认推荐 Shizuku；无障碍用途只写延长亮屏；不读窗口内容 |
| 全屏应用失效 | 无障碍路径写明限制；LSPosed 不依赖 overlay |
| KMP 与 LSPosed 打包冲突 | M3 第一天验证，不行就拆 APK |
| 电量差评 | 超时前才拍、退避、息屏停服务；不提供连续预览开关（自检除外） |

## 17. 命名与对外说法

| 用途 | 文案 |
|---|---|
| 中文产品名 | 注视保亮 |
| 英文名 | GazeKeep |
| 模块列表标题 | GazeKeep |
| 一句介绍 | 超时前看一眼前摄，你还在看就不熄屏 |
| 商店第一句 | 给没有「检测注视」的手机，补上这个开关 |

不使用：ISeeYou、我看见你、EyeLock、NeverOff、Smart Stay（三星商标）。

## 18. 开放问题

这些不阻塞 M0，但要在 M3 前定：

1. 同一 APK 还是 UI APK + 模块 APK。由 M3 第一天的打包实验决定。
2. `minSdk` 26 还是 28。无障碍窗口与前台服务在 26 已可用；若不想处理 Android 8 的通知渠道差异，可以抬到 28。12 Pro 是 Android 12/13/14，不受影响。
3. 白名单默认是否预置浏览器与常见阅读器包名。建议空列表，由自检向导让用户勾当前正在用的那一个，避免误全局。
4. 英文界面是否进 v1。miuix 文案先中文，字符串外置即可。

## 19. 建议的下一步

开工只做 M0：一个调试页，前摄三帧，屏幕上只有「在看 / 没在看 / 没看清」和耗时。阈值表贴在 12 Pro 上跑完，再写任何一个保亮后端。
