# Android 运行组件与配置

本页定义 App 装配、服务生命周期、配置持久化与异常通知的职责。

## 生命周期与所有权

AppGraph 为装配根；AndroidGazeKeepController 委托配置、自检、应用列表、就绪探测和应用级启停。Activity 关闭 controller 取消页面订阅/自检/图标任务，不取消已保存的应用级启停任务。

GazeService 适配生命周期，BackendSession 拥有 backend、oracle、engine、采集会话、屏幕广播和无障碍订阅。关闭会话取消定时/副作用/采集、注销订阅、释放相机/分类器并解绑后端。部分初始化失败也必须由已登记所有者清理。

每次 onStartCommand 都兑现 startForeground。息屏保持 camera 前台服务空转，只释放检测资源；后台亮屏广播直接重启 camera FGS 的限制不能靠猜测绕过。保留空转服务是为了避免依赖后台广播重新启动 camera FGS；仍须验证存活与冷启动恢复。RuntimeStatusStore 只发布状态，不驱动保亮。

## 配置与迁移

ConfigStore 通过 Context 的单例 DataStore 委托打开 `gazekeep_settings`，UI 与 Service 共享。持久化字段为 enabled、backend_lock、lead_millis、sensitivity、whitelist_enabled、packages、max_hold_minutes、debug_log_enabled、self_test_passed_at，另有迁移标记。

默认关闭、Auto、8000 ms、Standard、白名单开/空、30 分钟。lead 只允许 5000/8000，上限只允许 10/30/60；非法读取回默认，非法写入拒绝。GazeConfig 中模型选择、相机忙/暗光/低电/其他保护开关等字段尚未全部进入存储和设置 UI。

LegacyMigration 只迁移旧 `gazekeep_config` SharedPreferences 的后端选择，强制 enabled=false、whitelist=true，不迁移历史启用资格；保留旧文件，不读取 XML 文件猜测实时状态。

Shizuku 的 pendingTimeoutRestore 使用独立 `gazekeep_timeout_restore` SharedPreferences，不是普通用户配置，也不是已实现的 internal_state DataStore。恢复工作不得依赖即将取消的服务 scope；同步持久化与失败语义见[Shizuku 架构](backends/shizuku.md)。

## 当前清单与通知

applicationId 为 cn.rainafter.app.gazekeep；相机、camera 前台服务和通知是现有申请能力，Shizuku Provider 与 Xposed 打包入口保持稳定。无障碍配置 canRetrieveWindowContent=false。

当前未声明 WAKE_LOCK、GET_USAGE_STATS 和电池优化权限，未实现 BootRecoveryReceiver。最终权限必须从 APK 检查。

GazeNotifier 的异常通知按同类 10 分钟限频；不在可信 Looking 时提示。DecisionRecord 包含 uptime、包名、Attention、决策/门控/中止/分类原因、后端、extend 和耗时，运行状态另保存 lastDecision。

## 验证要求

单一 DataStore 避免 UI 与 Service 缓存分裂；迁移只继承后端选择，不继承启用资格，以避免未经自检的静默启用。运行 ConfigStoreTest、BackendSelectorTest 及应用构建/lint；真实设备验证快速启停、部分初始化失败、息屏空转、冷启动回读和通知限频。

## 决策日志

DecisionLogs 是应用进程单例，通过有界 IO 队列串行处理配置、写入与清理；默认关闭，隐私页显式开关进入 ConfigStore。文件位于 noBackupFilesDir/decisions，只保存允许字段，不保存传感器值或人脸特征。

每条记录单独保存，写入、启动及每分钟清理超过 24 小时记录，关闭开关清除文件。进程停止期间无法执行定时删除，下次启动先清理再写入。队列满时丢弃日志而不阻塞引擎；文件错误不影响 Release 与默认熄屏。
