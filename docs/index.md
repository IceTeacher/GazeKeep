# 文档索引

先读根与目标模块 AGENTS，再按本表选择相关页面。文档任务先加载 [docs-maintenance](../.agents/skills/docs-maintenance/SKILL.md)，不要默认读取全部 docs。

根安全承诺规定边界；模块规则补充职责；活动专题描述当前设计，ADR 解释长期取舍。代码/测试证明当前实现，不能豁免承诺；archive 仅在追溯背景时读取，不参与活动日期门禁。

| 文档                                                                                 | 类型     | 加载时机                                                | 维护状态         | 最后复核   |
| ------------------------------------------------------------------------------------ | -------- | ------------------------------------------------------- | ---------------- | ---------- |
| [adr/0001-process-module-boundaries.md](adr/0001-process-module-boundaries.md)       | 架构决策 | 进程与模块边界、纯决策、唯一 APK 或协议分离的取舍时     | 决策记录         | 2026-10-08 |
| [adr/0002-dimming-deadlines.md](adr/0002-dimming-deadlines.md)                       | 架构决策 | 降亮前调度、真实熄屏 deadline、协议 v2 与未知降亮回退时 | 决策记录         | 2026-10-08 |
| [adr/0003-accessibility-hold-mode.md](adr/0003-accessibility-hold-mode.md)           | 架构决策 | 无障碍默认 Hold、Pulse 的 ROM 差异和释放责任时          | 决策记录         | 2026-10-08 |
| [adr/0004-config-self-test-gate.md](adr/0004-config-self-test-gate.md)               | 架构决策 | DataStore 单一设置源、迁移资格与自检前置门禁时          | 决策记录         | 2026-10-08 |
| [adr/0005-foreground-service-lifecycle.md](adr/0005-foreground-service-lifecycle.md) | 架构决策 | 息屏空转、后台 camera FGS 与冷启动恢复的取舍时          | 决策记录         | 2026-10-08 |
| [agent-workflow.md](agent-workflow.md)                                               | 代理流程 | 执行边界、交付报告与工作记录                            | 持续维护         | 2026-10-08 |
| [architecture/GazeKeep-架构设计.md](architecture/GazeKeep-架构设计.md)               | 兼容导航 | 通过旧中文路径或旧章节引用进入时                        | 兼容入口         | 2026-10-08 |
| [architecture/GazeKeep-规划文档.md](architecture/GazeKeep-规划文档.md)               | 兼容导航 | 通过旧中文路径或旧章节引用进入时                        | 兼容入口         | 2026-10-08 |
| [architecture/android-runtime.md](architecture/android-runtime.md)                   | 当前架构 | 服务生命周期、资源所有权、配置迁移、Manifest 与通知时   | 持续维护         | 2026-10-08 |
| [architecture/backends/a11y.md](architecture/backends/a11y.md)                       | 当前架构 | 无障碍事件、overlay、Hold/Pulse 与空闲时钟时            | 持续维护         | 2026-10-08 |
| [architecture/backends/lsposed.md](architecture/backends/lsposed.md)                 | 当前架构 | 系统候选、锁内投递、鉴权、seq、桥接与打包时             | 持续维护         | 2026-10-08 |
| [architecture/backends/shizuku.md](architecture/backends/shizuku.md)                 | 当前架构 | shell Binder、power 解析、临时超时与原值恢复时          | 持续维护         | 2026-10-08 |
| [architecture/capture-classification.md](architecture/capture-classification.md)     | 当前架构 | 帧生命周期、CameraX、ML Kit、投票、阈值与采集预算时     | 持续维护         | 2026-10-08 |
| [architecture/engine.md](architecture/engine.md)                                     | 当前架构 | reducer 状态/事件/副作用、门控、时序、后端选择与上限时  | 持续维护         | 2026-10-08 |
| [architecture/overview.md](architecture/overview.md)                                 | 当前架构 | 模块依赖、进程边界、包名、唯一 APK 与外部入口时         | 持续维护         | 2026-10-08 |
| [architecture/ui.md](architecture/ui.md)                                             | 当前架构 | 设置页面、导航、自检、平台契约和应用列表时              | 持续维护         | 2026-10-08 |
| [compat/tested-systems.md](compat/tested-systems.md)                                 | 兼容证据 | 核对已测设备、ROM、框架与命中候选时                     | 持续记录         | 2026-10-08 |
| [development.md](development.md)                                                     | 开发规范 | 工具链、依赖、构建、提交与 Hook                         | 持续维护         | 2026-10-08 |
| [maintenance/overview.md](maintenance/overview.md)                                   | 维护导航 | 定位源码、资源所有者、实现差距与验证入口时              | 持续维护         | 2026-10-08 |
| [maintenance/可维护性分析与重构提案.md](maintenance/可维护性分析与重构提案.md)       | 兼容导航 | 通过旧中文路径或旧章节引用进入时                        | 兼容入口         | 2026-10-08 |
| [maintenance/维护导航.md](maintenance/维护导航.md)                                   | 兼容导航 | 通过旧中文路径或旧章节引用进入时                        | 兼容入口         | 2026-10-08 |
| [plans/20261008-repository-governance.md](plans/20261008-repository-governance.md)   | 实施计划 | 维护仓库治理、文档、skills 或开发门禁时                 | 计划文档         | 2026-10-08 |
| [privacy-security.md](privacy-security.md)                                           | 隐私安全 | 权限、帧、日志、系统设置与固定隐私文案                  | 持续维护         | 2026-10-08 |
| [product.md](product.md)                                                             | 产品规范 | 产品目标、范围、结果语言、默认使用方式                  | 持续维护         | 2026-10-08 |
| [release.md](release.md)                                                             | 发布规范 | 签名、R8、APK 检查与 Release 准备                       | 持续维护         | 2026-10-08 |
| [testing/m0-thresholds.md](testing/m0-thresholds.md)                                 | 验证证据 | 涉及m0-thresholds对应版本与实机/主机历史时              | 历史证据，可追加 | 2026-10-08 |
| [testing/m1-accessibility.md](testing/m1-accessibility.md)                           | 验证证据 | 涉及m1-accessibility对应版本与实机/主机历史时           | 历史证据，可追加 | 2026-10-08 |
| [testing/m2-shizuku.md](testing/m2-shizuku.md)                                       | 验证证据 | 涉及m2-shizuku对应版本与实机/主机历史时                 | 历史证据，可追加 | 2026-10-08 |
| [testing/m3-lsposed.md](testing/m3-lsposed.md)                                       | 验证证据 | 涉及m3-lsposed对应版本与实机/主机历史时                 | 历史证据，可追加 | 2026-10-08 |
| [testing/m4-settings.md](testing/m4-settings.md)                                     | 验证证据 | 涉及m4-settings对应版本与实机/主机历史时                | 历史证据，可追加 | 2026-10-08 |
| [testing/maintainability-refactor.md](testing/maintainability-refactor.md)           | 验证证据 | 涉及maintainability-refactor对应版本与实机/主机历史时   | 历史证据，可追加 | 2026-10-08 |
| [testing.md](testing.md)                                                             | 测试规范 | 选择测试层、失败场景、验收矩阵与证据                    | 持续维护         | 2026-10-08 |
| [usage.md](usage.md)                                                                 | 使用说明 | 安装、自检、三后端启用与故障排查                        | 持续维护         | 2026-10-08 |

## 计划与证据

活动计划采用 YYYYMMDD-slug.md，唯一“最后维护：YYYY-MM-DD”与前缀、索引复核日一致，日期为最后实质维护。纯机械迁移不推进日期；语义维护改名并更新引用。

证据保持原测试日期和版本，不把本次索引复核日视为实机重测日。测试记录可以追加新版本结果；archive 保留历史正文。旧中文文档是导航，不是新的权威长文。

术语见根 [CONTEXT.md](../CONTEXT.md)，产品设计入口见 [PRODUCT.md](../PRODUCT.md)，任务流水见 [tasks/todo.md](../tasks/todo.md)。
