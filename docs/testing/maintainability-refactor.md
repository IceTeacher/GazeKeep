# 可维护性重构验证（2026-10-08）

> 证据说明：本页保留历史版本与日期。2026-10-08 最新结构重构后的实机回归尚未完成；当前缺口见[维护导航](../maintenance/overview.md)。

基线：`f32143560743`，main。变更在当前工作区，未创建发布或更新已测系统列表。阅读入口见[维护导航](../maintenance/overview.md)。

## 实施范围

| 批次          | 实施结果                                                                                                                                  |
| ------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| 0：基线与设计 | 重构前 APK、全部已有测试和 lint 通过；架构 §0/§2/§12 记录新边界                                                                           |
| 1：精简       | 删除旧 M0/M1/M2 控制器及接口、Greeting/Platform 模板、模板图标、两个算术示例测试、37 条未引用文案、4 个未引用依赖别名与 ProGuard 模板注释 |
| 2：UI         | 8 页面拆分到 screens，App 仅状态与主题装配，回退栈/转场在 navigation，复用组件和文案映射独立                                              |
| 3：核心与命名 | EngineModel 拆成 6 个职责文件；M0CaptureSession 改为 AttentionCaptureSession；无障碍服务/后端分文件，Shizuku 按职责目录化                 |
| 4：控制器     | 自检、应用列表、就绪探测、应用级启停和运行状态提取；ConfigStore 的迁移独立文件                                                            |
| 5：运行组件   | Service 只适配生命周期；BackendSession 拥有后端/oracle/硬件/订阅；engine 执行副作用；通知与文案独立；无障碍可变回调替换为可关闭同步订阅   |
| 6：协议       | 新增 bridge:protocol，只保留原协议常量/AIDL；App 侧后端不再依赖 xposed 实现                                                               |
| 7：维护入口   | README/AGENTS/架构文档与目录对齐，新增维护导航和架构边界检查                                                                              |

与原代码相比的独立安全修正：采集 CancellationException 继续传播，取消任务不再生成旧轮次的 CaptureAborted(Error)。会话初始化先记录所有者再调用 start，确保部分初始化失败也能在 onDestroy 清理；会话与副作用使用各自可取消的子 scope。

未改注视阈值、投票算法、reducer 转移、协议版本、Pulse/Hold 模式、配置迁移规则或 Shizuku 系统超时恢复方式。LegacyEntry、MainActivity、GazeService、GazeA11yService、ShellService 的外部类名保持稳定。

## 自动验证

```bash
./gradlew :androidApp:assembleDebug :core:allTests :shared:testAndroidHostTest \
  :engine:testDebugUnitTest :backend:a11y:testDebugUnitTest \
  :backend:shizuku:testDebugUnitTest :xposed:testDebugUnitTest \
  :androidApp:testDebugUnitTest :androidApp:lintDebug
python3 scripts/check_architecture.py
git diff --check
```

自动测试共 55 项、0 失败：

| 模块            | 测试数量 |
| --------------- | -------: |
| core            |       28 |
| engine          |        6 |
| backend:a11y    |        2 |
| backend:shizuku |        6 |
| xposed          |       11 |
| androidApp      |        2 |

shared 两个模板测试已删除，host test 为 NO-SOURCE，没有新增 UI 行为测试。自动化通过不能替代 UI 与硬件回归。

新增 5 项测试验证：取消旧采集不向下一轮回灌错误；关闭会话取消门控/采集/唤醒且保留父 scope；后端掉线回灌失败且不标记成功；旧订阅关闭不影响新会话；活动信号同步先于下一次 deadline。

静态一致性检查：

- bridge 协议常量和 AIDL 与基线逐字一致。
- 核心状态机拆分后，去掉 imports/package/空白的内容与原 EngineModel 完全一致。
- 分类、相机实现与纯策略未引入行为改动。
- 架构脚本通过：core/commonMain 不导入 Android；shared/commonMain 不导入 Android 或 engine/backend；engine 不导入具体后端/应用 UI；xposed 和 protocol 不导入协程、DataStore、Compose 或模型。
- APK 中保留 Xposed 入口 assets/META-INF 和服务入口；Manifest 中 `canRetrieveWindowContent=false` 的无障碍配置继续打包。
- APK 权限检查：CAMERA、FOREGROUND_SERVICE、FOREGROUND_SERVICE_CAMERA、POST_NOTIFICATIONS，以及依赖要求的动态广播签名权限和 Shizuku API 权限；未发现 INTERNET、存储/相册、QUERY_ALL_PACKAGES、SYSTEM_ALERT_WINDOW、READ_PHONE_STATE。

期间修复了拆分产生的插件加载、导入与新测试签名问题，最终结果以上述完整命令为准。现有 CameraX 弃用警告和发布前 R8 门禁仍保留，不在结构重构中升级依赖。

## 实机回归：尚未执行

已按用户要求通过 `adb connect 192.168.12.46` 连接设备（2207122MC），重新构建并使用 `adb install -r` 成功覆盖安装当前 Debug APK，保留应用数据。尚未重启手机或执行功能回归；原 M0–M4 实测记录仅作历史证据。以下项目需在小米 12 Pro 上执行并填写结果，不得用自动测试替代：

| 项目                                | 期望                                          |
| ----------------------------------- | --------------------------------------------- |
| 8 页导航、系统返回、旋转恢复        | 页面顺序、返回栈、选项与文案保持一致          |
| 应用搜索/勾选/滚动                  | 设置持久化，图标预读不阻塞页面                |
| 自检正视→转头、离页、切后台         | 步骤正确推进，取消时释放相机，不与服务争用    |
| 关闭后立即离页、快速开关            | 待启动任务被取消，不意外重启服务              |
| 设置冷启动与迁移                    | DataStore 回读，旧配置按原规则迁移            |
| 三种后端正视/转头/触摸/相机占用     | 保亮与默认熄屏语义保持一致                    |
| 自动/锁定切换、后端掉线、无障碍关闭 | 切换期间不 Extend，订阅释放，掉线有通知       |
| Shizuku 服务重启/重连/息屏          | 原系统超时恢复，不遗留临时设置                |
| LSPosed 重启、错误钩点、过期 seq    | 入口可加载，签名失配不影响启动，旧 seq 不保亮 |
| 连续注视到达上限                    | 下一轮 Yield，必须正常熄一次                  |
| 通知权限/限频                       | 成功保亮不提示，异常按同类 10 分钟抑制        |

功能债务仍包括部分传感器采样、前台应用来源、完整日志、现代 libxposed 入口和 R8 发布验证。此次重构不宣称它们已验收。

## 目录与包名对齐（2026-10-08）

本轮完成 apps → applist、BackendSelector 与测试归入 runtime、xposed/core → power（含测试、导入与混淆规则），以及 Shizuku 的 client/protocol/oracle/restore 分包。PowerSnapshot 单独放 protocol，ShellService 文件回到根包对应目录，反射入口类名保持不变。

验证命令：

```bash
python3 scripts/check_architecture.py
./gradlew :androidApp:assembleDebug :backend:shizuku:testDebugUnitTest :xposed:testDebugUnitTest :androidApp:testDebugUnitTest :androidApp:lintDebug
git diff --check HEAD
```

结果：构建与 lint 通过，androidApp 2 项、Shizuku 6 项、xposed 11 项测试通过；9 个模块的架构边界与普通源码/测试的目录包名一致性检查通过。

去除 package/import/空白后，对比基线确认移动类的实现正文不变，PowerSnapshot 字段不变，Shizuku Binder descriptor、事务编号、版本及序列化正文不变，LSPosed 协议文件逐字不变。检查最终 APK 确认 ShellService、LegacyEntry 和新包类存在，旧 xposed.core.PowerHookCore 不再打包。

本轮 APK 尚未安装到设备，也未进行新一轮实机回归；上方安装记录对应前一轮结构重构。Release R8 未在本轮验证。
