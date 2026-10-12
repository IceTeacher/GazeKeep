# LSPosed 与系统桥接

App 客户端位于 backend:lsposed，协议常量/AIDL 位于 bridge:protocol，系统代码位于 xposed。保持唯一 APK 和协议包 cn.rainafter.app.gazekeep.xposed.protocol，App 侧不依赖 xposed 实现。

## 候选与锁边界

DeadlineResolver 按真实返回值/参数签名解析候选；当前优先 A，A 不可用再考虑 C，B 不作为未核实的自动替代。候选签名如下，是否可用必须在目标 ROM 逐项解析：

| 候选 | 签名                                                               |
| ---- | ------------------------------------------------------------------ |
| A    | AttentionDetector.updateUserActivity(long,long):long               |
| B    | PowerManagerService.updateUserActivitySummaryLocked(long,int):void |
| C    | scheduleUserInactivityTimeout(long):void                           |

只安装解析成功的计划；签名不符就 no-op，记录未适配。所有回调捕获 Throwable，连续异常熔断；PMS 锁内只计算/Handler 投递，禁止 Binder、其他锁或阻塞工作。

## 当前协议与安全

协议 v2 传递真实 off 与已知 dim，调用约束以 [BridgeProtocol](../../../apps/app/bridge/protocol/src/main/kotlin/cn/rainafter/app/gazekeep/xposed/protocol/BridgeProtocol.kt) 和 AIDL 为真源，不在文档再维护另一份编号表。

客户端注册鉴权、有效 seq 与 deadline 检查、令牌单次消费是保亮前提。过期 extend 返回 STALE，不调用 userActivity。系统侧不打开摄像头、不推理、不读配置文件，不引入协程或 DataStore。

同一绝对 off deadline 去重，避免重复 Hook 回调推动 seq 使当前采集失效。修改系统侧协议/入口后必须重启使新代码生效，验证前核对设备授权。

LegacyEntry 与 ModernEntry 共用 HookFacade 和 HookBootstrap，一次安装系统钩子。当前主流成熟框架（LSPosed 1.9.3）采用经典 LegacyEntry（IXposedHookLoadPackage）稳定注入 `system_server`；打包时通过 resources excludes 隔离 `java_init.list` 以兼容框架运行时环境；作用域同时声明 `system` 与 `android`。ActivityThread 存根及 Context 解析采用多级反射容错，最终 APK 不包含存根类定义。

Handler 派发 deadline 时通过 ActivityTaskManager.getInstance().getTasks(1) 查询 topActivity 包名，异常或签名不匹配返回未知；查询不在 PMS 锁内。系统按配置 lead 提前派发，App reducer 依据有效退避 lead 再调度采集，不在采集中重注册客户端或改变 seq。

CameraIndicatorResolver 独立解析 `com.android.server.appop.AppOpsService` 的相机活跃通知与 `isOperationActive` 签名，使用独立 HookCallbackGuard 与电源超时钩子隔离：当用户开启实验性“隐藏相机隐私指示器”并经 `TX_SET_HIDE_INDICATOR` 或 `TX_REGISTER` 同步至 PowerHookCore 时，单向过滤 GazeKeep 自身的 `OP_CAMERA`（26）活跃状态通知（保持 `startOp` 返回 `MODE_ALLOWED` 不变）；候选未命中或异常熔断仅降级绿点隐藏，不影响电源超时钩子 `HOOK_READY` 状态，详情见[隐私指示器分析](../camera-privacy-indicator.md)与 [ADR 0001](../../adr/0001-lsposed-hide-camera-privacy-indicator.md)。

验证入口为 xposed 的候选/调度/令牌/回调保护/指示器过滤测试，以及[实机矩阵](../../testing.md#实机完整矩阵)。故意错误钩点仍能开机、禁用作用域正常熄屏、完整长超时与触摸矩阵尚需补证。
