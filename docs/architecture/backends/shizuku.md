# Shizuku 后端

backend:shizuku 分为 client、protocol、oracle、restore。ShellService 的反射入口与 IGazeKeepShell descriptor 保持稳定。

## 保亮与恢复

首选 shell 身份调用 power userActivity，隐藏 API 运行时解析。退路临时增加 screen_off_timeout 60000 ms；用 Long 计算后限制到 Int 最大值。

原值保存在独立 gazekeep_timeout_restore SharedPreferences 的 pendingTimeoutRestore。写入与清理使用有 Boolean 结果的同步 commit，操作在 IO 调度器及互斥锁内串行执行。原值未成功落盘不修改系统设置，重复退路不覆盖原值。写入失败后的内存读取也不会伪装成已持久化成功。

恢复写回后读回确认原值，再同步清记录；任一步失败保留恢复记录并报告未就绪。连接先恢复再 Ready，息屏/释放及销毁共用恢复流程；恢复任务独立于检测会话 scope。Binder 不可用时只能保留记录，待连接恢复再写回，不能宣称离线时已恢复系统设置。

## 来源与协议

shell 协议 v3 保持已有事务编号与 PowerSnapshot 字段顺序，追加前台包查询；UserService 绑定版本跟随协议版本，触发旧服务更新；版本不匹配不就绪，避免连接旧 UserService。查询只解析明确 resumed activity，冲突/异常返回未知。shell 命令限时，原始 dumpsys 不跨进程、不进日志。

PowerDumpOracle 使用实际有效超时，兼容 HyperOS 的 excludingAttention 字段。保留 dim/off，同一 deadline 去重。成功 userActivity 的 uptime 用于区分自身与外部触摸，不将自身续期视为新保亮会话。

## 验证

TimeoutRestoreCoordinatorTest 覆盖持久化失败、重开恢复、读回失败、清理失败与溢出；前台解析及 power oracle 有隔离主机测试。当前版本的 Binder 断开、重连、息屏与服务恢复仍按[实机矩阵](../../development/testing.md)补证，不能从主机测试推断 ROM 行为。
