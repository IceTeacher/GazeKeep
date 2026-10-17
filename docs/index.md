# GazeKeep 文档

`docs/` 根目录只保留本导航；产品与使用文档集中在 `product/`，开发、测试与发布文档集中在 `development/`，架构、任务计划和图片资源分别放在 `architecture/`、`plans/` 和 `assets/`。

安装和使用从下面的产品文档开始；参与开发先读[开发与贡献](development/contributing.md)，再按修改范围查阅架构与测试要求。当前缺口和候选验收证据见[任务清单](plans/todo.md)。

## 了解与使用

| 文档                                      | 内容                               |
| ----------------------------------------- | ---------------------------------- |
| [产品定义](product/overview.md)           | 产品目标、范围、默认行为与当前进度 |
| [使用说明](product/usage.md)              | 安装、自检、后端启用和故障排查     |
| [隐私与安全](product/privacy-security.md) | 相机、权限、数据处理与安全承诺     |

## 开发与贡献

| 文档                                      | 内容                                   |
| ----------------------------------------- | -------------------------------------- |
| [开发与贡献](development/contributing.md) | 环境准备、常用命令、提交与格式要求     |
| [架构总览](architecture/overview.md)      | 模块职责、代码定位与各专题入口         |
| [测试与验收](development/testing.md)      | 主机检查、页面场景、实机矩阵与证据要求 |
| [发布门禁](development/release.md)        | 许可声明、签名、产物检查与发布验收     |

任务与计划统一放在 [plans/](plans/)，包括[任务清单](plans/todo.md)和[v1 稳定性与发布规划](plans/20261014-v1-stability-release.md)，不按进行中或已完成拆分目录。

术语见 [CONTEXT.md](../CONTEXT.md)。Agent 从根 [AGENTS.md](../AGENTS.md) 进入，专用流程、按需加载条件和维护登记集中在 `.agents/`。
