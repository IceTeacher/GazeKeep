# Android 运行组件与配置

## 生命周期与所有权

AppGraph 为装配根；AndroidGazeKeepController 委托配置、自检、应用列表、就绪探测和应用级启停。Activity 关闭 controller 取消页面订阅/自检/图标任务，不取消已保存的应用级启停任务。

GazeService 适配生命周期，BackendSession 拥有 backend、oracle、engine、采集会话、屏幕广播和无障碍订阅。关闭会话取消定时/副作用/采集、注销订阅、释放相机/分类器并解绑后端。部分初始化失败也必须由已登记所有者清理。

每次 onStartCommand 都兑现 startForeground。息屏保持 camera 前台服务空转，只释放检测资源；后台亮屏广播直接重启 camera FGS 的限制不能靠猜测绕过。具体决议见 [ADR 0005](../adr/0005-foreground-service-lifecycle.md)。RuntimeStatusStore 只发布状态，不驱动保亮。

## 当前配置

ConfigStore 通过 Context 的单例 DataStore 委托打开 `gazekeep_settings`，UI 与 Service 共享。持久化字段为 enabled、backend_lock、lead_millis、sensitivity、whitelist_enabled、packages、max_hold_minutes、self_test_passed_at，另有迁移标记。

默认关闭、Auto、8000 ms、Standard、白名单开/空、30 分钟。lead 只允许 5000/8000，上限只允许 10/30/60；非法读取回默认，非法写入拒绝。GazeConfig 中模型选择、相机忙/暗光/低电/调试开关等字段尚未全部进入存储和设置 UI。

LegacyMigration 只迁移旧 `gazekeep_config` SharedPreferences 的后端选择，强制 enabled=false、whitelist=true，不迁移历史启用资格；保留旧文件，不读取 XML 文件猜测实时状态。

Shizuku 的 pendingTimeoutRestore 使用独立 `gazekeep_timeout_restore` SharedPreferences，不是普通用户配置，也不是已实现的 internal_state DataStore。恢复工作不得依赖即将取消的服务 scope；持久化顺序债务见[Shizuku 架构](backends/shizuku.md)。

## 当前清单与通知

applicationId 为 cn.rainafter.app.gazekeep；相机、camera 前台服务和通知是现有申请能力，Shizuku Provider 与 Xposed 打包入口保持稳定。无障碍配置 canRetrieveWindowContent=false。

当前没有目标示例中的 WAKE_LOCK、GET_USAGE_STATS、BootRecoveryReceiver 和电池优化权限，不把旧 Manifest 设计代码当成合并后的清单。最终权限必须从 APK 检查。

GazeNotifier 的异常通知按同类 10 分钟限频；不在可信 Looking 时提示。DecisionRecord 当前是 uptime、包名、Attention、DecisionReason、backend、extended、latencyMs，运行状态只保存 lastDecision；完整调试日志和 24 小时滚动留存尚未交付。
