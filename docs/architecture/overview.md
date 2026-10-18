# 架构总览

当前以唯一 APK 交付，App 进程完成检测与决策，特权侧只报告系统时序并执行保亮。术语见 [CONTEXT.md](../../CONTEXT.md)。

```text
android → shared → core
android → engine → core
android → backend:{a11y,shizuku,lsposed} → core
backend:lsposed → bridge:protocol ← xposed
android → xposed（唯一 APK 打包）
```

## 仓库分区

Android 九个运行模块与一个编译存根模块统一位于 apps/app，内部依赖由 Gradle 管理。apps/web 提供 Astro 静态双语官网，并复用 shared 的浏览器展示入口，scripts/repo-tools 提供 Node 仓库工具；pnpm 管理开发依赖，Turbo 调度工程级任务，不取代引擎或模块依赖。Node 开发工具不进入 APK。

## 十模块职责

| 模块             | 所有权与边界                                                                           |
| ---------------- | -------------------------------------------------------------------------------------- |
| android          | AppGraph 装配、controller、selftest、applist、platform、runtime、service、notification |
| shared           | KMP UI：contract、navigation、screens、components、text；只认识 core 和平台契约        |
| core             | commonMain 纯 Kotlin：model、backend 接口、engine reducer、policy                      |
| engine           | Android runtime/capture/camera/classify/gate/oracle/config；不依赖具体后端             |
| backend:a11y     | 无障碍服务、overlay、同步事件订阅                                                      |
| backend:shizuku  | ShellService、client/protocol/oracle/restore                                           |
| backend:lsposed  | App 侧桥接客户端与 Binder resolver                                                     |
| bridge:protocol  | LSPosed 常量与 AIDL，保留原 .xposed.protocol 包名                                      |
| xposed           | system_server 的 entry/hook/power，不依赖 core/engine/UI                               |
| hidden-api-stubs | 隐藏 API 的最小编译签名，仅 compileOnly 引用，不进入 APK                               |

## 进程与入口

主进程包含 Activity、GazeService、GazeA11yService、引擎及 App 侧后端。Shizuku UserService 在 shell 身份进程运行；LSPosed 模块在 system_server 运行，不能把主进程库和模型带进去。

包名根 cn.rainafter.app.gazekeep；applicationId、Shizuku authority 与 LSPosed scope 同一身份，不使用 debug 后缀。MainActivity、GazeService、GazeA11yService、ShellService、LegacyEntry 等外部入口类名保持稳定，普通类目录与包名一致。

接口变化先改设计及真源再改调用方。AIDL、descriptor、transaction 编号、协议版本与字段顺序属于跨进程兼容边界。hidden-api 存根已提供最小编译签名且仅 compileOnly 引用。现代 libxposed API 102 与 legacy 双入口同 APK 登记；现代最低/目标 API 均为 102，API 100 不在支持范围，现代加载失败不自动回退。框架与 ROM 实机验收状态见任务清单。

## 架构专题

| 专题                                    | 内容                                     |
| --------------------------------------- | ---------------------------------------- |
| [Android 运行](android-runtime.md)      | 服务生命周期、配置、通知与日志           |
| [决策引擎](engine.md)                   | reducer、时序、退避与保亮上限            |
| [采集与分类](capture-classification.md) | 相机所有权、传感器门控、阈值与投票       |
| [页面与平台接口](ui.md)                 | 页面导航、自检、应用列表与平台契约       |
| [无障碍后端](backends/a11y.md)          | 事件、Hold 与空闲时钟                    |
| [Shizuku 后端](backends/shizuku.md)     | shell Binder、前台来源与系统超时恢复     |
| [LSPosed 后端](backends/lsposed.md)     | 系统桥接、API 102 双入口与隐私指示器取舍 |

各专题集中维护设计和长期取舍；实现缺口与设备验收结果见[任务清单](../plans/todo.md)。

## 按要改的功能定位

所有 Kotlin 包以 `cn.rainafter.app.gazekeep` 为根。下表从 `apps/app` 开始定位，省略这个前缀和 sourceSet 层级。

| 要修改的内容           | 入口 / 负责目录                                         | 验证位置                                               |
| ---------------------- | ------------------------------------------------------- | ------------------------------------------------------ |
| 页面层级与返回         | shared/ui/App、navigation/SettingsNavigation            | UI 交互矩阵                                            |
| 某个设置页面           | shared/ui/screens/对应 Screen                           | 核对文案、设置保存和页面交互                           |
| 通用页面外观           | shared/ui/components                                    | 页面显示回归                                           |
| UI 与 Android 的接口   | shared/ui/contract                                      | 不向 commonMain 引入 Android                           |
| 保存配置、开关门禁     | android/controller、engine/config                       | engine ConfigStoreTest、core SetupPolicyTest           |
| 自检与短时预览         | android/selftest、platform/AndroidPlatformActions       | 正视→转头、离页/后台取消                               |
| 应用列表与图标         | android/applist                                         | 搜索/勾选、列表滚动、取消预读                          |
| 启停服务               | android/runtime/ServiceActivation                       | 关闭取消待启动任务、冷启动回读                         |
| 后端选择与 deadline    | android/runtime/BackendSession、runtime/BackendSelector | BackendSelectorTest、三种后端手工矩阵                  |
| 引擎事件循环与副作用   | engine/runtime                                          | DefaultEngineEffectRunnerTest                          |
| 决策转移               | core/engine/EngineReducer                               | core EngineReducerTest                                 |
| 门控/投票/保亮上限     | core/policy                                             | core commonTest                                        |
| 相机、帧分类           | engine/capture、camera、classify                        | 分类器/阈值变更重跑分类计数场景                        |
| 无障碍与触摸           | backend/a11y                                            | A11ySignalsTest、无障碍实机矩阵                        |
| Shizuku App 侧         | backend/shizuku/client                                  | 后端实机矩阵                                           |
| Shizuku shell / Binder | backend/shizuku/ShellService、protocol                  | 保持类名、descriptor、字段顺序                         |
| power 解析与超时恢复   | backend/shizuku/oracle、restore                         | PowerDumpParserTest、PowerDumpOracleTest、恢复手工矩阵 |
| LSPosed App 侧         | backend/lsposed                                         | 桥接探测、掉线、升级兼容                               |
| system_server 钩子     | xposed/entry、hook、power                               | xposed 单测、故意错签名仍可启动                        |
| 隐藏 API 编译存根      | hidden-api-stubs                                        | compileOnly，最终 DEX 不含存根类定义                   |
| LSPosed 协议           | bridge/protocol                                         | 保留 protocol 包名、AIDL、版本与 transaction 编号      |
| 通知与限频             | android/notification、res/values/strings.xml            | 通知权限、同类 10 分钟抑制                             |

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

## 分层取舍与验证

纯决策、App 推理与最小系统协议分离，增加少量装配成本以隔离 system_server 风险。系统侧不引入模型、相机、协程或文件 I/O。pnpm/Turbo 仅负责工程级工具编排，Gradle 管理十模块依赖与缓存，Node 工具不进入 APK。

模块变动运行 `pnpm verify:architecture` 与 Android 综合门禁；接口、资源生命周期及设备行为按[测试规范](../development/testing.md)验证。当前能力差距集中在[任务清单](../plans/todo.md)。
