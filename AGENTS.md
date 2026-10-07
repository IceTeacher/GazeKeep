# GazeKeep 协作规范

本文件是仓库级规则入口。目录级 `AGENTS.md` 只补充所在模块的职责与验证要求。回答、注释、UI 文案和提交说明用中文；代码标识符、日志 tag 用英文。

## 产品与交付边界

注视保亮为缺少系统级注视保亮能力的 Android 手机提供端上短时检测与延后超时。小米 12 Pro / HyperOS 是首要验证基线，其他设备能力以实测为准。

主要模块已落地，最新重构后的实机回归、完整异常矩阵和发布门禁仍待补齐。实现与验收现状见[维护导航](docs/maintenance/overview.md)，兼容证据见[已测系统](docs/compat/tested-systems.md)。

## 按任务加载

1. 先检查工作树，保留已有改动；读取目标模块 `AGENTS.md`，搜索真实入口、接口、配置和调用方。
2. 文档任务先加载 [docs-maintenance](.agents/skills/docs-maintenance/SKILL.md)，再从[文档索引](docs/index.md)选取命中的活动专题。归档仅在追溯历史时读取。
3. 纯逻辑、解析、协议或隔离协作读取 [testing-unit](.agents/skills/testing-unit/SKILL.md)；页面交互读取 [testing-ui](.agents/skills/testing-ui/SKILL.md)；相机、系统与特权后端读取 [testing-device](.agents/skills/testing-device/SKILL.md)。
4. 单条或分组提交信息分别读取 [commit-message](.agents/skills/commit-message/SKILL.md)、[commit-message-groups](.agents/skills/commit-message-groups/SKILL.md)。

## 规则与实现证据

- 根规则中的产品承诺是不可突破的边界；模块规则补充职责，活动架构是当前设计真源，ADR 解释长期取舍。历史归档不参与当前规则优先级。
- 代码与测试证明现状，不能豁免安全承诺。发现差距时保留承诺，核对源码并登记待办；不能把目标设计写成已实现能力。
- 设计变更先更新相关活动架构；有长期取舍时再补 ADR。术语只维护在 [CONTEXT.md](CONTEXT.md)。

## 产品安全承诺

这些是产品承诺，不是风格偏好。代码评审遇到违反即打回。

1. **Unknown 等价于 Away。** 看不清、没脸、侧脸、多张脸、太暗、相机失败，一律不保亮。不得新增「失败也保持常亮」之类的配置项或分支。
2. **默认熄屏。** 任何链路中断（超时、异常、后端掉线、切换后端）结果都必须是系统按原超时熄屏。
3. **帧不出 `ImageAnalysis.Analyzer` 回调。** 不保存、不复制到长期存在的缓冲、不写文件、不进日志、不跨进程。唯一的预览只在自检向导页。
4. **禁止权限**：`INTERNET`、存储/相册类、`QUERY_ALL_PACKAGES`、`SYSTEM_ALERT_WINDOW`、`READ_PHONE_STATE`。新增依赖若经 Manifest 合并带入 `INTERNET`，用 `tools:node="remove"` 去掉。不接任何统计、崩溃上报、广告 SDK。
5. **日志只存枚举**：时间、包名、Attention、跳过/中止原因、后端、是否 extend、耗时。不存图像、人脸框、欧拉角、概率。调试日志默认关，开启后 24 小时滚动删除。
6. **单次最长保亮**（默认 30 分钟）到点必须熄一次。
7. **system_server 安全**：钩子方法逐候选解析，签名不匹配就不安装并记录，绝不抛异常到系统进程；钩子回调体整体 `try/catch(Throwable)`；PMS 锁内只做计算与 `Handler` 投递，不做 binder 调用、不取其他锁。
8. **不改用户设置**，除了 Shizuku 退路临时改 `screen_off_timeout`，且必须先把原值持久化到 `pendingTimeoutRestore`，并在服务重启 / binder 重连 / 息屏时写回（见[Shizuku 恢复要求](docs/architecture/backends/shizuku.md)）。
9. **不静默失效。** 服务被杀、相机拿不到、后端掉线、无障碍被关，必须发一条通知（同类 10 分钟内最多一条）。检测到「在看」时不弹任何提示。
10. 无障碍服务保持 `canRetrieveWindowContent="false"`，不调用 `event.source`，不读取文本。

## 共同工程约束

- `core` 的业务逻辑不得依赖 Android；`shared/commonMain` 不导入 Android 或具体后端；`engine` 只依赖注入接口；`xposed` 与协议模块保持最小依赖。
- 引擎串行处理 `EngineEvent`，副作用结果回灌 reducer；其他组件不得直接修改引擎状态或绕过 reducer 保亮。
- Kotlin 官方风格；新规则优先进入纯函数。复杂并发、恢复与安全代码用简短中文注释说明为什么。
- Android 依赖与 SDK 版本只在 `gradle/libs.versions.toml` 维护。新增依赖先确认必要性、最新稳定版本、体积与隐私影响；ML Kit 只使用 bundled `face-detection`。
- Android application/library 使用 AGP 内置 Kotlin，不应用 `org.jetbrains.kotlin.android`；不使用 `applicationIdSuffix`。
- UI 使用 miuix，字符串放 `composeResources`；用户结果语言为「在看 / 没在看 / 这次没看清」。隐私三句话以[隐私与安全](docs/privacy-security.md)为真源，与资源逐字一致。
- 隐藏 API、ROM 行为等未经验证时保留 TODO；验证后标注机型与 ROM，不把推断写成事实。

## 验证与完成定义

- 行为改动采用风险分层的测试先行；纯逻辑和缺陷先确认失败用例，硬件/UI 先定义复现与验收场景。文档、格式、纯重命名等无行为改动记录例外。
- 覆盖率用于发现风险；禁止无价值用例、弱化默认熄屏断言、无理由 skip 或空测试收集。
- 运行相关模块门禁、文档/资产检查和 `git diff --check`；`NO-SOURCE`、缺设备、未执行均不能写成通过。
- 完成项、验证命令、未运行项与后续风险追加到 [tasks/todo.md](tasks/todo.md)，不删除历史。每次发布更新已测系统与实际命中候选。

## Git 与执行边界

- 新提交采用 `type(scope): emoji 中文描述`，type/scope 以根 `commitlint.config.cjs` 为准；默认不改写历史。用户明确要求时，先保留恢复引用，限定改写范围并验证文件快照与作者信息不变。
- Lefthook 只处理暂存文件的格式与轻量检查，完整构建/测试按风险执行；详见[开发规范](docs/development.md)。
- 不提交 `local.properties`、签名文件、原始 dumpsys、序列号或含人脸图片。测试样本裁剪到所需 power 段并脱敏。
- 用户已授权的本地工作持续推进。重启设备、改变系统配置、安装到个人设备或发布等动作，先核对本次授权和影响；准备与验证先做到可审查。
- 流程与交付报告见[代理工作流](docs/agent-workflow.md)。
