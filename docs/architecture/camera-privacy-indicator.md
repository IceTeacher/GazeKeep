# 摄像头隐私指示器与无感体验分析

本页记录 Android 12+ 状态栏摄像头使用提示（隐私指示器 / 绿点）的底层原理、系统原生注视与各后端的对比分析，以及 GazeKeep 基于 LSPosed `system_server` 实现实验性隐藏隐私指示器的架构设计与工程优化方案。

## 背景与机制

从 Android 12 (API 31) 起，系统强制实现隐私指示器（Privacy Indicators）：应用访问前置摄像头时，状态栏右上角会展示绿色圆点或展开胶囊提醒。

### 系统触发链路

1. 引擎在 App 进程通过 [CameraXFrameSource](../../apps/app/engine/src/main/kotlin/cn/rainafter/app/gazekeep/engine/camera/CameraXFrameSource.kt) 打开相机。
2. 调用通过 Binder 进入 native 层的 `cameraserver` 进程。
3. `cameraserver` 向 `AppOpsService` 报告权限操作：调用 `AppOpsManager.startOp(OP_CAMERA, clientUid, clientPackageName, ...)`。
4. `AppOpsService` 标记操作活跃，并通过 `scheduleOpActiveChangedIfNeededLocked` / `notifyOpActiveChanged` 回调通知 `SystemUI` 的 `PrivacyItemController`（同时暴露 `isOperationActive` 查询）。
5. `SystemUI` 获取活跃列表，在状态栏最上层绘制绿色隐私指示器，并记录到系统隐私看板。
6. 相机释放（`unbindAll()`）后，`CameraService` 调用 `finishOp`，指示器在短时间延迟后淡出。

### 系统原生注视免提醒原因

系统自带的“注视不熄屏”（如 AOSP `AttentionManagerService` 或 OEM 注视保亮）不显示绿点，主要采用以下机制：

1. **硬件级 Always-On Sensing (AOC)**：旗舰 SoC（如高通 Spectra ISP / Google Tensor Context Hub）具备独立硬件安全岛与低功耗视觉传感器通道，直接在 DSP/CHRE 侧完成人脸与注视特征匹配，数据不进入主系统的 `CameraService`，Camera2 接口未被打开。
2. **系统服务归属豁免**：在基于纯软件实现的机型上，由 `system_server` 内部绑定的特权系统服务调用底层服务，在 Framework 的 `AppOps` 与 `PermissionController` 中配置了系统白名单或内部 attribution 豁免，不向用户端 UI 触发视觉提示。

## 各后端隐藏可行性分析

GazeKeep 的摄像头采集统一在 App 进程由 [AttentionCaptureSession](../../apps/app/engine/src/main/kotlin/cn/rainafter/app/gazekeep/engine/capture/AttentionCaptureSession.kt) 执行，各后端（无障碍、Shizuku、LSPosed）负责时序感知与保亮触发。针对隐藏系统隐私提示的分析如下：

| 后端                                            |           可行性           | 技术原因与局限                                                                                                                                                                                                                                                                                                                                      |
| :---------------------------------------------- | :------------------------: | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **无障碍后端 (`backend:a11y`)**                 |       **完全不可能**       | 运行在标准应用沙箱（普通 UID）。Android 隐私指示器由 `SystemUI` 在专用顶层窗口渲染，普通应用无权修改 AppOps 状态，也无法遮挡或篡改状态栏。                                                                                                                                                                                                          |
| **Shizuku 后端 (`backend:shizuku`)**            |       **实际不可行**       | 运行在 `shell` 身份（UID 2000）：<br>1. 无法定向豁免：将 GazeKeep 的 `OP_CAMERA` 置为 ignore 会导致相机输出黑帧，检测直接失效。<br>2. 全局关闭风险：虽可通过 `device_config` 尝试关闭全局指示器，但会导致整机所有应用隐私提醒失效，且常被 OEM 锁定或重置；此举严重违反[产品安全承诺](../../apps/app/AGENTS.md#产品安全承诺)（不擅改系统全局设置）。 |
| **LSPosed 后端 (`backend:lsposed` & `xposed`)** | **可行（实验性可选功能）** | 运行在 `system_server` 进程中，通过拦截 `com.android.server.appop.AppOpsService` 向外派发的 `OP_CAMERA` 活跃变更通知及活跃状态查询，仅针对 `cn.rainafter.app.gazekeep` 单向豁免状态栏绿点，保持 `startOp` 返回 `MODE_ALLOWED` 不变以保障相机正常出帧。                                                                                              |

## LSPosed 实验性隐藏方案设计

详细架构决策记录见 [ADR 0001](../adr/0001-lsposed-hide-camera-privacy-indicator.md)，具体实现边界如下：

1. **保持单一 `system_server` 作用域**：
   - 模块作用域维持 `android` 与 `system`（[scope.list](../../apps/app/android/src/main/resources/META-INF/xposed/scope.list)），不引入 `com.android.systemui`。
   - 避免因各 ROM（HyperOS、OriginOS、ColorOS）对 `SystemUI` 状态栏的重度定制引发崩溃，同时直接复用 `system_server` 内 [PowerHookCore](../../apps/app/xposed/src/main/kotlin/cn/rainafter/app/gazekeep/xposed/power/PowerHookCore.kt) 的内存状态与 Binder 桥接。
2. **`AppOpsService` 候选解析与故障隔离**：
   - 由独立的 `CameraIndicatorResolver` 解析 `com.android.server.appop.AppOpsService` 中的活跃通知方法（`scheduleOpActiveChangedIfNeededLocked`、`notifyOpActiveChanged`）及 `isOperationActive`。
   - 仅当 `code == 26 (OP_CAMERA)`、目标 UID 或包名匹配 `cn.rainafter.app.gazekeep` 且当前实验开关已启用时：
     - 拦截 `active == true` 的活跃状态通知（`active == false` 正常放行以确保任何已有指示器能正常熄灭）；
     - 将 `isOperationActive` 返回值覆盖为 `false`。
   - 严禁修改 `startOp` / `startOperation` / `checkOperation` 的返回值，确保 `cameraserver` 正常输出图像帧。
   - 采用独立的 [HookCallbackGuard](../../apps/app/xposed/src/main/kotlin/cn/rainafter/app/gazekeep/xposed/hook/HookCallbackGuard.kt) 保护：若目标 ROM 签名不匹配或运行异常触发熔断，仅降级绿点隐藏能力并退回系统默认绿点展示，绝不影响 LSPosed 保亮后端本身的 `HOOK_READY` 状态与延后超时主链路。
3. **跨进程状态同步与即时生效**：
   - 在 [GazeConfig](../../apps/app/core/src/commonMain/kotlin/cn/rainafter/app/gazekeep/core/model/Config.kt) 中提供默认关闭的 `hideCameraIndicator: Boolean = false` 配置项，持久化于 [ConfigStore](../../apps/app/engine/src/main/kotlin/cn/rainafter/app/gazekeep/engine/config/ConfigStore.kt)。
   - [BridgeProtocol](../../apps/app/bridge/protocol/src/main/kotlin/cn/rainafter/app/gazekeep/xposed/protocol/BridgeProtocol.kt) 新增 `TX_SET_HIDE_INDICATOR` 事务，并在 `TX_REGISTER` 中携带 `hideCameraIndicator`、在 `TX_PING` 响应中返回指示器钩子状态。
   - 用户在 [SettingsScreen](../../apps/app/shared/src/commonMain/kotlin/cn/rainafter/app/gazekeep/ui/screens/SettingsScreen.kt) 开启该实验开关（且所选/当前后端为 LSPosed）后，立即通过 Binder 同步至 `PowerHookCore`，无论是后台保亮采样还是前台相机自检均直接隐藏绿点；关闭开关或切换至 Shizuku / 无障碍后端时立即置为 `false`，免重启恢复系统默认绿点。

## 非侵入式工程优化方案

在未启用 LSPosed 实验性隐藏或使用 Shizuku / 无障碍后端时，通过以下方式减少视觉突兀感，逼近无感体验：

1. **单帧极速决策与快速释放**：
   - 当前参数为 150 ms 帧间隔、最多 3 帧。优化分类器链路：若首帧人脸判定结果置信度显著充足，则首帧即可输出 Looking 并在 100~150 ms 内完成 `unbindAll()`。
   - 极短的占用窗口在多数系统上只表现为极微弱的绿点轻闪，不会展开完整胶囊提醒。
2. **前置传感器物理门控**：
   - 强化接近传感器、暗光（LumaProbe）与设备朝下（归一化 Z < -0.8）判断。非视线交互条件直接放行熄屏，避免不必要的相机唤醒。
3. **用户认知引导**：
   - 在[隐私与安全](../privacy-security.md)及设置页中明确说明：默认保留系统状态栏绿点作为端侧按需采样的透明审计特征；仅在 LSPosed 后端下提供可选的实验性自身绿点豁免。
