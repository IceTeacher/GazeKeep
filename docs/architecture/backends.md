# 三大保亮后端与跨进程契约

GazeKeep 的相机采集与 `EngineReducer` 决策均在 App 主进程完成；三大后端（`LSPosed`、`Shizuku`、`Accessibility`）仅负责提供系统熄屏时序（`TimeoutDeadline`）、当前前台应用包名，以及在收到有效令牌时执行单次延后超时（`Extend`）或释放（`Release` / `ForceRelease`）。

## 三后端统一契约与对比矩阵

默认 `Auto` 模式按 **`LSPosed → Shizuku → Accessibility`** 顺序选用首个就绪后端；手动锁定时仅使用指定后端。后端切换时先取消旧会话并对切走的后端执行强制释放，切换期间绝不 `Extend`。未知前台包名绝不借用旧后端缓存。

| 维度              | LSPosed（`backend:lsposed` + `xposed`）                     | Shizuku（`backend:shizuku`）                                              | 无障碍（`backend:a11y`）                                      |
| ----------------- | ----------------------------------------------------------- | ------------------------------------------------------------------------- | ------------------------------------------------------------- |
| **运行进程**      | Hook 运行于 `system_server`，经系统服务夹带 Binder 桥接 App | `ShellService` 运行于 `shell`（UID 2000）或 `root` UserService 进程       | `GazeA11yService` 运行于 App 主进程                           |
| **Deadline 来源** | Hook 拦截系统电源内部超时调度，推送真实 `off` 与已知 `dim`  | 轮询解析 `dumpsys power`，综合 `lastUserActivityTime` 与 `NoChangeLights` | `IdleClockOracle` 基于系统超时设置与触摸/自身延长事件本地估算 |
| **前台包名来源**  | `Handler` 线程查询 `ActivityTaskManager.getTasks(1)`        | Shell 进程解析明确 `resumed` 的 Activity 记录                             | 过滤 `TYPE_WINDOW_STATE_CHANGED` 中可解析为 Activity 的事件   |
| **延后超时原语**  | 校验 `seq` 与令牌后在系统电源服务内触发 `userActivity`      | 首选 `PowerManager.userActivity`；被拒时走 `screen_off_timeout` 退路      | 挂载不可见的无障碍窗口持有亮屏（`Hold` 模式）                 |
| **释放与上限**    | 令牌单次消费，过期请求返回 `STALE`                          | 正常释放或 `ForceRelease` 时写回原系统超时并立即重读 Oracle               | 离开、`Unknown`、门控跳过、`MaxHold` 或解绑时立即摘除窗口     |
| **独有能力**      | 支持实验性单向过滤自身 `OP_CAMERA` 状态栏隐私绿点           | 免 Root 解锁，提供完整的异常断电/重启超时写回保护                         | 零 ADB / Root 门槛，开箱即用                                  |

---

## LSPosed 与 `system_server` 薄钩子

App 客户端位于 `backend:lsposed`，跨进程常量与 AIDL 位于 `bridge:protocol`（固定包名 `cn.rainafter.app.gazekeep.xposed.protocol`，以 [BridgeProtocol.kt](../../apps/app/bridge/protocol/src/main/kotlin/cn/rainafter/app/gazekeep/xposed/protocol/BridgeProtocol.kt) 为唯一真源），系统注入代码位于 `xposed`。

### 候选签名解析与 PMS 锁安全边界

- **候选签名解析**：`DeadlineResolver` 在目标 ROM 上按真实参数与返回值签名逐项解析候选，优先候选 `A`（`AttentionDetector.updateUserActivity(long,long):long`），不可用时回退候选 `C`（`scheduleUserInactivityTimeout(long):void`）。签名失配则跳过安装并记录未适配，绝不向 `system_server` 抛出异常。
- **锁与异常熔断**：钩子回调体整体 `try/catch(Throwable)`，连续异常自动熔断；**在 `PowerManagerService` 锁内只允许数值计算与 `Handler` 投递**（包括 `ActivityTaskManager` 前台包名查询也必须在 `Handler` 线程执行，不得在 PMS 锁内调用），严禁在锁内发起 Binder 调用、获取其他锁、使用协程/DataStore/文件 I/O。
- **单次令牌与去重**：客户端注册鉴权、有效 `seq` 与 `deadline` 校验是执行 `userActivity` 的前提；同一绝对 `off` 截止时间去重，防止重复 Hook 回调推进 `seq` 使在途采集失效；过期请求返回 `STALE`。
- **App 侧 Hidden API 串行豁免**：`SystemPowerBinderResolver` 经 `HiddenApiBypass` 豁免 `ServiceManager`；由于该库底层使用非线程安全集合拼接前缀，并发调用会将 `null` 传入 `VMRuntime` 导致原生崩溃，因此在进程内加锁串行执行且成功一次后永久复用。

### API 102 双入口同包设计

- **双登记不自动降级**：唯一 APK 同时保留现代登记 `META-INF/xposed/java_init.list`（`ModernEntry`，最低/目标 API 均为 `102`，静态作用域 `system`，关闭热重载）与旧登记 `assets/xposed_init`（`LegacyEntry`，作用域 `android`，最低 API `93`）。不支持 API 100；现代框架加载失败不会自动回退 legacy，更新模块后须重启系统生效。
- **具名 Hooker 与回滚隔离**：`ModernHookFacade` 使用具名内部类 `BeforeHooker` / `AfterHooker` 实现 `XposedInterface.Hooker`（避免 SAM lambda 被 R8 压缩裁剪，由 `verify:apk` 静态校验类与 `intercept(Chain)` 签名）。主钩子部分安装失败时先关闭 core 再逆序卸载已注册句柄。

### 实验性相机隐私指示器过滤

- 默认保留系统状态栏摄像头绿点；仅在 LSPosed 就绪且用户开启「隐藏相机隐私指示器」（二次确认）时，由 `CameraIndicatorResolver` 独立 Hook `AppOpsService` 的 `scheduleOpActiveChangedIfNeededLocked` / `notifyOpActiveChanged` 与 `isOperationActive`。
- **严格单向与自身限定**：仅当开关开启、操作码为 `OP_CAMERA`（26）且 UID/包名属于 GazeKeep 时，拦截 `active == true` 通知并将活跃查询置为 `false`；`active == false` 正常放行，绝不修改 `startOp` / `checkOperation` 权限结果，也不注入 `SystemUI` 或影响任何其他应用。指示器使用独立 `HookCallbackGuard`，解析失败或异常仅降级绿点隐藏，不影响电源主链路 `HOOK_READY`。

---

## Shizuku 与系统超时恢复状态机

`backend:shizuku` 包含 `client`、`protocol`、`oracle`、`restore`，`ShellService` 保留在根包以维持反射入口与 `IGazeKeepShell` descriptor 稳定。

### 保亮原语与临时超时退路写回状态机

1. **首选与退路触发**：优先以 shell/root 身份调用 `PowerManager.userActivity`。当抛出 `SecurityException`（或 Debug 构建设置 `debug.gazekeep.deny_user_activity=1` 故障注入；Release 源码集为空实现并由 `verify:apk` 校验）时走退路，将系统 `screen_off_timeout` 临时设为 `max(原值, 距上次用户活动已过时间 + 60000 ms)`（限制在 `Int.MAX_VALUE` 内）。退路不产生新的系统用户活动时间戳，因此退路生效期间允许同一活动序号连续延长。
2. **同步落盘与防覆盖保护**：修改系统设置前，先在 IO 互斥锁内通过带布尔返回值的同步 `commit()` 将原值及最近两次本应用写入的延长值持久化至独立 `gazekeep_timeout_restore` SharedPreferences；落盘失败绝不修改系统超时。恢复时若检测到当前系统值并非本应用写入的延长值，说明用户在延长期间手动修改了系统超时，此时**保留用户新值并仅清除恢复记录**。
3. **推迟恢复与强制恢复（`ForceRelease`）边界**：
   - 写回原值并读回确认一致后才清除恢复记录；恢复任务运行在独立于检测会话的清理作用域中。
   - 亮屏期间若因未注视、门控跳过或采集中止触发普通 `Release`，且写回原值后距按上次活动计算的熄屏点不足 `10 s`（不足以容纳下一轮提前量与采样），则推迟写回，交由后续息屏释放或 `detach` 完成。
   - **到达单次保亮上限的 `ForceRelease`、切换后端时的强制释放以及 `detach` 绝不推迟**，立即写回原值。每次写回改变系统熄屏点后，会话立即重读 `PowerDumpOracle` 刷新 `deadline`。
   - 若退路期间设备重启，`TimeoutRestoreBootReceiver` 在开机广播中检测到待恢复记录后仅单独连接 shell 写回原值，不启动检测服务或相机。
4. **UserService 退避重连**：当 `ShellService` 子进程单独退出而 Shizuku 仍在线时，`UserServiceReconnector` 按 `1 / 2 / 5 / 10 / 30 s` 退避重绑，Shizuku 离线或 `detach` 时取消重绑。

### `PowerDumpOracle` 与 `NoChangeLights` 视频顺延

- **协议 v4 与有效活动时间**：`PowerDumpParser` 解析 `dumpsys power` 中的实际有效超时（兼容 HyperOS `excludingAttention`）、`lastUserActivityTime` 与 `lastUserActivityTimeNoChangeLights`。
- **区分截止顺延与真实触摸**：当视频播放等持有带 `ON_AFTER_RELEASE` 标志的亮屏锁自然释放时，系统仅更新 `lastUserActivityTimeNoChangeLights` 顺延熄屏时间。Oracle 将 `max(lastUserActivityTime, lastUserActivityTimeNoChangeLights)` 用于推算真实 `off`/`dim` 截止时间（避免锁释放瞬间误判即将熄屏），但**仅将 `lastUserActivityTime` 的推进视为外部真实用户活动**，防止视频播完自然释放误清零 `MaxHold` 单次保亮上限累计。
- **自适应轮询退避**：息屏时完全暂停轮询（由亮屏广播、触摸或释放后的 `requestRead` 恢复）；其他应用持有亮屏锁时，在候选检测窗口外按 `5 s` 退避轮询，进入检测点前 `1 s` 至熄屏点窗口内恢复 `1 s` 轮询，兼顾低功耗与锁释放后的及时衔接。

---

## 无障碍后端（`Accessibility`）

- **零内容读取与信号同步**：`GazeA11yService` 固定声明 `canRetrieveWindowContent="false"`，只消费事件类型、包名与时间戳。`TYPE_TOUCH_INTERACTION_START` 与无障碍窗口 `ACTION_OUTSIDE` 产生真实用户活动；信号在主线程同步先向 reducer 提交 `UserActivity`，再通知 `IdleClockOracle` 发布新 `deadline`，避免先 `Armed` 后被自身触摸事件取消。
- **仅使用 `Hold` 模式**：Xiaomi 12 Pro 真机实测表明 `Pulse`（短暂挂载 300 ms 窗口）无法在 HyperOS 上重置完整系统超时周期，因此当前固定采用 `Hold` 模式（确认 `Looking` 后维持不可见的无障碍窗口，无需 `SYSTEM_ALERT_WINDOW` 权限）。在 `Away`、`Unknown`、门控跳过、`MaxHold` 到点或服务解绑时必须立即同步摘除窗口释放常亮锁。
- **Activity 前台解析过滤（`A11yForeground`）**：`TYPE_WINDOW_STATE_CHANGED` 同样会由输入法、权限弹窗或系统悬浮层（如 HyperOS 传送门 `com.miui.contentextension`）触发。`A11yForeground` 仅在事件的 `packageName` + `className` 能经 `PackageManager.getActivityInfo` 解析为已知 Activity 时才更新前台包名，彻底阻断悬浮层篡改前台导致黑名单失效。受限于清单仅声明桌面启动器 intent 可见性，非启动器应用的全屏页将沿用上一已知 Activity。
