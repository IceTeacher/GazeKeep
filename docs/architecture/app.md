# Android 与 KMP 核心架构

GazeKeep 采用唯一 APK 交付：App 主进程完成相机采样与纯函数状态机决策，特权侧（LSPosed / Shizuku / 无障碍）只报告系统时序与前台包名并执行单次延后超时。领域术语与安全红线见 [产品、术语与隐私](../product.md)。

## 模块拓扑与代码定位

```text
android → shared → core
android → engine → core
android → backend:{a11y,shizuku,lsposed} → core
backend:lsposed → bridge:protocol ← xposed
android → xposed（唯一 APK 打包）
```

模块依赖方向、包名与目录一致性及越界 `import` 由 `scripts/repo-tools/scripts/check-architecture.ts`（`pnpm verify:architecture`）在代码层强制守卫：

| 模块               | 职责与隔离边界（根包：`cn.rainafter.app.gazekeep`）                                           |
| ------------------ | --------------------------------------------------------------------------------------------- |
| `core`             | `commonMain` 纯 Kotlin：`model`、`backend` 接口、`EngineReducer`、`policy`（零 Android 依赖） |
| `engine`           | `runtime`、`capture`、`camera`（CameraX）、`classify`（ML Kit）、`gate`、`config`             |
| `shared`           | KMP `miuix` UI：页面、返回栈、`GazeKeepController`/`PlatformActions` 契约与 Web `Showcase`    |
| `android`          | `AppGraph` 装配、`GazeService`、`GazeQuickTileService`、自检协调、应用列表、通知与语言        |
| `backend:a11y`     | `GazeA11yService`、无障碍 `Hold` 窗口、Activity 前台解析与 `IdleClockOracle`                  |
| `backend:shizuku`  | `ShellService`（根包保留）、`PowerDumpOracle`、`NoChangeLights` 与系统超时恢复状态机          |
| `backend:lsposed`  | App 侧 Binder 桥接客户端与 `HiddenApiBypass` 进程内串行解析                                   |
| `bridge:protocol`  | LSPosed 跨进程常量与 AIDL（固定 `.xposed.protocol` 包名，零业务/协程依赖）                    |
| `xposed`           | `system_server` 薄钩子（`ModernEntry`/`LegacyEntry`、电源调度、相机隐私指示器过滤）           |
| `hidden-api-stubs` | 隐藏 API 最小编译签名，仅允许 `compileOnly`，严禁打包进最终 DEX                               |
| `e2e`              | `com.android.test` 独立进程自插桩 UiAutomator 实机套件（`targetProjectPath = :android`）      |

## 决策引擎与事件驱动契约（`core` + `engine`）

### 单向状态机与时序

- **唯一决策入口**：`EngineReducer` 接收显式 `uptime` 与 `EngineEvent`，纯函数输出 `EngineSnapshot` 与 `EngineEffect` 列表。`DefaultEngineEffectRunner` 串行执行门控、采集与 `Extend` 副作用并将结果回灌 reducer；其他组件（含 `RuntimeStatusStore`）严禁绕过 reducer 直接触发保亮。
- **轮次与令牌隔离**：采集与 `Extend` 结果携带本轮 `token`，门控结果携带 `deadline`；会话取消或进入新轮次后，迟到回调即使忽略协程取消返回成功，也会在回灌前被拦截丢弃。`Extend`、`Release` 与 `ForceRelease` 通过互斥锁串行执行。
- **唤醒窗口与退避**：`TimeoutDeadline` 优先按 `dim - 有效 lead`（未知 `dim` 时按 `off - lead`）调度，唤醒时刻不晚于 `off - 2500 ms`，单次采集绝对截止为 `min(off - 200 ms, now + 1600 ms)`。默认最多 3 帧、2 票 `Looking` 通过；连续两轮 `Looking` 后进入稳态退避，提前量缩短 `3000 ms`（下限 `4000 ms` 且不超过用户设定值）并开启单帧快判（仅首帧即 `Looking` 时提前结束，首帧非 `Looking` 自动回退标准 3 帧 2 票）。
- **单次保亮上限（`MaxHold`）**：当 `maxHoldMinutes > 0` 时，首次成功 `Extend` 记录 `holdStartUptime` 并调度带起点的独立定时；到点发出 `ForceRelease` 解除后端保亮并锁定 `MaxHold`，后续轮次一律跳过，直到 `ScreenOff` 或外部真实用户活动重置。

### 门控顺序与暗光累计

亮屏且解锁后，`GatePolicy` 与 `AndroidGateProbe` 按成本从低到高依次检查：

1. **零开销系统与范围门控**（不打开传感器窗口）：
   - 系统省电模式（`PowerSaveMode`）与未充电低电量（`LowBattery`，通过 `PowerManager` 与粘性电池广播只读获取）。
   - 生效范围过滤：`Whitelist` 与 `Blacklist` 均要求当前前台包名已知（未知则 `ForegroundUnknown` 不放行）；只要前台已知，三种范围均强制跳过主流原生系统相机包名（`CameraSensitiveApp`）。
   - 通话状态（`InCall`）与前摄/公开逻辑摄像头外部占用（`CameraUnavailable`）。
2. **按需短窗传感器门控**：前序门控全部放行后才打开传感器采样窗口（见下节 ADR），判定口袋（`Pocket`）、倒扣（`FaceDown`）与连续暗光（`TooDarkRepeatedly`）。
3. **截止周期去重与暗光保持**：同一截止周期内（新 `deadline` 差值 `< 1 s` 且无外部活动）仅刷新前台包名，不重复触发门控（避免锁屏微调截止时间反复空转）。`darkUnknownStreak` 仅在采集结果为 `Unknown` 且至少 2 帧 `TooDark` 时递增；因暗光不保亮后必然伴随用户触摸或重新亮屏，**触摸与息屏均保留 `darkUnknownStreak`**，仅在非暗光采集结果或后端切换时清零；当 `darkUnknownStreak >= 2` 且环境光 `< 3 lux` 时直接跳过不开相机。

## 采集、分类与硬件实测取舍（ADR）

以下关键参数均由真机（Xiaomi 12 Pro / HyperOS 2）实测与耗时/准确率核算确定，属于不可随意改动的架构决策记录（ADR）：

- **CameraX 维持 `640×480`（不下调至 `480×360`）**：
  - _实测依据_：单轮采集耗时（约 `560～890 ms`）的大头在于相机打开至首帧出图（`370～430 ms`），ML Kit 单帧分类仅 `20～60 ms`。将分辨率降为 `480×360`（竖屏有效宽 `360 px`）最多节省 `< 15 ms`，却会导致远距离、戴眼镜或垂眼阅读时眼部像素不足，使标准模式下睁眼概率跌破 `0.6` 误判 `EyesClosed`，且部分前摄 HAL 缺少原生 `480×360` 档位。
  - _帧生命周期_：固定 `YUV_420_888`、`KEEP_ONLY_LATEST`、帧间隔 `150 ms`（首两帧未满 `200 ms` 预热丢弃）。分类在 analyzer 同步回调内完成，取消采集时先解绑 UseCase 并等待队列排空，严禁中断线程提前关闭正被 ML Kit 读取的 `ImageProxy`。
- **共享采集协调器（`CaptureOwnershipCoordinator`）**：
  - 进程内单例注入自检与后台服务；自检租约优先级高于后台服务，取得租约立即取消在途服务采集并等待旧资源排空交接；服务在自检租约存续期间直接取消避让，不排队抢占相机。
- **ML Kit 分类模式固定为 `ALL`（含宽松灵敏度）**：
  - _实测依据_：仅使用 bundled `face-detection`（`FAST`、无 landmark/contour、`minFaceSize = 0.15`）。真机实测表明，在 `FAST` + 无 landmark/contour 下若将分类模式设为 `NONE`，**ML Kit 返回的头部欧拉角恒为 0**，导致 `60°～90°` 侧脸仍被误判为正视（`HeadTurned` 失效），且 `ALL` 与 `NONE` 单帧耗时无可分辨差异。因此宽松模式也在模型层保持 `ALL`，仅在纯函数 `judgeFeatures` 中跳过双眼 `>= 0.6` 检查（仍要求单脸、脸宽 `>= 0.18`、偏航/俯仰角 `<= 25°`）。
  - _暗光快速短路_：进入模型前对 Y 平面按步长 8 抽样均值，`< 25` 直接判 `TooDark`，省去无效推理。
- **传感器采样窗口固定为 `1000 ms`（非常驻监听）**：
  - _实测依据_：为降低亮屏待机功耗（实测正视与息屏整机功耗下降 `11%～20%`），接近、光照、加速度传感器仅在其余门控通过后以 `SENSOR_DELAY_UI` 临时注册，三个传感器各上报一次且朝下判定（归一化 `Z < -0.8` 持续 `300 ms`）可下结论时立即注销。Xiaomi 12 Pro 搭载的 Elliptic Labs 超声波接近传感器注册后约 `0.56 s` 才首次上报，若窗口设为 `500 ms` 会导致接近读数 100% 缺失；设为 `1000 ms` 时三项读数在 `0.58～0.61 s` 内全部就绪。超时仍缺失的传感器读数保持 `null`（未知不触发跳过）。

## 服务生命周期、配置与日志（`android`）

- **装配与会话边界**：`AppGraph` 为进程装配根；`AndroidGazeKeepController` 管理 UI 订阅、自检与应用列表，Activity 销毁关闭 controller 时不取消已保存的后台启停任务。`GazeService` 持有 `BackendSession`；息屏期间保持 `camera` 类型前台服务空转（仅释放检测与传感器资源），避免因 Android 14+ 后台广播无法重新拉起 `camera` FGS 而失效。相机权限在 `onCreate` 与每次 `onStartCommand` 提升前校验，缺失时清理会话、发通知、`stopSelf` 并返回 `START_NOT_STICKY`；权限恢复且回到前台时由 controller 自动重配恢复。
- **快捷设置磁贴（`GazeQuickTileService`）**：通过 `TileService` 同步开关与运行状态；未授权或未自检时点击拉起 `MainActivity` 引导，绝不静默越权开启；Android 14+ 从关闭态点击开启时经 `PendingIntent` 拉起 `MainActivity`（等待配置与后端就绪加载后启动服务）以满足前台相机服务限制，关闭态可在后台直接停止服务。
- **配置持久化、SAF 备份与降级兼容**：
  - `ConfigStore` 经单例 DataStore 维护 `gazekeep_settings`。写入生效范围时主动删除旧键 `whitelist_enabled`：若旧键存在（说明刚从旧版升级或降级后由旧版写入）则优先按旧键推导，否则读取 `app_filter_mode`，确保版本升降级时白名单与黑名单分离且不意外扩大为全局保亮。
  - **零权限 SAF 配置备份（`ConfigBackupCodec`）**：`core` 层纯函数读写 `schemaVersion = 1` JSON，仅包含可迁移参数与清洗后名单（导出遇有效包名超 500 条直接拒绝 `PackageListTooLarge` 而不静默截断，导入单份名单上限 500 条），读取强制限制 512 KB（严格覆盖双名单满额最坏体积 ~257 KB）且忽略未知顶层字段；严禁导出或恢复 `enabled`、`self_test_passed_at`、`pendingTimeoutRestore`，导入经 `ConfigStore.importConfig` 原子合入并强制维持未自检设备 `enabled = false`；SAF 协调器无易失实例状态，支持 Activity 旋转重建后继续完成读写与反馈。
  - 应用列表「显示系统应用」偏好存于独立 `gazekeep_ui_prefs`；Shizuku 超时恢复记录存于独立 `gazekeep_timeout_restore`（同步 `commit`）。
- **应用内语言（`AppLocales`）**：不引入 AppCompat；Android 13+ 读写系统 `LocaleManager`（`localeConfig` 声明 `zh-CN` 与 `en`），Android 12 及以下持久化至 `gazekeep_app_locale` 并在 `attachBaseContext` 与进程默认 `LocaleList` 同步。语言切换时 `GazeService` 实时刷新通知渠道名与在展常驻通知；系统无障碍设置与 LSPosed 管理器中的模块描述由宿主系统按系统语言读取。
- **诊断日志（`DecisionLogs`）**：进程单例通过无界 IO 队列串行处理读写与清理，事件时间戳取调用时刻而非写盘时刻，写入后增量插入内存列表头部；存储于 `noBackupFilesDir/decisions`，启动、读取及每分钟自动清理超 24 小时文件，支持 `.txt` 导出。

## KMP `miuix` 界面与两步自检（`shared`）

- **导航与页面状态**：`Home` 与 `Settings` 为一级底栏标签（220 ms 淡入淡出），`Backend`、`Apps`、`Logs`、`Privacy`、`SelfTest`、`About/Notices` 为二级页面（450 ms `FastOutSlowInEasing` 转场并隐藏底栏）。
  - `HomeScreen` 结合 `RuntimeStatus` 与 `LastOutcome` 展示最近一次检测或跳过/暂停原因（如省电暂停、未在白名单、暗光暂停、上限锁定），服务重配时保留、显式关闭总开关时清空；`Auto` 模式无就绪后端时提示 `NeedsBackend` 直达后端页。
  - `AppsScreen` 仅查询启动器可见应用（零 `QUERY_ALL_PACKAGES`），默认隐藏未更新系统应用，但**已选中或本页本次操作过的系统应用始终可见**；搜索仅命中隐藏系统应用时提示数量并支持一键显示；菜单提供「选中搜索结果」与带二次确认的「清空已选」。
  - `LogScreen` 打开筛选或更多菜单前先调用 `clearFocus()` 收起输入法候选栏，支持点按/长按单条日志复制及带条数提示的清空二次确认。
- **两步自检状态机（`SelfTestCoordinator`）**：严格按 `Looking → Away → Complete` 推进（`Unknown` 不推进），写入 `self_test_passed_at` 成功后才提交完成状态；若当前为仅白名单且名单为空，完成页「完成并开启」后引导直达应用列表。离页、按 Home 或后台暂停时立即释放相机并保留已通过步骤，返回页面后等待用户手动重试，不自动开相机。
- **官网展示接入点（`ShowcaseNavHost`）**：在 `shared` 的浏览器目标中复用 `SettingsNavContainer`，仅链接 `Home` / `Backend` / `Apps` / `Settings` / `Privacy` 五个展示页（在编译器层通过死代码消除裁剪 `SelfTest`、`Logs`、`About`、`Notices` 与正则库），并通过 `CompositionLocal` 注入 `ShowcaseDriver` 登记滚动容器与可点击坐标，Android 端为 `null`，零运行时开销。
