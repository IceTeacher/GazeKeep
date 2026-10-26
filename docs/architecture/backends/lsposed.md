# LSPosed 与系统桥接

App 客户端位于 backend:lsposed，协议常量/AIDL 位于 bridge:protocol，系统代码位于 xposed。保持唯一 APK 和协议包 cn.rainafter.app.gazekeep.xposed.protocol，App 侧不依赖 xposed 实现。

## 候选与锁边界

DeadlineResolver 按真实返回值/参数签名解析候选；当前优先 A，A 不可用再考虑 C，B 尚未核实，不参与解析和安装。候选签名如下，是否可用必须在目标 ROM 逐项解析：

| 候选 | 签名                                                 |
| ---- | ---------------------------------------------------- |
| A    | AttentionDetector.updateUserActivity(long,long):long |
| C    | scheduleUserInactivityTimeout(long):void             |

只安装解析成功的计划；签名不符就 no-op，记录未适配。所有回调捕获 Throwable，连续异常熔断；PMS 锁内只计算/Handler 投递，禁止 Binder、其他锁或阻塞工作。

## 当前协议与安全

协议 v2 传递真实 off 与已知 dim，调用约束以 [BridgeProtocol](../../../apps/app/bridge/protocol/src/main/kotlin/cn/rainafter/app/gazekeep/xposed/protocol/BridgeProtocol.kt) 和 AIDL 为真源，不在文档再维护另一份编号表。

客户端注册鉴权、有效 seq 与 deadline 检查、令牌单次消费是保亮前提。过期 extend 返回 STALE，不调用 userActivity。系统侧不打开摄像头、不推理、不读配置文件，不引入协程或 DataStore。

同一绝对 off deadline 去重，避免重复 Hook 回调推动 seq 使当前采集失效。修改系统侧协议/入口后必须重启使新代码生效，验证前核对设备授权。

App 侧经 HiddenApiBypass 豁免 ServiceManager 后解析 power 服务。该库以非线程安全的集合拼接前缀，设置页探测、运行服务注册与指示器同步可能并发，并发调用会把 null 传入 VMRuntime 原生方法使进程崩溃；豁免在进程内串行执行、成功一次后不再调用，失败时下次解析重试。

## API 102 入口设计

唯一 APK 保留 LegacyEntry 与 ModernEntry。现代登记为 `META-INF/xposed/java_init.list`，最低/目标版本均为 102，静态作用域仅 `system`，关闭热重载；旧登记 `assets/xposed_init` 保留，推荐作用域仅 `android`，最低版本沿用 93。API 100 框架不在支持范围：识别现代登记的框架可能优先加载现代入口，失败不会自动切回 legacy；legacy 只用于确实选择旧登记的框架。不增加独立 legacy 产物或第三种适配。

ModernEntry 仅使用无参构造，在 onModuleLoaded 核对 API >=102、系统钩子能力与系统进程身份；onSystemServerStarting 使用提供的类加载器解析 Context，再调用共享 Bootstrap。本进程只尝试一次；现代 Context 缺失直接报告未就绪，不等待可能已执行过的 systemMain。legacy 保留已有 Context 解析回退。两个入口共享一次安装保护，现代路径不调用旧式 Xposed API。

HookFacade 返回可解除句柄。主钩子部分安装失败先关闭 core，再逆序解除已注册钩子；清理异常隔离，残留回调放行系统。指示器钩子失败只清理该组句柄，不影响电源主链路。现代拦截器以 proceed(newArgs) 回传成功的参数替换，setResult 跳过原调用，模块回调异常使用原参数放行；原调用异常透传且不重试。

现代前后置回调使用 ModernHookFacade 内的具名 BeforeHooker/AfterHooker 实现 XposedInterface.Hooker，不以 SAM lambda 生成框架入口。框架从 APK 外部调用 intercept(Chain)，两个具名类与方法由应用现有 entry 包保留规则保护；APK 门禁分别核对类定义及完整回调签名，防止压缩版本只剩实现接口的空壳。主机 facade 测试覆盖调用语义，压缩 DEX 与重启后实际桥接另行验收，startup=Installed 不能代替回调运行成功。

更新模块后重启系统生效，不提供热重载：现有系统 core、Handler 和客户端生命周期不具备代际交接能力，旧资源或钩子可能继续影响系统。未来若支持 API 100 或热重载，须重新评估交付方式与恢复责任。桥接协议仍为 v2，应用页面只显示实际桥接就绪状态；系统日志区分 modern_102/legacy 入口与初始化结果，框架 API/版本由验收记录核对。ActivityThread 存根仅编译，最终 APK 不包含定义。管理器识别、入口加载、钩子就绪和真实保亮分层验收。

上述入口与打包已按设计实现；实际构建与框架验收状态见[任务清单](../../plans/todo.md)，不得用目标设计声称已通过实机验收。

Handler 派发 deadline 时通过 ActivityTaskManager.getInstance().getTasks(1) 查询 topActivity 包名，异常或签名不匹配返回未知；查询不在 PMS 锁内。系统按配置 lead 提前派发，App reducer 依据有效退避 lead 再调度采集，不在采集中重注册客户端或改变 seq。

## 相机隐私指示器

默认保留系统摄像头隐私提示，仅 LSPosed 提供默认关闭的实验性自身指示器过滤。摄像头仍由 App 进程采集，系统侧只过滤活跃通知与查询；当前实现和目标 ROM 的实测效果必须分开表述。

- `CameraIndicatorResolver` 独立解析 `AppOpsService` 的 `scheduleOpActiveChangedIfNeededLocked` / `notifyOpActiveChanged`，并在候选可用时安装 `isOperationActive` 查询钩子。
- 仅在实验开关启用、操作为 `OP_CAMERA`（26）且 UID/包名属于 GazeKeep 时拦截 `active == true` 通知，活跃查询返回 `false`；`active == false` 正常放行。不修改 `startOp`、`startOperation` 或 `checkOperation` 的权限返回值，不影响其他应用。
- 指示器使用独立 `HookCallbackGuard`；候选未命中、安装失败或异常熔断只降级指示器过滤，不影响电源主链路的 `HOOK_READY`。
- `hideCameraIndicator` 默认为 `false`，由 ConfigStore 持久化，经 `TX_SET_HIDE_INDICATOR` / `TX_REGISTER` 同步至 PowerHookCore，`TX_PING` 返回钩子状态。后台采样与自检使用同一过滤条件；关闭开关或切换非 LSPosed 后端时同步关闭过滤，无需重启。实际通知恢复效果按[实机矩阵](../../development/testing.md#实机完整矩阵)验收。

选择在 `system_server` 内过滤，可复用现有内存状态与 Binder 桥接，保持现代 `system`、legacy `android` 的作用域。不注入 `com.android.systemui`，以避免扩大作用域和引入 ROM 状态栏适配风险；不通过 Shizuku 修改全局 `device_config`，也不将相机 AppOps 设为 `ignore`，以免破坏隐私提醒或相机出帧。无障碍与 Shizuku 后端保留系统默认指示器。

采集释放与传感器门控以[采集架构](../capture-classification.md)为准，不承诺固定的绿点显示时长，也不以指示器消失证明相机已释放。

## 验证

验证入口为 xposed 的候选/调度/令牌/回调保护/指示器过滤测试，以及[实机矩阵](../../development/testing.md#实机完整矩阵)。故意错误钩点仍能开机、禁用作用域正常熄屏、完整长超时与触摸矩阵尚需补证。
