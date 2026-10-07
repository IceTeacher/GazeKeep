# 维护导航

本项目面向 Android，UI 和纯逻辑继续使用 KMP。阅读产品边界先看[产品定义](../product.md)，阅读状态机和安全要求看[架构总览](../architecture/overview.md)。重构前的现状分析保存在[重构提案归档](../archive/maintenance/可维护性分析与重构提案.md)，本页描述重构后的源码。

## 按要改的功能定位

所有 Kotlin 包以 `cn.rainafter.app.gazekeep` 为根。下表省略这个前缀和 sourceSet 层级。

| 要修改的内容           | 入口 / 负责目录                                            | 验证位置                                               |
| ---------------------- | ---------------------------------------------------------- | ------------------------------------------------------ |
| 页面层级与返回         | shared/ui/App、navigation/SettingsNavigation               | 本轮 UI 手工回归清单                                   |
| 某个设置页面           | shared/ui/screens/对应 Screen                              | 核对文案、设置保存和页面交互                           |
| 通用页面外观           | shared/ui/components                                       | 页面显示回归                                           |
| UI 与 Android 的接口   | shared/ui/contract                                         | 不向 commonMain 引入 Android                           |
| 保存配置、开关门禁     | androidApp/controller、engine/config                       | engine ConfigStoreTest、core SetupPolicyTest           |
| 自检与短时预览         | androidApp/selftest、platform/AndroidPlatformActions       | 正视→转头、离页/后台取消                               |
| 应用列表与图标         | androidApp/applist                                         | 搜索/勾选、列表滚动、取消预读                          |
| 启停服务               | androidApp/runtime/ServiceActivation                       | 关闭取消待启动任务、冷启动回读                         |
| 后端选择与 deadline    | androidApp/runtime/BackendSession、runtime/BackendSelector | BackendSelectorTest、三种后端手工矩阵                  |
| 引擎事件循环与副作用   | engine/runtime                                             | DefaultEngineEffectRunnerTest                          |
| 决策转移               | core/engine/EngineReducer                                  | core EngineReducerTest                                 |
| 门控/投票/保亮上限     | core/policy                                                | core commonTest                                        |
| 相机、帧分类           | engine/capture、camera、classify                           | 分类器/阈值变更重跑 M0 计数表                          |
| 无障碍与触摸           | backend/a11y                                               | A11ySignalsTest、无障碍实机矩阵                        |
| Shizuku App 侧         | backend/shizuku/client                                     | 后端实机矩阵                                           |
| Shizuku shell / Binder | backend/shizuku/ShellService、protocol                     | 保持类名、descriptor、字段顺序                         |
| power 解析与超时恢复   | backend/shizuku/oracle、restore                            | PowerDumpParserTest、PowerDumpOracleTest、恢复手工矩阵 |
| LSPosed App 侧         | backend/lsposed                                            | 桥接探测、掉线、升级兼容                               |
| system_server 钩子     | xposed/entry、hook、power                                  | xposed 单测、故意错签名仍可启动                        |
| LSPosed 协议           | bridge/protocol                                            | 保留 protocol 包名、AIDL、版本与 transaction 编号      |
| 通知与限频             | androidApp/notification、res/values/strings.xml            | 通知权限、同类 10 分钟抑制                             |

## 运行链路和资源所有者

```text
MainActivity → AppGraph → AndroidGazeKeepController
                              ├─ ConfigStore → ServiceActivation → GazeService
                              ├─ SelfTestCoordinator → AttentionCaptureSession
                              └─ LaunchableAppsRepository

GazeService → BackendSession
               ├─ BackendSelector → 后端 + oracle → EngineEvent
               ├─ GazeEngine → EngineReducer → DefaultEngineEffectRunner → EngineEvent
               ├─ AttentionCaptureSession → CameraXFrameSource + MlKitFrameClassifier
               ├─ 无障碍订阅、屏幕广播
               └─ GazeNotifier
```

- Activity 关闭 controller：取消自检/图标任务和页面订阅；不会取消应用级已保存的启停任务。
- Service 关闭 BackendSession：取消会话 scope、定时器、副作用和 deadline，注销广播/无障碍订阅，关闭相机/分类器，解绑后端。
- Shizuku 系统超时恢复在独立清理任务中执行，不能放进即将取消的服务 scope。
- RuntimeStatusStore 只发布运行状态；不能借它驱动保亮。决定是否 Extend 的唯一入口是 reducer。
- MainActivity、GazeService、GazeA11yService、ShellService、LegacyEntry 的外部类名保持稳定。普通类的目录与包名保持一致；ShellService 留在 shizuku 根目录与根包，Binder descriptor 保持稳定。

## 每次修改的验证

先运行 `python3 scripts/check_architecture.py` 检查源码/构建依赖边界，再根据改动运行对应模块测试。APK 与 lint 使用：

```bash
./gradlew :androidApp:assembleDebug :androidApp:lintDebug
./gradlew :core:allTests
./gradlew :engine:testDebugUnitTest :backend:a11y:testDebugUnitTest
./gradlew :backend:shizuku:testDebugUnitTest :xposed:testDebugUnitTest :androidApp:testDebugUnitTest
```

shared 的模板测试已删除，当前 host test 任务没有行为测试，不能以 NO-SOURCE 代表 UI 验收通过。回归证据和未验证项见 [本轮重构验证](../testing/maintainability-refactor.md)。

## 保留的功能债务

传感器采样、独立于无障碍的前台应用来源、完整决策日志和现代 libxposed 入口仍是后续工作。当前 release 未开启 R8。本轮没有实现这些能力，也没有修改分类阈值或 ROM 策略。

普通 Kotlin 源码和测试的目录必须与包名一致，`scripts/check_architecture.py` 同时检查这一约定，避免后续只移动文件而遗漏包名。

## 实现与验收差距（2026-10-08）

| 能力               | 当前现状与后续要求                                                                               |
| ------------------ | ------------------------------------------------------------------------------------------------ |
| 最新结构重构       | 主机验证通过；最后一轮包名移动 APK 尚未安装，实机回归未执行                                      |
| 保亮上限/Hold      | reducer 上限/门控跳过未发 Release，不能证明 overlay 释放；保留“必须熄一次”承诺并补修复与实机矩阵 |
| Shizuku 恢复持久化 | apply() 不能证明改超时前同步落盘；需实现有结果的持久化保证并测失败/重启                          |
| 前台应用来源       | 当前依赖无障碍；LSPosed topPackage、Shizuku 独立来源未落地                                       |
| 传感器与相机门控   | proximity/lux/faceDown 为 null，前摄检查只证明设备存在，未证明未占用                             |
| 退避与暗光         | lead/帧数退避未完整接入；Unknown streak 未仅归因暗光                                             |
| 日志               | 仅 lastDecision，完整日志与24小时滚动留存尚未实现                                                |
| 现代入口与发布     | libxposed 现代入口、hidden-api 存根与 Release R8 验证尚未完成                                    |
| 完整设备矩阵       | 长超时、Unknown/多脸、触摸、上限、错误钩点、非小米设备等仍有缺证；LSPosed 管理器版本待补         |

上表记录差距，不改变[安全承诺](../../AGENTS.md)。修复另立任务，先更新活动设计与失败场景，再实施与验收。历史数量仅描述当时版本，不能作为当前全部行为已通过的证明。
