# Shizuku 后端

backend:shizuku 按 client、protocol、oracle、restore 分工。ShellService 留在根包与对应目录，以保持 Shizuku 反射入口；IGazeKeepShell descriptor、编号、版本和 PowerSnapshot 序列化顺序保持稳定。

## 保亮与恢复

首选在 shell 身份通过 power Binder 调用 userActivity，隐藏 API 签名运行时解析，失败不能向系统抛异常。退路临时修改 screen_off_timeout，当前实现为原值加固定 60000 ms，而非加当前超时周期。

产品要求：必须先持久化原值到 pendingTimeoutRestore，再修改系统设置；服务重启、binder 重连和息屏时写回。当前 PendingTimeoutRestoreStore 使用 apply()，其异步落盘尚不能证明顺序保证，属于安全债务，不能降低承诺。

恢复存储为独立 SharedPreferences；恢复任务放在会话取消之外，成功恢复后才清记录。异常不允许永久扩大用户超时。

## PowerDumpOracle 与来源

解析 power 的实际有效 Screen off timeout，缺失才用 mScreenOffTimeoutSetting；兼容 HyperOS 的 mLastUserActivityTime(excludingAttention)。保留 dimDurationMs 与实际 off deadline，同一令牌只发一次，避免重复事件取消采帧。

前台应用门控当前由应用装配层使用无障碍来源，独立 UsageStats 来源尚未实现；不能承诺不开无障碍时白名单仍完整可用。

验证入口为 PowerDumpParserTest、PowerDumpOracleTest 与[实机记录](../../testing/m2-shizuku.md)。样本只保留所需 power 字段并去序列号；重启/重连/息屏恢复、权限丢失和 Binder 断开需真实设备验证。
