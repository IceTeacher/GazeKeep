# Android 运行组件与配置

本页定义 App 装配、服务生命周期、配置持久化与异常通知的职责。

## 生命周期与所有权

AppGraph 为装配根；AndroidGazeKeepController 委托配置、自检、应用列表、就绪探测和应用级启停。Activity 关闭 controller 取消页面订阅/自检/图标任务，不取消已保存的应用级启停任务。

GazeService 适配生命周期，BackendSession 拥有 backend、oracle、engine、采集会话、屏幕广播和无障碍订阅。关闭会话取消定时/副作用/采集、注销订阅、释放相机/分类器并解绑后端。部分初始化失败也必须由已登记所有者清理。

无障碍服务初始化 backend 后发布可观察的实例状态；运行会话持续跟踪实例及 readiness，覆盖服务后连接、断开和重连。断开同步摘除窗口，再清除实例；同类型的新实例也必须释放旧后端并重新选择、配置 oracle。旧服务延迟销毁不能清除新服务的实例状态，取消会话后不再注册后端。

服务在 onCreate 与每次 onStartCommand 提升前核对相机授权，并处理权限撤销或 while-in-use 条件变化造成的平台拒绝。失败时清理会话、发布停止状态、尝试异常通知并 stopSelf，各项清理彼此隔离；失败启动返回 START_NOT_STICKY，不在权限缺失时反复粘性重启，也不创建采集会话。保存的启用配置与自检资格保留；前台页面刷新时若相机权限由未授权变为已授权、且启用与自检资格仍在，控制器自动重配，不要求手动关开总开关（首次读取只记录基线）。有效的每次 onStartCommand 都兑现 startForeground。息屏保持 camera 前台服务空转，只释放检测资源；后台亮屏广播直接重启 camera FGS 的限制不能靠猜测绕过。保留空转服务是为了避免依赖后台广播重新启动 camera FGS；仍须验证存活与冷启动恢复。RuntimeStatusStore 只发布状态，不驱动保亮或采集避让；自检避让由 AppGraph 注入的进程共享采集协调器负责。

## 配置与迁移

ConfigStore 通过 Context 的单例 DataStore 委托打开 `gazekeep_settings`，UI 与 Service 共享。持久化字段为 enabled、backend_lock、lead_millis、sensitivity、whitelist_enabled、packages、max_hold_minutes、debug_log_enabled、hide_camera_indicator、self_test_passed_at，另有迁移标记。

默认关闭、Auto、8000 ms、Standard、白名单开/空、30 分钟，隐私指示器实验开关默认关闭。lead 允许 0～60000 ms 且为整秒，上限允许 0（无限制）或 1～1440 分钟；非法读取回默认，非法写入拒绝。模型固定为 bundled ML Kit，相机占用时跳过检测，让系统正常熄屏；重复暗光退避也是固定策略，不提供隐藏配置；低电量暂停尚未交付。

LegacyMigration 只迁移旧 `gazekeep_config` SharedPreferences 的后端选择，强制 enabled=false、whitelist=true，不迁移历史启用资格；保留旧文件，不读取 XML 文件猜测实时状态。

Shizuku 的 pendingTimeoutRestore 使用独立 `gazekeep_timeout_restore` SharedPreferences，不是普通用户配置，也不是已实现的 internal_state DataStore。恢复工作不得依赖即将取消的服务 scope；同步持久化与失败语义见[Shizuku 架构](backends/shizuku.md)。

## 应用内语言

AppLocales 不引入 AppCompat：Android 13+ 写入系统 LocaleManager，与系统设置「应用语言」同源，切换后由系统重建页面；Manifest 的 `localeConfig` 声明 zh-CN 与 en，系统页只列出这两种语言。Android 12 及以下把语言标签存入独立的 `gazekeep_app_locale` SharedPreferences，MainActivity 在 attachBaseContext 套用并重建页面，同时更新进程默认 LocaleList，因为 Compose 资源按进程默认语言选择。跟随系统时重置为系统语言。

通知文案来自 android 模块的 `values`/`values-en` 资源，GazeNotifier 每次生成通知时按当前应用语言解析，因为运行中切换语言不会重建服务。语言变化时 GazeService 刷新渠道名并替换仍在展示的常驻通知：Android 13+ 的应用内语言与系统语言变化以配置变化送达 `onConfigurationChanged`；Android 12 及以下只写进程内偏好，由 AppLocales 的偏好监听通知服务。已展示的提醒保持原文，用户移除的常驻通知不重新弹出。

应用内语言只作用于本应用进程。系统设置中的无障碍服务名称与描述、LSPosed 管理器中的模块描述由其他应用按自身语言读取，跟随系统语言；任何版本的应用内设置（包括 Android 13+ 的按应用语言）都无法改变这些入口，因此不宣称它们支持应用内切换。

## 当前清单与通知

applicationId 为 cn.rainafter.app.gazekeep；相机、camera 前台服务和通知是现有申请能力，Shizuku Provider 与 Xposed 打包入口保持稳定。无障碍配置 canRetrieveWindowContent=false。

当前未声明 WAKE_LOCK、GET_USAGE_STATS 和电池优化权限，未实现 BootRecoveryReceiver。最终权限必须从 APK 检查。

GazeNotifier 的异常通知按同类 10 分钟限频；不在可信 Looking 时提示。DecisionRecord 包含 uptime、包名、Attention、决策/门控/中止/分类原因、后端、extend 和耗时，运行状态另保存 lastDecision（服务重配时保留，仅在显式关闭总开关时清空）。

## 验证要求

单一 DataStore 避免 UI 与 Service 缓存分裂；迁移只继承后端选择，不继承启用资格，以避免未经自检的静默启用。运行 ConfigStoreTest、BackendSelectorTest 及应用构建/lint；真实设备验证快速启停、部分初始化失败、息屏空转、冷启动回读和通知限频。

## 详细日志

DecisionLogs 是应用进程单例，通过无界 IO 队列串行处理配置、写入、读取与清理，并对外发布最新在前的日志列表；记录时间取调用 record/log 的时刻，排队写盘不改变时间，写入后只把新条目插到列表前端，不重读全部文件；默认关闭，设置页「详细日志」开关进入 ConfigStore。文件位于 noBackupFilesDir/decisions，统一记录引擎状态、门控、采样、保亮判定与后端事件（含级别、时间戳、Tag、来源/目标标识与消息正文），只保存允许字段，不保存传感器值、图像或人脸特征。

每条记录单独保存，启动、读取及每分钟清理超过 24 小时记录（写入不扫描目录），关闭开关或点击「立即清空日志」清除文件。进程停止期间无法执行定时删除，下次启动先清理再写入。入队不阻塞引擎；文件错误不影响 Release 与默认熄屏。导出文件为 .txt，日志与导出内容包含前台应用包名，设置页说明中注明。
