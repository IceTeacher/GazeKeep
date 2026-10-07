# LSPosed 与系统桥接

App 客户端位于 backend:lsposed，协议常量/AIDL 位于 bridge:protocol，系统代码位于 xposed。保持唯一 APK 和协议包 cn.rainafter.app.gazekeep.xposed.protocol，App 侧不依赖 xposed 实现。

## 候选与锁边界

DeadlineResolver 按真实返回值/参数签名解析候选；当前优先 A，A 不可用再考虑 C，B 不作为未核实的自动替代。目标 ROM 的历史签名记录：

| 候选 | 签名                                                               |
| ---- | ------------------------------------------------------------------ |
| A    | AttentionDetector.updateUserActivity(long,long):long               |
| B    | PowerManagerService.updateUserActivitySummaryLocked(long,int):void |
| C    | scheduleUserInactivityTimeout(long):void                           |

只安装解析成功的计划；签名不符就 no-op，记录未适配。所有回调捕获 Throwable，连续异常熔断；PMS 锁内只计算/Handler 投递，禁止 Binder、其他锁或阻塞工作。

## 当前协议与安全

协议 v2 传递真实 off 与已知 dim，调用约束以 [BridgeProtocol](../../../bridge/protocol/src/main/kotlin/cn/rainafter/app/gazekeep/xposed/protocol/BridgeProtocol.kt) 和 AIDL 为真源，不在文档再维护另一份编号表。

客户端注册鉴权、有效 seq 与 deadline 检查、令牌单次消费是保亮前提。过期 extend 返回 STALE，不调用 userActivity。系统侧不打开摄像头、不推理、不读配置文件，不引入协程或 DataStore。

同一绝对 off deadline 去重，避免重复 Hook 回调推动 seq 使当前采集失效。修改系统侧协议/入口后必须重启使新代码生效，验证前核对设备授权。

当前 LegacyEntry 打包入口已存在，现代 libxposed 入口与 hidden-api 存根尚未落地；topPackage 来源未实现，不把目标的系统前台查询写成当前能力。

验证入口为 xposed 的候选/调度/令牌/回调保护测试，以及[实机记录](../../testing/m3-lsposed.md)。故意错误钩点仍能开机、禁用作用域正常熄屏、完整长超时与触摸矩阵尚需补证。
