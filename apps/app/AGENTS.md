# Android 工程

本目录继承根 [AGENTS.md](../../AGENTS.md)，保存完整 Gradle 工程。各模块继续继承本文件，并由模块 `AGENTS.md` 补充职责与门禁。

## 交付边界

- Android 九个运行模块与一个编译存根模块统一位于本目录，应用模块为 `android`。模块名、包名、协议及外部入口保持稳定；目录迁移不改变产品安全承诺。
- 产品为缺少系统级注视保亮能力的 Android 手机提供端上短时检测与延后超时。兼容性按 Android、ROM 和后端能力实测，不指定优先机型。
- 主要模块已实现，完整实机回归、异常矩阵与发布门禁仍待补齐；当前实现见[架构总览](../../docs/architecture/overview.md)，缺口见[任务清单](../../docs/plans/todo.md)。

## 不可变工程约束

- `core` 的业务逻辑不得依赖 Android；`shared/commonMain` 不导入 Android 或具体后端；`engine` 只依赖 core 和注入接口；`xposed` 与协议模块保持最小依赖。
- 引擎串行处理 `EngineEvent`，副作用结果回灌 reducer；其他组件不得直接修改引擎状态或绕过 reducer 保亮。
- Kotlin 官方风格；新规则优先进入纯函数。复杂并发、恢复与安全代码用简短中文注释说明为什么。
- Android 依赖、SDK 与 ktlint 版本仅在 `gradle/libs.versions.toml` 维护。新增依赖先确认必要性、最新稳定版本、体积与隐私影响；ML Kit 选型要求见 [engine 规则](engine/AGENTS.md)。Node workspace 只提供任务入口，不进入 APK 依赖。
- Android application/library 使用 AGP 内置 Kotlin，不应用 `org.jetbrains.kotlin.android`；不使用 `applicationIdSuffix`。
- UI、结果语言与资源要求见 [shared 规则](shared/AGENTS.md)；隐私三句话以[隐私与安全](../../docs/product/privacy-security.md)为真源，与资源逐字一致。
- 隐藏 API、ROM 行为等未经验证时保留 TODO；验证后标注机型与 ROM，不把推断写成事实。

## 产品安全承诺

这些是产品承诺，不是风格偏好。代码评审遇到违反即打回。

1. **Unknown 等价于 Away。** 看不清、没脸、侧脸、多张脸、太暗、相机失败，一律不保亮。不得新增「失败也保持常亮」之类的配置项或分支。
2. **默认熄屏。** 任何链路中断（超时、异常、后端掉线、切换后端）结果都必须是系统按原超时熄屏。
3. **帧不出 `ImageAnalysis.Analyzer` 回调。** 不保存、不复制到长期存在的缓冲、不写文件、不进日志、不跨进程。唯一的预览只在自检向导页。
4. **禁止权限**：`INTERNET`、存储/相册类、`QUERY_ALL_PACKAGES`、`SYSTEM_ALERT_WINDOW`、`READ_PHONE_STATE`。新增依赖若经 Manifest 合并带入 `INTERNET`，用 `tools:node="remove"` 去掉。不接任何统计、崩溃上报、广告 SDK。
5. **日志只存枚举**：时间、包名、Attention、跳过/中止原因、后端、是否 extend、耗时。不存图像、人脸框、欧拉角、概率。调试日志默认关，开启后 24 小时滚动删除。
6. **单次最长保亮**（默认 30 分钟，支持自定义或设为无限制）未设为无限制时，到点必须熄一次。
7. **system_server 安全**：钩子方法逐候选解析，签名不匹配就不安装并记录，绝不抛异常到系统进程；钩子回调体整体 `try/catch(Throwable)`；PMS 锁内只做计算与 `Handler` 投递，不做 binder 调用、不取其他锁。
8. **不改用户设置**，除了 Shizuku 退路临时改 `screen_off_timeout`，且必须先把原值持久化到 `pendingTimeoutRestore`，并在服务重启 / binder 重连 / 息屏时写回（见[Shizuku 恢复要求](../../docs/architecture/backends/shizuku.md)）。
9. **不静默失效。** 服务被杀、相机拿不到、后端掉线、无障碍被关，必须发一条通知（同类 10 分钟内最多一条）。检测到「注视了屏幕」时不弹任何提示。
10. 无障碍服务保持 `canRetrieveWindowContent="false"`，不调用 `event.source`，不读取文本。

## 测试与验收

- 主机测试验证纯规则、reducer、解析、协议、配置和隔离适配；页面验收验证导航、自检、权限和生命周期；完整旅程验证真实相机、ROM、三后端与系统超时恢复。
- 按任务加载 [testing-unit](../../.agents/skills/testing-unit/SKILL.md)、[testing-ui](../../.agents/skills/testing-ui/SKILL.md) 或 [testing-e2e](../../.agents/skills/testing-e2e/SKILL.md)。主机单元测试使用 kotlin.test 与 JUnit4，界面与截图使用 shared 的 Robolectric、Compose UI 测试与 Roborazzi，实机套件使用 android 的 UiAutomator。
- 禁止弱化默认熄屏断言。fake、主机通过和“不 Extend”不能证明硬件兼容、Hold 锁释放或实际熄屏；`NO-SOURCE`、缺设备与旧版本结果都不能写成当前验收通过，移动目录不能视为实机验收。
- 在本目录执行 `./gradlew`，或从仓库根执行 `pnpm build`、`pnpm lint`、`pnpm test`。完整主机门禁使用 `pnpm --filter @gazekeep/app verify`；相关验证以[测试规范](../../docs/development/testing.md)为准。Turbo 不缓存 Android 任务，Gradle 保留自身缓存。
- 发布说明记录该版本的实测系统、后端/框架版本与实际命中候选，不能从构建或主机测试推断设备兼容性。

## 安全与执行边界

- 设备安装、重启、系统配置和发布不因本地实现任务而自动获得授权；具体执行边界继承根规则。
- 不提交 `local.properties`、签名文件、生成物、原始 dumpsys、序列号或含人脸图片；测试样本仅保留所需 power 字段并脱敏。
