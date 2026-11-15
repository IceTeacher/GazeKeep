# v1.1.6 迭代与发版规划

最后维护：2026-11-15

状态：已完成 WP1–WP4 核心实现、`versionCode=8` / `versionName="1.1.6"` 调整、[v1.1.6.md](../releases/v1.1.6.md) 编写、多架构 `unsigned` Release APK 静态门禁、`Pixel_8_API_35` 模拟器验收与 `Xiaomi 2207122MC`（HyperOS `OS2.0.6.0.ULGCNXM`）真机核心回归（含 Chrome `ON_AFTER_RELEASE` 释放顺延实测）；多 ROM 兼容补充与由 CI 在 `v1.1.6` tag 上执行正式签名发布待后续完成。本文件记录设计与执行进度，交付与验收证据仍以[任务清单](todo.md)和[发布门禁](../development/release.md)为准。

## 目标与发版定位

`v1.1.6` 面向日常高频可用性与上手体验收敛，把已开发的「实时日志流」与本次新增的日常实用功能、引导断点修复和高影响缺陷整合为一个结构完整、可向用户明确说明升级理由的版本：

- **日常高频便利**：新增系统下拉「快捷设置」磁贴，支持一键暂停或恢复注视保亮，无需每次切回 App。
- **电源克制保护**：补齐[运行架构](../architecture/android-runtime.md)中尚未交付的低电量 / 省电模式暂停门控，避免手机低电或开启系统省电模式时继续唤醒前摄。
- **状态透明与引导闭环**：在首页展示最近跳过或停止检测的通俗原因，补全 `Auto` 模式下无就绪后端时的引导路径，减少「开着总开关却不知道为什么不生效」的困惑。
- **关键缺陷与体验收尾**：修复 Shizuku 在视频等带 `ON_AFTER_RELEASE` 亮屏锁释放后漏读延后活动时间的问题，并打磨日志页交互细节与高发 UI 小瑕疵。

### 范围边界（本版不做）

以下事项保留在后续中长期演进，不挤占 `v1.1.6` 周期：

- SAF 配置导出/导入、按应用独立覆盖灵敏度/提前量、充电不计上限等 `v1.2.x` 能力；
- 480×360 分析分辨率对比、连续注视单帧判定、MediaPipe / Camera2 备用管线；
- 官网 EdgeOne 边缘语言分流及跨浏览器视觉矩阵（可在独立 web 分支并行推进）。

### 不可变安全底线

本版全部改动必须继续满足 [Android 工程规则](../../apps/app/AGENTS.md#产品安全承诺) 与[隐私与安全](../product/privacy-security.md)：

1. **Unknown 等价于 Away**，省电暂停、后端异常或链路中断均默认放行系统熄屏；
2. 不申请 `INTERNET`、存储权限、电池优化豁免或 `SYSTEM_ALERT_WINDOW`；
3. 首页展示的原因只使用领域枚举映射的通俗状态文案，绝不展示角度、概率、原始传感器读数或 `dumpsys` 原文；
4. 实施具体模块前，若涉及接口、配置键或状态机变更，须先同步更新对应活动架构文档，再修改实现与测试。

---

## 详细设计方案

### 一、系统快捷设置磁贴（`TileService`）

- **归属模块**：`apps/app/android`（`AndroidManifest.xml`、`service/`、资源字符串）
- **设计方案**：
  - 新增 `GazeQuickTileService : TileService`（minSdk 26，原生支持 Android 7.0+ `TileService`），在 `AndroidManifest.xml` 声明 `BIND_QUICK_SETTINGS_TILE` 权限、`TileService.ACTION_QS_TILE` intent-filter 与可见性标记。
  - 状态映射（仅读取 `ConfigStore`、`RuntimeStatusStore`、`BackendReadinessProbe` 与相机权限，不绕过 `EngineReducer` 直接保亮）：
    - 未完成自检或缺少相机权限：磁贴状态为 `Tile.STATE_INACTIVE`，副标题提示「需完成准备」；点击后通过 `startActivityAndCollapse` 打开 `MainActivity`（API 34+ 使用 `PendingIntent` 重载），不静默启用。
    - 已具备前提但 `config.enabled == false`：状态为 `Tile.STATE_INACTIVE`，副标题「已关闭」；点击后更新 `ConfigStore` `enabled = true` 并调用 `ServiceActivation.reconfigure(context)`。
    - `config.enabled == true` 且服务工作/等待中：状态为 `Tile.STATE_ACTIVE`，副标题展示当前工作状态或激活后端名（工作/等待后端）；点击后将 `config.enabled` 置为 `false` 并调用 `ServiceActivation.stop(context)`。
  - 生命周期与刷新：`onStartListening()` 订阅状态流刷新磁贴；在 `ServiceActivation` 启停及 `RuntimeStatusStore` 状态变化时主动调用 `TileService.requestListeningState(...)` 通知系统更新磁贴，避免磁贴与首页开关不同步。
  - 语言同步：磁贴标签与副标题按应用内语言（`AppLocales`）解析，与 `GazeNotifier` 保持一致。
- **验收要点**：主机单元测试覆盖状态映射规则与 API 34+ 启动适配；真机验证下拉磁贴启停、未自检跳转、与首页卡片双向同步及横竖屏/锁屏下拉行为。

### 二、省电模式与低电量暂停门控

- **归属模块**：`core`（`GazeConfig`、`GateInput`、`SkipReason`、`GatePolicy`）、`engine`（`ConfigStore`、`AndroidGateProbe`）、`shared`（`SettingsScreen`、字符串资源、图标文案）
- **设计方案**：
  - 配置字段：在 `GazeConfig` 与 `ConfigStore` 新增 `pauseOnPowerSave: Boolean = true`（开启系统省电模式时暂停）与 `lowBatteryThreshold: Int = 15`（可选 `0` 关闭 / `10` / `15` / `20` 百分比，充电时不触发低电量跳过）。
    - 校验规则：`lowBatteryThreshold in setOf(0, 10, 15, 20)` 或 `0..50`（建议先收敛为固定离散挡位：关闭 / 10% / 15% / 20%，降级或非法值回到默认 `15`）。
  - 门控数据流：
    - `GateInput` 新增 `powerSaveMode: Boolean = false`、`batteryPercent: Int? = null`、`isCharging: Boolean = false`。
    - `AndroidGateProbe.collect()` 直接通过 `PowerManager.isPowerSaveMode` 与粘性广播 `Intent.ACTION_BATTERY_CHANGED`（无需任何新权限）读取电量与充电状态，**不打开传感器窗口**即可提前跳过。
    - `SkipReason` 新增 `PowerSaveMode` 与 `LowBattery`，并在 `GatePolicy.decide()` 中置于 `CameraSensitiveApp` / `InCall` 之后或硬件检查之前（在 `AppFilterMode` 之后即可减少无意义采样），触发后走标准 `GateDecision.Skip`，不打开前摄也不激活传感器窗口。
  - UI 设置项：在 `SettingsScreen` 检测与保护卡片区域新增「低电量暂停」下拉选项（关闭 / 系统省电模式时 / 低于 15% 且未充电时 等清晰枚举，避免增加过多开关复杂度；或拆分为一个开关「省电模式时暂停」+ 一个下拉「低电量暂停阈值」）。
- **验收要点**：`GatePolicyTest` 与 `ConfigStoreTest` 先建失败用例覆盖省电模式、低电量未充电、低电量充电中放行及配置持久化；主机门禁全过；真机验证开启系统省电模式后记录 `skip=PowerSaveMode` 且按原超时熄屏。

### 三、首页状态可解释性与引导断点修复

- **归属模块**：`core`、`android`、`shared`
- **设计方案**：
  1. **首页最近运行原因展示**：
     - 扩展 `RuntimeStatus`（[RuntimeStatusStore.kt](file:///run/media/IceTeacher/System/Data/Code/Project/GazeKeep/apps/app/android/src/main/kotlin/cn/rainafter/app/gazekeep/runtime/RuntimeStatusStore.kt)）与 `ControllerState`（[ControllerState.kt](file:///run/media/IceTeacher/System/Data/Code/Project/GazeKeep/apps/app/shared/src/commonMain/kotlin/cn/rainafter/app/gazekeep/ui/contract/ControllerState.kt)），除 `lastDecision: Attention?` 外记录轻量 `lastOutcome: LastOutcome?`（仅含 `DecisionReason`、`SkipReason?`、`AbortReason?`、`FrameVerdict.Reason?` 及时间戳，服务重配保留，显式关闭总开关清空，**不暴露角度/概率/包名敏感隐私**）。
     - 在 [HomeScreen.kt](file:///run/media/IceTeacher/System/Data/Code/Project/GazeKeep/apps/app/shared/src/commonMain/kotlin/cn/rainafter/app/gazekeep/ui/screens/HomeScreen.kt) 主卡片副标题及辅助说明中，当处于跳过、暗光暂停、保亮上限锁定或采集中止时，优先展示可读的中文/英文简明提示（如「已达单次保亮上限，触摸后恢复」「当前应用未在白名单内」「光线过暗已暂停检测」「相机被其他应用占用」「省电模式已暂停」），常规注视/未注视仍按原结果文案展示。
  2. **无就绪后端引导闭环**：
     - `UiNotice` 新增 `NeedsBackend`：当 `config.enabled == true` 且所有候选后端均未就绪（即 `activeBackend == null` 且相机已授权、自检已通过）时，首页展示可点击 `NoticeCard` 直达 `BackendScreen`。
     - [BackendScreen.kt](file:///run/media/IceTeacher/System/Data/Code/Project/GazeKeep/apps/app/shared/src/commonMain/kotlin/cn/rainafter/app/gazekeep/ui/screens/BackendScreen.kt) 优化：即使处于 `BackendLock.Auto` 模式，若当前没有任何后端就绪，也在下方展示常用开启引导按钮（优先展示已安装 Shizuku 的授权/打开按钮，及无障碍设置跳转按钮）；若 Android 13+ 非商店安装开启无障碍受阻，在提示文案旁提供「打开应用信息」快捷跳转（在 `PlatformActions` 新增 `openAppDetailsSettings()`）。
  3. **自检完成与默认空白名单提示**：
     - 在 `SelfTestScreen` 完成页点击「完成并开启」后，若当前为 `AppFilterMode.Whitelist` 且白名单为空，弹窗或引导条直接提供「去选择应用」跳转 `AppsScreen`，防止新用户开启后误以为失效。
  4. **扩充主流 ROM 相机包名**：
     - 在 [GatePolicy.kt](file:///run/media/IceTeacher/System/Data/Code/Project/GazeKeep/apps/app/core/src/commonMain/kotlin/cn/rainafter/app/gazekeep/core/policy/GatePolicy.kt) 的 `cameraPackages` 中补充主流厂商原生相机包名（OPPO/ColorOS `com.oplus.camera` / `com.oppo.camera`、vivo/OriginOS `com.android.camera` / `com.vivo.camera`、一加 `com.oneplus.camera`、三星 `com.sec.android.app.camera`、华为/荣耀 `com.huawei.camera` / `com.hihonor.camera` 等，逐一核实标准包名后写入并加单元测试）。
- **验收要点**：更新 `HomeUiTest`、`BackendUiTest` 与受影响 Roborazzi 截图基线并逐张核对；官网 `Showcase` 的 `ControllerState` 保持兼容默认。

### 四、Shizuku `NoChangeLights` 适配与状态/语言修复

- **归属模块**：`backend:shizuku`、`backend:a11y`、`android`、`shared`
- **设计方案**：
  1. **Shizuku `lastUserActivityTimeNoChangeLights`**：
     - 先在真机抓取并脱敏确认 `dumpsys power` 中视频播放等 `ON_AFTER_RELEASE` 亮屏锁释放前后的实际字段名与格式（核实 `mLastUserActivityTimeNoChangeLights` 与 `mLastUserActivityTime` 的关系）。
     - 扩展 [PowerSnapshot.kt](file:///run/media/IceTeacher/System/Data/Code/Project/GazeKeep/apps/app/backend/shizuku/src/main/kotlin/cn/rainafter/app/gazekeep/backend/shizuku/protocol/PowerSnapshot.kt) 与 `IGazeKeepShell` 协议（协议版本号同步递增或向后兼容附加字段，确保 `UserService` 版本校验匹配）：`PowerDumpParser` 解析该字段。
     - 区分「用于计算 `screenOffAt` 的有效活动时间（`max(lastUserActivityTime, lastUserActivityTimeNoChangeLights)`）」与「用于判定用户真实触摸清除 `MaxHold`/累计的 `lastExternalActivityUptime`」——**视频亮屏锁自然释放属于 `NoChangeLights` 顺延熄屏点，不能误算作用户真实触摸**，否则会错误清零 `MaxHold` 累计。此边界必须在 `PowerDumpOracleTest` 与 `EngineReducerTest` 中用测试锁定。
  2. **外部开启无障碍后页面就绪状态刷新**：
     - 排查 `MainActivity.onResume` / `AndroidGazeKeepController.refresh()` 与 `BackendReadinessProbe` 在系统通过 `settings put` 或无障碍后台绑定时的时序：确保 `onResume` 主动触发 `refresh()` 且 `GazeA11yService.connected` 或 `AccessibilityManager.AccessibilityStateChangeListener` 变化时实时更新 `backends[Accessibility]` 状态。
  3. **应用内英文状态下偶发回退中文排查 & 大字体布局微调**：
     - 检查 `AppLocales` 与 `BackendService` / `Activity` 重建、`Context` 资源配置同步链路，定位跨配置变更或服务拉起时的语言覆写问题；
     - 调整 `SettingsScreen` 中 `OverlayDropdownPreference` 在 1.3x 英文大字体下的副标题与右侧取值列宽比例，避免说明被挤成窄列。

### 五、日志页交互打磨（接续 `feat/log-live-stream`）

- **归属模块**：`shared`（[LogScreen.kt](file:///run/media/IceTeacher/System/Data/Code/Project/GazeKeep/apps/app/shared/src/commonMain/kotlin/cn/rainafter/app/gazekeep/ui/screens/LogScreen.kt)、`PlatformActions.kt`、`composeResources`）、`android`（`AndroidPlatformActions.kt`）
- **设计方案**：
  - 点击顶部筛选按钮或「更多」菜单前先调用 `focusManager.clearFocus()`，消除搜索框输入法候选栏遮挡菜单的问题（对齐 `AppsScreen`）。
  - 点击「清空日志」弹出 `OverlayDialog` 二次确认（展示当前日志条数提示，确认后再调用 `controller.clearLogs()`）。
  - 支持点击或长按单条日志卡片/原始行复制格式化文本（在 `PlatformActions` 增加 `copyToClipboard(text: String)`，官网演示可空实现或走浏览器剪贴板，复制后给出轻量反馈）。
- **验收要点**：补充 `LogUiTest` 覆盖清空确认取消/确认分支、菜单展开清焦点与复制入口。

---

## 执行任务清单

按依赖关系分成 5 个工作包推进；每包遵循「先写最小失败测试（纯逻辑/缺陷修复）→ 更新活动架构文档 → 实现 → 跑受影响门禁」：

### WP1：核心模型、门控与文档前置同步

- [x] 更新活动架构文档：[engine.md](../architecture/engine.md)、[android-runtime.md](../architecture/android-runtime.md)、[ui.md](../architecture/ui.md)、[shizuku.md](../architecture/backends/shizuku.md) 预登记配置项、门控枚举、首页状态展示与协议字段变化。
- [x] 在 `core` 补全 `GazeConfig` 新字段校验、`GateInput`/`SkipReason`（`PowerSaveMode`、`LowBattery`）与主流相机包名列表，先补充 `GatePolicyTest` 失败用例后实现。
- [x] 在 `engine` 更新 `ConfigStore` 读写与非法值回退、`AndroidGateProbe` 省电与电量读取，先补充 `ConfigStoreTest` / `DefaultEngineEffectRunnerTest` 用例后实现。

### WP2：Shizuku `NoChangeLights` 与后端状态刷新修复

- [x] 真机采集并脱敏核对 `dumpsys power` 关键字段格式与释放顺延（已在 `Xiaomi 2207122MC` / HyperOS `OS2.0.6.0.ULGCNXM` 确认小写 `lastUserActivityTime` 与 `lastUserActivityTimeNoChangeLights`，并通过 Chrome 22 秒视频自然结束释放 `ON_AFTER_RELEASE` 实测验证顺延与保留 `MaxHold`）。
- [x] 在 `backend:shizuku` 扩展 `PowerDumpParser`、`PowerSnapshot`、`IGazeKeepShell` 协议及 `PowerDumpOracle`，先写失败用例验证「带 `ON_AFTER_RELEASE` 亮屏锁释放后 `deadline` 顺延且不误判 `UserActivity`」。
- [x] 在 `android` / `backend:a11y` 注册无障碍系统状态监听并在前台 `onResume` 触发 `controller.refresh()`，排查英文语言偶发回退中文根因。

### WP3：快捷设置磁贴与状态共享

- [x] 在 `android` 实现 `GazeQuickTileService`，在 `AndroidManifest.xml` 注册并声明仅系统绑定权限（Android 14+ 开启时经 `PendingIntent` 拉起 `MainActivity` 满足 camera 前台服务约束）。
- [x] 扩展 `RuntimeStatusStore` 与 `BackendSession` 发布最近 `LastOutcome`（不含敏感图像/数值），并与 `TileService` 刷新联动。
- [x] 补全 `android` 单元测试（磁贴状态映射与语言解析）。

### WP4：UI 体验闭环、可解释性与日志页收尾

- [x] 在 `shared` 实现首页跳过/暂停原因展示、`UiNotice.NeedsBackend`、`BackendScreen` `Auto` 引导与「应用信息」跳转、`SelfTestScreen` 空白名单提示、`SettingsScreen` 省电/低电量设置项及 1.3x 字体排版调整。
- [x] 在 `shared` 完成 `LogScreen` 清焦点、清空日志二次确认与单条日志复制。
- [x] 补充并更新 `HomeUiTest`、`SettingsUiTest`、`LogUiTest`、`BackendUiTest`，按需重录并逐张核对 Roborazzi 截图基线。

### WP5：联调、全量门禁与发布准备

- [x] 运行完整主机门禁：`pnpm --filter @gazekeep/app verify`、`pnpm verify:architecture`、`pnpm verify:docs`、`pnpm verify:agent-assets`、`git diff --check`。
- [x] 编译多 ABI Release APK（本地未配签名环境变量下构建 `unsigned` 四架构产物：`arm64-v8a`、`armeabi-v7a`、`x86_64`、`universal`），并逐一通过 `pnpm verify:apk --apk <路径> --abi <ABI>` 校验权限、Xposed 保留规则与日志类型保留（正式签名包留待 CI 对 `v1.1.6` tag 执行）。
- [ ] 在真机完成 `v1.1.6` 核心回归（含快捷磁贴、省电模式/低电量跳过、视频播完 `NoChangeLights` 衔接、日志页交互及 `pnpm --filter @gazekeep/app test:device`），将实际环境与结果回填[任务清单](todo.md)（`Pixel_8_API_35` 模拟器已完成全部 5 项 `test:device` 及全功能闭环；`Xiaomi 2207122MC` 真机已完成 Debug 包磁贴、省电/低电量门控、真机注视延长、Chrome `ON_AFTER_RELEASE` 播完顺延、无障碍刷新、英文冷启动、日志交互与 4/5 项 `test:device` 通过 + 1 项安全锁屏跳过，非小米 ROM 与 CI 正式签名包实机安装待补）。
- [x] 按[发布门禁](../development/release.md)编写 [docs/releases/v1.1.6.md](../releases/v1.1.6.md)（仅含「更新日志」「启用步骤」）并登记文档索引；待确认合入后由 `v1.1.6` tag 触发 CI 正式签名预发布。

---

## 验收标准与阻断条件

除[发布规范](../development/release.md)与[测试规范](../development/testing.md)中的七条必守不变量外，本版额外核对以下阻断项：

- **磁贴越权或绕过自检**：未授权相机或未完成自检时，点击磁贴绝不能开启服务或跳过自检。
- **省电/低电量门控失效**：省电模式开启或电量低于阈值（且未充电）时，不得继续唤醒前摄或激活传感器窗口；调整设置后不残留旧状态。
- **Shizuku 错把锁释放当触摸**：视频亮屏锁自然释放顺延 `deadline` 时，必须保留已有的 `MaxHold` 累计与退避记录，不可误清除。
- **APK 新增禁止权限**：核对最终 APK Manifest 不含 `INTERNET`、存储权限或多余后台唤醒权限。
