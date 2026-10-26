# Shizuku 后端

backend:shizuku 分为 client、protocol、oracle、restore。ShellService 的反射入口与 IGazeKeepShell descriptor 保持稳定。

## 保亮与恢复

首选 shell 身份调用 power userActivity，隐藏 API 运行时解析。userActivity 抛出 SecurityException 时走退路，临时把 screen_off_timeout 设为「距上次用户活动已过时间 + 60000 ms」，不低于原值，用 Long 计算后限制到 Int 最大值。系统从上次用户活动起计时，退路不产生活动，序号（上次活动时间）不变；因此退路生效期间允许同一序号再次延长，延长前已发生的活动也不再视为外部活动，持续注视才能连续延长。Sui 的 root 与 Shizuku 的 shell 身份通常都有 DEVICE_POWER，退路难以自然触发；Debug 构建在 ShellService 调用前读取系统属性 `debug.gazekeep.deny_user_activity`，为 1 时抛出 SecurityException 以实机验证退路与写回。注入只在 debug 源码集，release 源码集为空实现，APK 门禁拒绝不可调试包中出现注入类。

原值保存在独立 gazekeep_timeout_restore SharedPreferences 的 pendingTimeoutRestore，同时记录本应用最近两次写入的延长值；恢复时当前值不是本应用写入的值，视为用户在延长期间手动修改，保留用户值、只清记录，下一次延长以该值为原值。保留两次写入值是为了覆盖落盘后、写入系统前进程退出的窗口。写入与清理使用有 Boolean 结果的同步 commit，操作在 IO 调度器及互斥锁内串行执行。原值未成功落盘不修改系统设置，重复退路不覆盖原值。写入失败后的内存读取也不会伪装成已持久化成功。

恢复写回后读回确认原值，再同步清记录；任一步失败保留恢复记录并报告未就绪。连接先恢复再 Ready，息屏/释放及销毁共用恢复流程。亮屏时若写回后距按上次活动计算的熄屏点不足 10 s（覆盖默认检测提前量与一次采集，不足时引擎只能放弃检测），连接与释放推迟恢复、保留记录与延长值，由熄屏释放或 detach 完成。推迟只用于仍可能继续检测的释放（未注视、门控跳过、采集中止，此时熄屏点即当前检测窗口末尾）；到达保亮上限的 ForceRelease、后端被切走时的强制释放与 detach 不推迟，否则临时延长会留给之后不再经过本后端的熄屏释放，或让上限超出最多 60 s。退路延长期间重启时，开机接收器发现记录后单独连接 shell 写回；Shizuku 未就绪或 20 s 内未连上则保留记录，待下次连接恢复。恢复任务独立于检测会话 scope。Binder 不可用时只能保留记录，待连接恢复再写回，不能宣称离线时已恢复系统设置。

UserService 进程单独退出而 Shizuku 仍在线时不会再收到 binderReceived，由 UserServiceReconnector 按 1/2/5/10/30 s 退避重新绑定，连接成功后重置；bindUserService 同步抛错时不会有断开回调，重连器按同一退避继续安排下一次；Shizuku 自身离线或后端 detach 时取消待执行的重绑，等待 binderReceived 正常刷新。

## 来源与协议

shell 协议 v3 保持已有事务编号与 PowerSnapshot 字段顺序，追加前台包查询；UserService 绑定版本跟随协议版本，触发旧服务更新；版本不匹配不就绪，避免连接旧 UserService。查询只解析明确 resumed activity，冲突/异常返回未知。shell 命令限时，原始 dumpsys 不跨进程、不进日志。

PowerDumpOracle 使用实际有效超时，兼容 HyperOS 的 excludingAttention 字段。保留 dim/off，同一 deadline 去重。成功 userActivity 的 uptime 用于区分自身与外部触摸，不将自身续期视为新保亮会话。

## 验证

TimeoutRestoreCoordinatorTest 覆盖持久化失败、重开恢复、读回失败、清理失败与溢出；前台解析及 power oracle 有隔离主机测试。当前版本的 Binder 断开、重连、息屏与服务恢复仍按[实机矩阵](../../development/testing.md)补证，不能从主机测试推断 ROM 行为。
