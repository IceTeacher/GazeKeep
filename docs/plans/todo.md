# 任务清单与验证

只维护未完成事项、本次验证和当前候选仍有效的验收证据；主机测试和构建不替代设备验收。

当前按[v1 稳定性与发布规划](20261014-v1-stability-release.md)推进，先复查自检及后台切换时的相机释放与取消链路，再完成异常恢复和正式产物验收。

## 真机矩阵（2026-10-25）

环境：Xiaomi 12 Pro Dimensity（2207122MC），Android 14/API 34，HyperOS `OS2.0.6.0.ULGCNXM`，KernelSU + LSPosed 2.2.1（7912，API 102）+ Sui；Wi-Fi ADB。被测为 `1734c1c` 的 arm64 Debug 包（SHA-256 `5a3de1d8…5aa0`，开发签名，经核心破解覆盖原正式签名 1.1.2 安装，数据保留），用户确认以 Debug 包代替签名 Release；R8/资源压缩相关风险不在本轮覆盖范围。安装后重启，模块日志 `entry=modern_102 startup=Accepted/Installed`，AbstractMethodError 为 0。测试用系统超时 15 s、提前量 8 s，未注明时为宽松灵敏度、上限无限制、后端 Auto（LSPosed）。判定依据为 DecisionRecord 顺序、deadline uptime 与系统 Wakefulness；日志时间戳不可信（见下方缺陷），不用于计时。

| 场景                               | 结果                                                                                                                                                         |
| ---------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 正视（LSPosed，系统设置前台）      | 通过：64 s 内 11/11 Looking/Extended，持续 Awake；单次 559–637 ms                                                                                            |
| 转头                               | 通过：2/2 Away(NoFace)，不 Extend，15 s 熄屏                                                                                                                 |
| 闭眼                               | 宽松模式 6/6 Looking（设计跳过眼睛检查）；标准模式 2/2 Away(EyesClosed)，熄屏，通过                                                                          |
| 采样中真实触摸                     | 通过：3 次命中，2 次 StaleExtend 未延长，1 次 Capturing 中作废重计                                                                                           |
| 朝下 / 口袋                        | 通过：GateSkipped FaceDown，不开相机；口袋因倒放先命中 FaceDown，Pocket 分支未单独触发                                                                       |
| 遮挡前摄 / 多脸                    | 通过：TooDark→Unknown ×2 后 TooDarkRepeatedly 门控；MultipleFaces→Unknown ×2，均熄屏                                                                         |
| 真实暗光                           | 用户人工确认无异常，无本机日志证据                                                                                                                           |
| 视频自身保亮 / 通话                | 通过：B 站自身 SCREEN_BRIGHT 锁，无采样、无相机客户端；通话记录 GateSkipped InCall                                                                           |
| 相机抢占                           | 让出与不崩溃通过；默认「智能」省电策略下失败（见缺陷）；「无限制」下 CameraBusy 门控，退出后 5/5 Extended 恢复                                               |
| 保亮上限 1 分钟（LSPosed、无障碍） | 通过：连续 Extend 后 MaxHold，熄一次；无障碍 Hold 窗口无 KEEP_SCREEN_ON、无亮屏锁；解锁后新一轮 6 次 Extended                                                |
| Shizuku 正视/转头                  | 通过：6 Extended 后 Away 熄屏，系统超时无残留                                                                                                                |
| Shizuku UserService 被杀           | 失败（见缺陷）；手动重选后端后恢复                                                                                                                           |
| 主进程 kill -9 / 划掉任务          | 通过：GazeService 1 s 后重启，Shizuku 重连，检测继续，超时无残留                                                                                             |
| 无障碍运行中关闭                   | 通过：撤销采集与 Hold 后 Disabled，无窗口与亮屏锁残留，16 s 熄屏                                                                                             |
| 隐私指示器                         | 通过：关闭隐藏时自检有绿点；开启后自检与后台采样无绿点；系统相机绿点正常                                                                                     |
| 自检退出                           | 通过：采集中 Home/返回/移除任务，客户端首次缺席 1287/1385/1315 ms、连续 3 次确认 1743/1471/1414 ms；重进后步骤未被迟到结果推进；旋转打断后释放且可完成第一步 |
| 自检中撤权                         | 释放与不崩溃通过，服务停止不循环重启；界面原因与恢复见缺陷                                                                                                   |
| 禁用/恢复 LSPosed 作用域           | 通过：禁用后开机正常、钩子未加载、Auto 回落 Shizuku；恢复后重启入口重新加载                                                                                  |
| 白名单                             | 通过：不在名单时 NotWhitelisted，不开相机                                                                                                                    |
| 页面                               | 通过：浅色/深色/1.3 倍字体/横屏下主页、设置、后端、自检可用，返回逐级退出；应用列表搜索空结果文案正常；导出 6573 行且与卡片时间一致（两者均为写盘时间）      |
| 实机套件                           | BackendReadinessTest 修复后连续 2 次通过；其余 3 项见缺陷                                                                                                    |

未执行：Sui/Shizuku 服务端重启；旧 seq；legacy；命中候选（当前 APK 不记录死线钩子候选）；应用列表加载失败重试；Pocket 分支。测试后已恢复系统超时 60000 ms、无障碍列表、夜间模式、字体、旋转、应用语言、相机权限与应用配置；保留用户改动：省电策略「无限制」、Debug 包安装。

### 修复（fix/device-matrix-issues，2026-10-25）

上述缺陷在本分支修复，主机门禁：`pnpm --filter @gazekeep/app verify`、仓库工具 41 项、架构/文档/资产/格式检查通过；新增或调整的用例均先在旧实现上失败。真机为同一 Xiaomi 12 Pro，安装本分支 arm64 Debug 包覆盖（数据保留），回归后已恢复配置与系统设置。

- HyperOS 冻结：真机对照确认「智能」策略下后台闲置 90 s 后开系统相机 3–6 s 内冻结、回桌面不解冻；加入 deviceidle 白名单（等同忽略电池优化）仍冻结；「无限制」同条件不冻结。修改策略会强停应用，前台服务不自动重启。小米设备设置页新增「后台运行」入口，真机从应用内打开 powerkeeper 后台配置页；使用说明与 README 同步启用步骤。
- Shizuku：UserServiceReconnector 退避重绑；真机杀 UserService 后 1.67 s 重新拉起，引擎 1.64 s 内从 Disabled 回到 Shizuku/Armed。
- 主页与权限：真机撤权后主页显示「等待相机授权」，重新授权回到应用后服务自动恢复为工作中。
- 日志：真机日志时间与 logcat 事件时间一致到毫秒，列表与导出最新在前、导出为 .txt。
- 锁屏空转：reducer 不再对同一截止周期内的微调重新门控（主机用例）。2026-10-27 以 1.1.3 Debug 包（`be40c97`）真机复现原触发条件：锁屏亮屏时 0.4 s 内 55 次 deadline 更新、每次后推 4–5 ms，只门控跳过 2 次（原为 27 s 内 53 次）；另一轮锁屏 30 s 内 3 次更新、跳过 1 次，无采集。
- 隐藏 API 豁免崩溃：模块日志显示 10-25 跑 `test:device` 期间进程原生崩溃 7 次，6 次位于 `SystemPowerBinderResolver.resolve` → `HiddenApiBypass.addHiddenApiExemptions`（CheckJNI：`GetStringUTFChars received NULL jstring`），1 次为相机回调中的野地址访问，疑同源；服务自动重启，套件未暴露。原因是库内前缀集合非线程安全、每次探测都调用；改为进程内串行且只成功一次（主机并发与重试用例先在旧行为上失败）。修复后（`e7c735c` Debug 包）真机连跑 `test:device` 10 轮：第 1 轮 Wi-Fi ADB 掉线失败，其余 9 轮 BackendReadinessTest 与 CameraPermissionTest 通过，ConfigPersistenceTest 因当前保亮上限为无限制按设计跳过，ScreenTimeoutRestoreTest 因安全锁屏跳过；模块日志无新增 GazeKeep 原生崩溃。
- Shizuku 临时超时退路：本机 root/shell 身份均有 DEVICE_POWER，退路无法自然触发，改用 Debug 专用注入（`debug.gazekeep.deny_user_activity=1`，Release 不含，APK 门禁核对）。首轮以 `3f4b204` 发现：持续注视约 45–60 s 后熄屏（序号不变被判 STALE、延长值固定为原值 + 60 s，迟到发现的活动触发写回后立即按原值熄屏）；延长期间用户手动修改的超时被写回覆盖；重启后延长值保留到打开应用。修复后以 Debug 包（arm64，后端固定 Shizuku，系统超时 15000，均已恢复）复测：S1 延长并先落盘原值；S4 持续注视 150 s 屏幕始终亮，超时 63316→98216→132993→170803→208580，原值保持 15000；S2 熄屏、S3 关闭总开关均写回并清记录；S5 强停保留记录、重新打开后亮屏推迟写回并继续延长；S6 杀 UserService 后重连推迟写回、屏幕保持亮并继续延长；S8 延长期间改为 30000，熄屏后保留 30000 并清记录；S7 延长期间重启，开机广播拉起进程连接 shell 写回 15000，未启动服务与相机，HyperOS 未拦截该广播。Sui/Shizuku 服务端重启未执行。
- R8 压缩包（2026-10-27，`cfd055d` 的 arm64 Release，本地 Android 开发密钥签名副本，证书 SHA-256 `767c4d84…bfc423c0`，非正式签名）：Debug 与 Release 各 4 个 ABI 包通过 `verify:apk`，Release 不含调试故障注入；覆盖安装保留配置，重启后模块日志 `entry=modern_102 startup=Accepted/Installed`。注意：apksigner 默认生成 `.idsig`，adb 随之改用增量安装，LSPosed 未更新模块路径、重启后不加载，须用 `--no-incremental` 安装（用户经系统安装器安装不受影响）。冒烟：启动与三后端就绪显示、后台运行跳转 powerkeeper、撤权显示等待相机授权且授权后自动恢复、日志导出 `.txt`、语言切换、杀 UserService 后重连、LSPosed 采集无 MIUISafety 指示器状态均正常；注视计数 LSPosed 连续 8 次延长后转头（HeadTurned）熄屏，Shizuku 与无障碍各 10/10 延长；用户手动自检通过；全程崩溃缓冲区无 GazeKeep。发现并修复：详细日志与 dumpsys 的引擎状态在 Release 中显示为混淆名（如 `state=zn0`），改为保留相关类名并按二进制名取短名，真机复测显示 `Yielded`/`Ready`/`WaitingDeadline`，APK 门禁新增检查。
- 正式签名包（2026-10-27，维护者本机以正式密钥库构建 `192e32b`，4 个 ABI 包证书 SHA-256 均为 `bbf8c24e…14d94f`，均通过 `verify:apk`；arm64 SHA-256 前缀 `961bc5308f0bcd6c`）：先降级安装 GitHub 预发布的正式签名 1.1.2 arm64（校验 SHA-256 与证书一致），配置与自检状态保留并正常运行；再以正式签名 1.1.3 覆盖升级（签名一致，无需核心破解），配置文件升级前后一致，重启后模块日志 `entry=modern_102 startup=Accepted/Installed`；主页工作中、三后端就绪显示、后台运行跳转、杀 UserService 后重连、日志可读均正常；系统超时 15 s 下注视 65 s 连续 11 次延长，首条为唤醒产生新活动后的 StaleExtend；崩溃缓冲区无 GazeKeep。未执行：armeabi-v7a 与 x86_64 包的真机安装、R8/正式签名包上的完整页面矩阵与暗光等人工场景。
- PR #10 评审跟进（Codex 两条 P1，2026-10-27）：UserServiceReconnector 在 bindUserService 同步抛错后不再重试（确认属实，改为失败继续退避，主机用例覆盖，真机未构造绑定异常）；Shizuku 退路在放弃保亮时推迟写回（确认：后端切换后恢复记录无人处理、保亮上限可超出最多 60 s；未注视等路径影响很小，保持推迟）。到达上限改发 ForceRelease、切换后端强制释放。复测时另发现：释放写回原超时后 oracle 未重读，引擎沿用延长 deadline，延长期间的用户活动可能导致注视中熄屏；改为释放后立即重读。Debug 注入真机复测：上限 1 分钟时 MaxHold 后 1 s 内写回并熄屏（修复前按延长值约 35 s 后熄）；延长中切回自动立即写回 15000 并清记录，LSPosed 接手继续延长；延长期间三次按键产生用户活动，屏幕均保持亮且继续延长。未精确复现「检测窗口内活动被立即发现并直接写回」的时序，该路径以执行器用例覆盖。当前设备安装的正式签名包为 `192e32b` 构建，不含上述修复，正式产物需按最终提交重建。
- ScreenTimeoutRestoreTest：2026-10-27 用户临时关闭锁屏密码后，以 `e7c735c` Debug 包在本机执行，测试前临时关闭服务，结束后恢复。Auto（实际 LSPosed）与固定 Shizuku 各 3 轮全部通过、无跳过；每轮实际按电源键息屏并唤醒一次，系统超时始终为用户原值 60000。Shizuku 临时超时退路仍未触发，原值写回路径不在本项覆盖内。
- 触摸作废记录：补记 Aborted(Cancelled)（主机用例）。
- 实机套件：迁至自插桩模块 `e2e`；`test:device` 在本机连续 10 轮 3 项通过、ScreenTimeoutRestoreTest 因安全锁屏跳过；修复异步查杀、同名文案与落盘时序导致的偶发失败。

- [ ] 开机后首屏短暂显示「LSPosed 已就绪」且引擎先选用 LSPosed、约 45 s 后改为 Shizuku，而该次开机模块日志无加载记录；就绪探测为实时 Binder 查询、无缓存，原因未定位。2026-10-27 以 `e7c735c` Debug 包复测未复现：禁用作用域重启后，解锁即打开应用、75 s 内每约 2 s 采样，首屏始终为 LSPosed 未就绪，引擎开机 114.6 s 起直接使用 Shizuku、未出现 LSPosed，模块日志无 GazeKeep 加载；恢复作用域重启后首屏即为 LSPosed 已就绪，引擎使用 LSPosed，模块日志 `entry=modern_102 startup=Accepted/Installed`。原次现象仍无解释，后续禁用作用域场景继续观察。
- [ ] HyperOS 冻结只能引导，无法检测当前策略或冻结状态；其他 ROM 的后台限制未验证。

## 应用内语言（2026-10-24）

- 文案继续使用 Compose Multiplatform Resources，不引入 i18n 框架或 AppCompat；设置页新增「语言」（跟随系统默认、简体中文、English），Android 13+ 走系统 LocaleManager 与 `localeConfig`，12 及以下用独立偏好在 attachBaseContext 套用；android 与 a11y 模块补齐英文原生资源：通知随应用内语言切换；Xposed 描述与无障碍描述由 LSPosed 管理器与系统设置读取，只随系统语言切换。官网演示不显示该项。
- 主机验证：先确认新增用例在旧实现上编译失败；shared 新增 `AppLanguageTest` 4 项与设置页语言交互 2 项，新增 `settings_bottom_light_en` 截图（共 12 张，原 11 张校验未变）；`:shared:testAndroidHostTest`、JS/Wasm 编译、`:android:testDebugUnitTest`、`:android:assembleDebug`、`pnpm --filter @gazekeep/app lint`、全仓 `format:check` 通过；合并 Manifest 含 `localeConfig`，权限无新增。
- 模拟器验证（Pixel_8_API_35，x86_64，系统语言 zh-Hans-CN，Debug APK）：默认显示跟随系统；应用内切到 English/简体中文/跟随系统时，系统记录分别为 `[en]`/`[zh-CN]`/`[]`，页面重建且停留在设置页；冷启动保持；外部写入 en-US 显示 English、写入 fr-FR 显示跟随系统且为中文；系统「应用语言」页只列出中英，选择 en-GB 后应用显示 English；英文下的异常通知与渠道名为英文；应用语言为 `[en]` 时，系统设置的无障碍服务详情仍显示中文描述，符合“只随系统语言切换”的边界。验证后已恢复为 `[]`。
- 低版本模拟器验证（Pixel_6_API_31 Android 12 x86_64；Pixel_2_API_27 Android 8.1 x86，后者使用仅额外保留 x86 原生库的临时 Debug 包，不代表发布支持 x86）：设置页切换 English/简体中文/跟随系统时偏好分别为 `en`/`zh-CN`/空，页面重建并停留在设置页，冷启动保持；跟随系统时改系统语言后应用随之切换；应用为 English 时系统无障碍详情仍为中文描述。API 31 前置相机接主机摄像头完成自检后，GazeService 前台运行中经设置页切换 `en`→空：进程与服务实例不变，常驻通知与两条渠道名随之切换，已展示的提醒保持原文；English 下撤销相机权限，进程重建后的提醒为英文，恢复权限后服务重新前台运行；常驻通知不可划掉，划掉场景不适用；两台均无崩溃。验证后已恢复语言、AVD 相机配置与权限。
- [ ] Android 8.1 的运行中通知刷新只有主机测试：模拟器相机以 640×480 NV21 打开主机摄像头失败并导致 HAL 超时，经 v4l2loopback 转接时模拟器找不到设备或崩溃，自检无法完成，未执行。
- 运行中切换语言（PR #9 评审 P2）：android 接入 Robolectric，新增 `GazeNotifierLocaleTest` 3 项与 `AppLocalesTest` 1 项（SDK 32），先确认旧实现编译失败；`:android:testDebugUnitTest`（30 项）、`:android:assembleDebug`、`:android:lintDebug`（28 条警告与 HEAD 相同）、`verify:architecture`、`pnpm --filter @gazekeep/app lint` 通过。模拟器（同上，GazeService 前台运行）切换 `[]`→`[en]`→`[]`：进程与服务实例不变，常驻通知标题与正文、两条渠道名随之切换，已展示的提醒保持原文，无崩溃。语言写入使用 `cmd locale set-app-locales`，与设置页同为 LocaleManager 记录。重装后 LSPosed 提示模块需重启，期间出现“保亮后端不可用”提醒，与本项无关，未重启模拟器。
- [ ] 真实 ROM（含 MIUI/HyperOS 的应用语言入口）未验收。

## 本次文档维护（2026-10-17）

- 任务清单与专题计划统一迁入 `docs/plans/`，索引统一为“任务与计划”，状态在正文和索引中标注；同步协作规则、文档维护 Skill、读者导航及全部引用。
- 文档检查器与隔离测试改用新目录；先确认旧实现漏检新路径下的计划日期不一致，再修复检查入口。29 项仓库工具测试、文档索引、Agent 资产、架构、受影响文件 Prettier 检查与 `git diff --check` 通过。
- 本次涉及文档组织与仓库检查工具，未运行 Android 构建或设备验收；候选验收证据保留原日期，下列实机与发布缺口继续保留。

## 仓库工具链迁移（2026-10-20）

- 仓库与官网脚本、测试由 `.mjs`/`.cjs`/`.py` 改为 TypeScript，Node 原生类型剥离直接运行；repo-tools 新增 `tsc` 严格检查，官网脚本与测试纳入 `astro check`。Prettier 由 oxfmt 0.73.0 替换（开启 Tailwind CSS 类名排序与 Astro 格式化），`node --check` 由 oxlint 1.88.0 correctness 规则替换；XML 改为只校验语法。修复 oxlint 报出的 2 处问题（三元表达式语句、未使用变量）。
- 展示字体脚本改用 harfbuzzjs 1.6.3（hb-subset）并自行改写 name 表：与 fontTools 4.67.0 产物对比，表集合、422 个码位、702 个字形轮廓与步进、GSUB/GPOS 特性、18 条 name 记录和纵横度量一致；已提交字体未重新生成。
- 主机验证：31 项仓库工具测试（新增 XML 阻断与 TypeScript/Tailwind 用例，先确认旧实现失败）、52 项官网 Node 测试（新增 3 项字体改名与校验和用例）、两工程 oxlint 与 `tsc`、Astro 类型检查（0 错误）、`build:site`、全仓 `format:check`、文档/Agent 资产/架构/Hook 检查、TS 版 commitlint 与 `git diff --check` 通过。未运行：Android 构建与 lint（未改动）、KMP 完整构建、真实编辑器集成。

## 测试框架化（2026-10-22，进行中）

- `testing-browser` 更名为 `testing-ui`，统一负责 app 的 Compose 交互与 Roborazzi 截图、web 的 Vitest 真实 Chromium 测试；`testing-unit` 与 `testing-e2e` 按新分层重写，测试规范登记目标分层与各入口现状。
- web 迁到 Vitest 5.0.5：`unit` project 49 项（字符串资源用例已迁到 app）（按源模块合并，删除与三幕姿态重复的 1 项；shader 保留列表改为与页面图层同源，删除读取源码的比对用例）；`browser` project 15 项，Playwright 1.64.1 无头 Chromium，覆盖桌面/移动端逐幕吸附、减少动态（含运行中切换）、中英语言与主题同步、内屏 Wasm→JS 降级与重试、`pagehide` 清理与往返缓存，以及 3 张排版截图基线。等待内屏事件的上限统一为 15 秒：内屏在 requestIdleCallback 后才加载，机器负载约 20 时原 5 秒上限会偶发超时，调整后连续 3 次全部通过。几何与姿态常量为官网架构写明的设计规格，作为规格断言保留。
- 已用临时改坏实现确认浏览器用例失败（禁用卸载清理、跳过 JS 降级、移除减少动态监听）。`pnpm --filter web test`、lint、Astro 类型检查（0 错误）、`build:site`（产物 shader 注册表仍只含首屏 5 个组件）与全仓格式检查通过。`resources.test.ts` 暂留 web，待 app 侧 Kotlin 资源测试落地后迁出。未执行：Safari/Firefox、真实 GPU 与 WebGL 机身渲染、实体手机触摸。
- app 单元测试：现有 40 个 Kotlin 测试经审查均保留；`PowerDumpOracleTest` 改用 `kotlinx-coroutines-test`（1.10.2，与 coroutines 核心版本一致）虚拟时间，为此给 `PowerDumpOracle` 增加可注入轮询调度器；`SystemInfoFormatterTest` 改用虚构设备样本并补空字段边界；android 模块统一为 kotlin.test。新增 `LsposedKeepAwakeBackendTest`（Robolectric 4.17，9 项，生产构造函数不变，内部构造函数注入 Binder 解析）与 shared `StringResourcesTest`（自 web 迁入）。均先确认旧实现失败（缺少注入点编译失败；改坏 STALE 映射、协议版本校验与英文占位符后对应用例失败）。
- 主机验证：`pnpm --filter @gazekeep/app verify`（Debug 组装、lint、246 项主机测试，失败/跳过 0）、ktlint/TOML 检查、31 项仓库工具测试、repo-tools lint 与架构检查通过。未执行：设备与实机验收。
- Kotlin/JS 依赖安装已修复：Kotlin Gradle 插件下载的 Yarn 1.22.22 会向上查找第一个声明 `packageManager` 的 package.json，原先读到仓库根的 `pnpm@11.7.0` 后中止 `:kotlinNpmInstall`/`:kotlinWasmNpmInstall`，阻断 `:core:allTests` 的 JS/Wasm 任务。根构建脚本在生成的 `build/js`、`build/wasm` 根 package.json 中写入 `packageManager: yarn@1.22.22`，`yarn.lock` 不变。删除生成目录后不设任何绕过变量重跑 `:core:allTests`，JS、Wasm 与主机各 41 项通过；`pnpm --filter @gazekeep/app verify` 通过。
- app 界面与视觉：`shared/androidHostTest` 新增 22 项交互用例与 11 张 Roborazzi 1.76.0 截图（Robolectric 4.17、androidx Compose ui-test 1.12.1，均只进入测试配置），驱动真实 `App` 入口。已用临时改坏实现确认失败（离页不取消自检、首页工作态配色）；校验模式连续两次通过。`pnpm --filter @gazekeep/app verify`（275 项主机测试，失败 0）与 Debug APK 静态门禁通过。未执行：真实 ROM 渲染、厂商字体、系统栏与大字号实机对比。
- app 实机套件：`android/src/androidTest` 新增 4 项 UiAutomator 旅程（配置冷启动回读、相机权限撤销与拒绝、无障碍就绪一致性、熄屏超时恢复），`verify` 纳入 `:android:assembleDebugAndroidTest`，新增 `test:device` 入口；测试依赖只进入测试 APK，主 APK 静态门禁通过。
- 实机套件已在 Xiaomi 12 Pro / HyperOS OS2.0.6 上执行，结果见「真机矩阵（2026-10-25）」的修复记录。

## pnpm 升级（2026-10-22）

- `packageManager` 由 pnpm 11.7.0 升至 12.14.0（当时 `latest`，发布 7 天）；本机 11.7.0 自动切换到锁定版本，未修改全局安装。锁文件只新增首段 pnpm 自身二进制的完整性记录，依赖解析不变；`--no-frozen-lockfile` 重解析无变化。已核对 12.0.0 不兼容变更：仓库无 git 依赖，`--frozen-lockfile` 不带值使用，工作区设置均被识别。
- 验证：冻结安装与供应链策略检查、Hook 自检、31 项仓库工具测试、文档/Agent 资产/架构检查、全仓格式与 lint、官网 64 项测试、lint、类型检查与 `build:site`、`pnpm --filter @gazekeep/app verify` 均通过。未执行：其他平台与 CI 环境。

## 官网 KMP 内屏线上加载失败（2026-10-23）

- 现象：生产站 `/zh/` 内屏不显示，控制台报 `static.cloudflareinsights.com/<哈希>.wasm` CORS 与 522。原因：Cloudflare Pages 项目开启了 Web Analytics，在展示 iframe 的 HTML 末尾注入 `beacon.min.js`；webpack 自动 publicPath 在 module 脚本中取不到 `currentScript`，回退到页面最后一个 http(s) 脚本，Wasm 与 JS 两个运行时的 `.wasm` 都去 beacon 的源加载，降级同样失败。重跑 CI 不能修复（注入发生在响应阶段）。
- 修复：shared 新增 `webpack.config.d/public-path.js` 固定 `publicPath: "./"`；`scripts/build.ts` 检查两个入口不再含自动推断运行时，否则中止构建（`tests/scripts/kmp-showcase.test.ts`，先确认模块缺失时失败）。
- 验证：本地以 Playwright 在所有 HTML 页尾注入跨源脚本复现，修复前 Wasm/JS 入口都向注入脚本的源请求 `.wasm`；完整构建后 `.wasm` 均从 `/kmp-showcase/<运行时>/` 加载，`/zh/` 内屏 `data-ready=true` 且无页面错误。官网 64+1 项 unit、lint、类型检查、完整构建与 `git diff --check` 通过。未执行：浏览器 project 容器基线（未改页面外观）、合入后线上复验。
- [ ] 合入 `main` 发布后在生产站复验内屏加载。
- Cloudflare Web Analytics 保留：统计只针对官网，App 隐私承诺不变，无需补充隐私说明。

## 官网接入 Umami 统计（2026-10-23）

- `/zh/`、`/en/` 头部加载自建 Umami 的 `/stats/script.js`（`data-domains` 限生产域名）与 `/stats/recorder.js`；Pages `_redirects` 不能代理外部域名，新增 Pages Function `functions/stats/[[path]].ts` 转发到 `https://umami.rainafter.cn`，以访客 IP 写入 `X-Forwarded-For`，不转发官网 Cookie。
- 测试先行：Function 3 项（路径与查询、方法/请求体/请求头、非 `/stats` 路径）与页面脚本 2 项均先确认失败；架构测试的镜像目录加入 `functions`，页面渲染工具新增头部外链脚本属性。`wrangler pages dev dist` 实测 `/stats/script.js`、`/stats/recorder.js` 与录制配置接口经代理返回 200，`/` 302、`/zh/` 与 KMP 目录 200。未经代理发送真实上报，避免污染生产统计。
- [ ] 推送 `dev` 后在预览站确认 `/stats/*` 转发，且预览不计入页面统计；合入 `main` 后确认 Umami 后台的访问、地区与会话录制。地区若全部相同，需在 Umami 设置 `CLIENT_IP_HEADER=x-forwarded-for`（仓库外配置）。

## CI 流水线（2026-10-22）

- 新增 GitHub Actions 流水线，使用托管 runner：改动范围判断、仓库门禁、Android 主机门禁、官网完整构建，以及官网 Pages 预览与生产发布（含冒烟和 PR 评论）、tag 的 Release APK 门禁。官网仍用 Cloudflare Pages Direct Upload；k3s 与 GitOps 仓库不接入（当前没有服务端负载）。已用 `gh` 创建 Environment `web-preview` 与 `web-production`（后者仅允许 `main`），写入项目名、站点地址、账号 ID 与 Pages API token。
- 新增 `ci-changes.ts`、`check-web-deploy.ts` 及 5 项测试，先确认模块缺失时失败。冒烟脚本对现网 `https://gazekeep.rainafter.cn` 通过；实测发现 Pages 会把 `index.html` 308 跳到目录地址，因此探测改为目录地址。
- 本地验证：36 项仓库工具测试、repo-tools lint、文档/Agent 资产/架构检查、全仓格式、`git diff --check`、actionlint 1.7.12（含 shellcheck 0.11.0）均通过，`main..dev` 区间 commitlint 通过；Wrangler 输出解析、版本提取与评论正文已用本地样本模拟。
- 前两次 CI 运行暴露的干净环境问题已修复：Wasm `yarn.lock` 未跟踪且以空行结尾（提交锁文件，并用 `.gitattributes` 免除空白检查）；截图基线依赖本机字体（浏览器测试改在 Playwright v1.64.1-noble 镜像中运行并重录基线）；官网未声明 `vite` 依赖（新增 devDependency）；`:core:wasmJsBrowserTest` 找不到 `kotlin-web-helpers` 运行器（推测为冷缓存下 Yarn 1 并发安装，已用共享构建服务串行化，app job 失败时上传 Yarn 日志）。
- 第三次 CI 运行（`dev` 推送，run 39710220826）全部通过：仓库门禁、改动范围判断、`app` 门禁（含 `:core:wasmJsBrowserTest`，约 8.5 分钟）、官网构建与容器内浏览器测试（约 7 分钟）、`dev` 预览上传（别名 `https://dev.gazekeep.pages.dev`）与冒烟。Yarn 串行化后未再复现，但原失败为偶发，单次通过只能说明未复现。预览 job 中 pnpm 自安装耗时 5 分钟，其余 job 约 2 秒，判断为 npm registry 偶发慢。
- 之后 job 依赖改为三层（`app`、`web` 依赖 `changes` 与 `repo`，发布 job 只依赖 `app` 与 `web`），job 与 step 改用英文名，官网检查拆成独立 step；actionlint 1.7.12（含 shellcheck）通过，新任务图与命名尚待下一次 CI 运行确认。
- 随后优化缓存：pnpm 本体按版本缓存并替代 `pnpm/action-setup`（本地确认 npm 安装 `pnpm@12.14.0` 可用）；Gradle 缓存在 `dev`/`main` 推送时写入；新增 Kotlin/JS 工具与 Yarn 缓存；runner 固定为 `ubuntu-24.04`。actionlint 与 shellcheck 通过；推送后首次运行（run 39713395454，冷缓存）通过并写入缓存，三层任务图与英文步骤名生效；第二次手动运行（run 39714436230）命中 pnpm、Gradle 与 Kotlin/JS 缓存，Android app 由约 9.7 分钟降至 1.5 分钟，Website 由约 6.8 分钟降至 3 分钟，整次运行约 3.8 分钟。输入未变的 Gradle 任务（含测试）从构建缓存复用，这是 Gradle 的增量语义，不代表重新执行。Gradle 配置缓存不跨运行复用：试过传入 `cache-encryption-key`，调试日志显示 setup-gradle v6.4.0 的项目状态缓存为 `not-enabled`（需隐藏开关与 Develocity 授权），且命中依赖缓存后配置阶段仅 11–15 秒，已撤销该配置并删除 secret。
- [ ] 核对 PR 预览评论原地更新，以及 `main` 生产发布与冒烟。以上未在 CI 中执行，不计作通过。
- 正式签名：已生成 RSA 4096 密钥库（证书 SHA-256 `bbf8c24e…7514d94f`），保存在维护者本机仓库外，CI 副本存于仅允许 `v*.*.*` tag 的 `release` 环境。`release-apk` 改为正式签名构建、核对证书指纹并创建 GitHub 预发布，发布说明取自 `docs/releases/<tag>.md`。本地正式签名构建通过 `apksigner verify` 与 APK 静态门禁；用开发签名 APK 确认指纹解析能区分不同证书。
- [ ] 首个 `v1.1.2` tag：核对托管镜像上的签名构建、`apkanalyzer`/`aapt2` 门禁、指纹核对与预发布创建。首次推送 `v1.1.2`（run 39740578287）在仓库门禁失败：tag 指向 `main` 的最新提交，与主线共同祖先相同，commitlint 拒绝起止相同的区间，签名构建未执行、未创建预发布。已改为区间为空时跳过提交检查，待合入 `main` 后重新触发。
- [ ] 维护者离线备份正式签名密钥库与密码（本机 `~/.android/` 以外的位置）；丢失后无法覆盖升级。

## 分 ABI 打包与流水线去重（2026-10-22）

- Release 按 ABI 拆分为 `arm64-v8a`、`armeabi-v7a`、`x86_64` 与 `universal`，32 位 x86 不打包；原生库改为压缩存放。本地 unsigned Release：原单包 40.8 MB，拆分后 arm64-v8a 10.5 MB、armeabi-v7a 10.0 MB、x86_64 10.9 MB、universal 17.2 MB。各包 versionCode 相同。
- `verify:apk` 新增必填 `--abi`，要求原生库 ABI 集合一致且全部压缩（读 ZIP 中央目录）。`ci-changes.ts` 新增 `--reuse`：head 或被合入的分支头与 head 树一致且有成功的 push 运行时输出 `verified=true`，PR、合并到 `main` 与合并后同步 `dev` 跳过重复的 Android/官网测试（`main` 仍构建官网用于生产发布）。tag 发版拆到 `release.yml`，等待 `main` 上同一提交的 pipeline 成功后只构建签名包并发布。
- 验证：新增 4 项仓库工具测试（ABI/压缩门禁、ZIP 中央目录解析、树一致复用、主线分叉不复用），先确认旧实现缺少导出而失败；四个本地 Release 包分别通过 APK 静态门禁，通用包只声明 arm64 时按预期报多余 ABI；actionlint 1.7.12 通过（本机无 shellcheck，未做 shell 静态检查）。
- 原生库压缩后安装时需解压，属于安装方式变化；未在设备上验证。
- [ ] 推送后核对 CI：dev 推送完整运行；dev→main PR 只跑仓库检查；合并到 `main` 跳过 Android 门禁与官网测试但完成生产发布；合并后同步 `dev` 整次跳过；tag 等待 `main` 运行后发布四个签名包。
- [ ] 在 arm64 与 32 位设备（或模拟器）上安装对应 ABI 包，确认 ML Kit 原生库加载与自检正常，并验证不同 ABI 包之间覆盖安装。
- [ ] 单独评估裁剪未使用的 ML Kit 模型（快速模式下 `contours.tfl`、`LMprec_600.emd`、`fssd_medium_*` 理论上不加载，约 2.5 MB 压缩体积），需真机自检确认。

## 官网改版与测试镜像布局（2026-10-22）

- 测试目录改为镜像源码（`tests/src`、`tests/scripts`，整页旅程在 `tests/scenarios`），新增 5 项布局守卫并用临时违规文件确认其失败；迁移后旧 3 张截图基线原样通过（unit 54、browser 15）。
- 页面拆为布局与 13 个组件，样式拆为令牌、基础、各区块、吸附与动效文件，`orchestrator.ts` 拆出主题、首屏背景、外框交互与滚动显现模块。拆分前后用关闭脚本的计算样式快照比对（中英 × 3 宽度 × 明暗 × 紧凑，共 24 个状态），剩余差异只在不渲染的声明上（零宽边框样式、网格上的 `flex-direction`、`display: contents` 或隐藏元素）。
- 功能：品牌色与手部肤色明暗共用；主题三态循环，默认跟随系统，视图过渡圆形揭示；图标统一为 Lucide 1.53.0；运行方式与 KMP 演示列表使用开源应用真实图标与包名（模拟器导出，来源与许可登记于 `apps/web/public/media/credits.txt`），演示版本号从 `versionName` 生成；移除专注范围示例列表并改版保护上限卡片；隐私图标改为描边圆环（随后按反馈去掉连线与光晕）；首屏手机漂移；滚动显现与微交互。新增单元测试 13 项（主题偏好、揭示条件、漂移，先确认缺少实现时失败）与浏览器场景 8 项（三态主题、跟随系统、品牌色不变、揭示跳过条件、漂移与减少动态、滚动显现、3 张新截图；与实现同步编写，未在旧实现上单独运行）；3 张既有截图因品牌色与图标有意变化，在容器内重录并逐张核对（运行方式基线未超出差异阈值，删除后重录以反映新图标）。
- 主机验证：web unit 62 项、browser 23 项（容器内连续 3 次通过）、oxlint、Astro 类型检查（0 错误）、完整 Wasm/JS → Astro 构建、ktlint、格式、文档/Agent 资产/架构检查与 `git diff --check` 通过；`:shared:testAndroidHostTest` 因输入未变由 Gradle 复用结果。本地构建预览中检查了桌面明暗、移动端深色、圆形揭示中途帧（内屏已换肤）与演示应用列表。
- 调试中修正：漂移令手机持续重绘，容器内截图与重试按钮稳定性检测超时，改为只剩漂移时约 25 帧、内屏加载失败时停住漂移；漂移只作用于 WebGL 机身，测试等待实体渲染后取样。
- 修正工作流标题停靠时手机轻微偏转（约 1°）并露出约 1% 透明度手部：该停靠点场景进度约 0.07，第一幕的进入、横移与侧倾原从 0 起步；改为从 0.2 开始。新增 1 项单元测试（先确认旧实现得到 0.012 的手部透明度而失败），横移倾斜峰值随之由 0.5 移到 0.6；构建预览中该停靠点投影为正矩形、手部透明度 0，unit 63 项与 browser 23 项（连续 2 次）通过。
- 按反馈移除隐私三项之间的连线与圆环光晕及对应描线动画，保留描边圆环与按列显现；2 张隐私截图基线在容器内重录并核对。
- 修正首屏漂移偶发停止：阻尼跟随在各轴差值低于 1e-3 时吸附到目标，若各轴同时吸附，渲染循环判定已收敛而不再调度，漂移冻结（浏览器场景偶发失败暴露）。改为漂移期间无论是否收敛都按约 25 帧继续调度；浏览器 23 项连续 3 次通过。
- 补齐分享元数据：`og:url`、`og:site_name`、`og:image` 尺寸与替代文本、`og:locale:alternate`、`canonical` 与 Twitter `summary_large_image` 全套，分享图改为 1200×630 的中英首屏截图（原为 512px 方形图标的相对地址）。测试渲染改为同时提取头部 meta，新增 2 项分享信息场景（先确认缺少实现时失败）。期间发现主题切换在视图过渡回调被推迟时会一直不生效（浏览器场景偶发暴露）：改为点击时立即更新偏好，过渡 1 秒未应用时直接应用；browser 25 项连续 3 次通过。未执行：各社交平台实际抓取预览（需部署后用平台调试工具验证）。
- 按反馈把手机状态栏图标改回旧版仿 Android 原生的实心 SVG（Wi-Fi、信号、电池），其余界面图标仍为 Lucide；构建预览中明暗两种主题已核对。
- 优化悬停细节：原下载按钮上浮 2px、图标同时下移 2px，二者抵消使图标看似不动。悬停样式集中到 `interactions.css`，改为箭头循环下落、托盘不动，并补齐外链箭头、文字链接、导航图标按钮、常见问题、探索入口与按压反馈。新增 2 项浏览器场景（移除新样式时确认失败），browser 27 项连续 3 次通过，构建预览中核对了悬停截图。
- 风险：深色底上主题绿对比度约 3.6:1，小号绿色文字（眉题、机外状态）低于 4.5:1，按要求未改色，待产品决定是否为小字单独加深色主题的文字色。
- 未执行：Safari/Firefox 的视图过渡与降级、真实 GPU 与实体手机上的漂移功耗和流畅度、系统主题实时切换的真机表现；LSPosed 未安装在模拟器上，其图标按开源仓库矢量与样本底色渲染。

## 官网本次验证（2026-10-20）

- 根路径跳转：原 `index.astro` 依靠 meta refresh 跳转，浏览器会先渲染“进入 GazeKeep 官网”链接页。改为 `public/_redirects` 的 `/ /zh/ 302` 服务端跳转，兜底页加 `noindex`/`canonical` 并使用站点深色底。新增 1 项测试（缺少 `_redirects` 时先失败）；49 项官网 Node 测试、Astro 类型检查（0 错误）与 `build:site` 通过；本地 `wrangler pages dev dist` 中 `/` 返回 `302 Location: /zh/`，`/zh/`、`/en/` 返回 200。完整构建后已部署（预览 `https://4b331619.gazekeep.pages.dev`），线上 curl 复验 `https://gazekeep.pages.dev/` 返回 `302 location: /zh/`，`/zh/`、`/en/` 返回 200。未执行：真实浏览器观察跳转过程。
- 静态托管部署到 Cloudflare Pages：使用 Wrangler Direct Upload 创建并绑定 `gazekeep` 项目（生产分支 `main`），在 `apps/web/package.json` 固化 `deploy` 脚本（`pnpm run build && wrangler pages deploy dist --project-name=gazekeep --branch=main`）。49 个静态资源成功上传并发布至 `https://gazekeep.pages.dev/`；线上 curl 验证根路由重定向至 `/zh/`（现为服务端 302，见上条），中英双语页面与 KMP Showcase HTML 响应 200，预览环境 `https://c99387c2.gazekeep.pages.dev` 访问一致。
- 首屏加载优化：shaders 注册表裁剪到首屏 5 个组件（分块 2.56 MB → 1.30 MB，gzip 708 KB → 301 KB），Three.js 分块构建后注入 `modulepreload`，样式内联，页面图标由 263 KB 的 512px PNG 改为 6.6 KB WebP，favicon 改为 2.6 KB PNG。新增 5 项构建性能测试（缺少实现模块时先失败）；48 项官网 Node 测试、Astro 类型检查（0 错误）、完整 Wasm/JS → Astro 构建与格式检查通过。
- 浏览器对比（本地 `astro preview`，390×844 移动端，4 倍 CPU 降速，Fast 4G，冷缓存，单次测量）：FCP 846 → 494 ms，机身首帧 1616 → 1141 ms，总阻塞时间 3.3 → 3.0 s。headless Chrome 默认没有 WebGPU 适配器，shader 走 CSS 降级；改用 SwiftShader WebGPU 后裁剪版 shader 运行正常（`data-running="true"`），1440×900 截图中光场正常。未执行：真实 GPU、Safari/Firefox、实体手机。
- 待办：托管端压缩与缓存头尚未配置（`astro preview` 不压缩 `.wasm`，内屏实传 12.5 MB，约 17 s 才就绪；Brotli 后约 3.5 MB）。同源 iframe 中 Kotlin/Wasm 初始化仍有约 1.9 s 的主线程长任务，会阻塞父页面滚动，可评估改为跨源 iframe 隔离进程（桥接需改为纯消息通道）。KMP 字体与字符串在 Wasm 初始化后串行请求，可在展示入口预加载。`public/media/` 约 4.3 MB 资源在源码中没有引用，删除前需确认 `credits.txt` 的许可要求。

- 本轮修复首屏设备闪烁：原脚本执行前内屏以 392×848 逻辑尺寸平铺，Three.js 加载期间显示无倾斜的正面降级外壳，之后才切到倾斜实体机身。改为 `<head>` 内联标记首帧待绘制，CSS 隐藏设备（不改布局），实体机身按叙事姿态首次绘制或确认降级后淡入 0.2s，内联脚本 4 秒限时显示。移动端三幕手机高度上限由 340 提到 460（可用高度 45%→58%），后端与范围设备槽新增独立高度（70%，上限 560）。未加 Three.js modulepreload：首帧等待主要在 4 倍降速下的解析执行，未单独测量预加载收益。
- 主机验证：43 项官网 Node 测试（新增移动端手机尺寸、设备槽高度与三幕吸附点断言，先确认旧实现缺少导出且 340 < 420 而失败）、Astro 类型检查（0 错误）、完整 Wasm/JS → Astro 构建、格式与 `git diff --check` 通过。
- 移动端三幕改为逐幕吸附：新增三个零高吸附标记（`mobileStorySnaps`，进度计算抽为 `mobileStoryProgress`），首屏与运行方式区域延伸覆盖三幕之外的内容，避免分区边界被吸附。440×875 与 390×844 以 300px 平滑 `scrollBy` 正反扫描：三幕停靠进度为 1/2/3（390×844 第二幕 1.999），首屏 0–539px 与运行方式至页尾每步自由停靠；440×560 紧凑模式吸附为 none；1440×900 桌面吸附结果不变。未验证真实触摸惯性与 iOS Safari 吸附手感。
- 浏览器验收（本地 Chrome 构建预览，4 倍 CPU 降速与 Fast 4G，逐帧记录）：1920×934 与 440×875 首次加载中设备可见帧全部为 WebGL 已绘制状态，非 WebGL 可见帧为 0，首屏截图为倾斜姿态。440×875 三幕手机 460px、正文 608–720px 不越过底部；后端与范围手机 560px，460→560 切换时舞台透明度为 0 且位于视口外。440×560 紧凑模式与禁用 WebGL 的降级设备正常显示。未执行：实体手机、Safari/Firefox 与真实慢网首帧。

- 本轮桌面功能展示改为 CSS Scroll Snap 一次一屏：首屏、工作流标题、三幕文案、运行方式与专注范围文案整体强制逐个停靠；运行方式与专注范围增加文案包裹作为吸附目标，隐私区改 `overflow: clip` 以免截断页面吸附。隐私至结尾区包入一个覆盖到页脚的吸附区域，区域内连续滚动。移动端、紧凑与减少动态保持连续滚动。
- 运行方式→专注范围：原横移整段落在约 1500px 吸附动画最快的约 120ms 内，倾斜不可见。改为横移在范围停靠点结束（新增 `featureSpan`），停靠间距由 1501px 缩至 981px（三幕约 1015px），姿态为第二幕→第三幕的镜像；范围文案按阻尼后手机位置越过中线后淡入（新增 `featureReveal`）。
- 主机验证：40 项官网 Node 测试（新增横移区间与文案淡入断言，先确认旧实现缺少对应导出而失败）、Astro 类型检查（0 错误，1 条既有未使用变量提示）、`build:site`（沿用已有 KMP 产物）、格式与 `git diff --check` 通过。
- 浏览器验收（本地 Chrome 开发服务，`scrollBy` 平滑滚动模拟单次滚轮）：1440×900 运行方式→专注范围横移约 300–900ms 完成，与第二幕→第三幕约 180–700ms 相近；中段截图确认侧转与侧倾为第三幕镜像，后段文案半透明且不与手机重叠，终点左右布局正确。隐私之后每次 400px 连续滚到页面底部，反向越过隐私区顶部后回到专注范围停靠点。1280×680 文案高于视口时在包裹内可继续滚动读完，正反对称；390×844 不启用吸附，范围文案透明度不受脚本控制。
- 未复现：用户报告的“专注范围文字先弹出一部分、停顿后再弹出剩余”。`scrollBy` 下为一次连续滑入；无头 Chrome 的 CDP 合成滚轮/手势只按最近点吸附回原位，不代表真实滚轮。本次以缩短停靠间距和文案随手机淡入处理，需在真实鼠标滚轮与触控板上复核；未验证 Safari/Firefox 吸附手感。

- 本轮修复首屏至第一幕滚动掉帧：此区间手部遮挡轮廓每帧为全部顶点分配对象并以字符串去重求凸包，机身绘制晚一帧且读取布局，相机提示环在样式写入后读取 `scrollY` 强制样式重算。改为缓存文档坐标与宿主尺寸、滚动帧内同步绘制、合并矩阵投影与数值凸包、仅在值变化时写入，深色手部改用压暗肤色。专注范围桌面改为手机在左、内容在右；三段桌面横移改为五次缓动并叠加朝运动方向的侧转、侧倾与轻微缩小，水平位移与旋转 120ms 阻尼跟随。
- 主机验证：39 项官网 Node 测试（新增横移倾斜包络、专注范围镜像位置与阻尼收敛测试，先确认旧实现缺少对应导出而失败）、Astro 类型检查、完整 Wasm/JS → Astro 构建通过。
- 浏览器验收（本地 Chrome 有头，1440×900，三幕标题至第一幕 1238px 每帧等距滚动）：开发服务 4 倍 CPU 降速下帧间隔均值 43.5ms → 23.1ms，滚动相关长帧脚本累计约 720ms → 约 7ms；生产构建同环境交替测量 4 倍降速 40.3/31.3ms → 27.8/25.1ms，不降速两版均约 16.7ms。截图检查横移中段倾斜方向、专注范围过渡中段无正文遮挡及终点左右布局、深色手部、390×844 移动布局与减少动态紧凑布局。数值受本机负载影响，未在真实中低端设备与 120Hz 屏幕测量。

- 本轮修复功能介绍区慢速滚动回弹：原设备为 absolute 并由滚动脚本逐帧改写 `top`，合成器先随页面上移、下一帧再拉回。桌面改为 CSS sticky（首屏至专注范围结束），移动端三幕固定段用 fixed、其余段锚定文档坐标；脚本只更新水平位移、姿态、透明度与遮罩。
- 第三幕至后端区：手机连续停在右侧，黑屏期间换到后端页，进入后端区后随滚动渐亮，不再贴入区块；移动端离场设备保持黑屏，后端设备槽进入视口时渐亮，后端槽离开视口后才切到范围页。Header 滚动后改为 `saturate(1.8) blur(20px)` 与约 8% 主题色，取消分隔线；菜单展开时提高底色保证可读。运行方式、专注范围与隐私去掉淡绿底色区块和边框，改为页面底色上的细线行、大号时长摘要与居中三列承诺。
- 主机验证：36 项官网 Node 测试（新增后端区渐亮、移动端黑屏保持、紧凑遮罩与移动端固定/锚定坐标测试，先确认旧实现缺少对应行为再实现）、Astro 类型检查、完整 Wasm/JS → Astro 构建，以及文档、架构、Agent 资产、格式和差异检查通过。构建仍提示延迟加载分块超过 500 kB。
- 浏览器验收（本地 Chrome 无头，构建预览端口 4323）：40px 步进滚轮连续滚过三幕至专注范围，桌面 1280×800 与 390×844 的单帧跳变回位次数均为 0，桌面手机视口位置稳定在 128px；桌面在进入后端区后约 400px 内由黑渐亮。1280×800、390×844、320×640、横屏 844×390 与减少动态整页扫描：Header 始终位于顶部，手机与可见正文无相交，无横向溢出，三幕阅读区间不变。隐私页回执先于手部淡出，后端回执及反向回隐私均在遮罩为 1 时发生；手机内重试热区 44×44，点击后重新就绪。已检查中英、明暗下三个重设计区域与过渡截图。
- 未执行：实体手机触摸与惯性滚动、Safari/Firefox（含 `backdrop-filter` 表现）、系统大字号与真实 GPU 渲染；Chrome 有头 DevTools 因缺少 X 服务不可用。控制台仅有 Kotlin/Wasm `wasmExports` 弃用提示。开发命令与预览服务保留，未发布网站。
- [ ] 在支持 WebGPU 的真实设备补验 Shader 渲染、主题切换、离屏/后台恢复及 120Hz 表现；本次浏览器使用 CSS 背景降级。
- [ ] 补 Safari/Firefox、实体手机触摸、地址栏伸缩与系统缩放矩阵；当前移动布局采用 Chromium 设备模拟。
- [ ] 持续评估 KMP/Skiko 与 Three.js 资源体积及加载体验。

## LSPosed 模块仓库（2026-10-27）

- 新增 `apps/app/distribution/lsposed/` 元数据、`verify:lsposed-repo` 检查（接入 `repo` job）与 `lsposed-repo.yml` 同步流程，规则见[发布规范](../development/release.md#lsposed-模块仓库)。
- 主机验证：新增 8 项 `lsposed.test.ts` 用例（先确认缺少实现时失败），51 项仓库工具测试、repo-tools lint 与 `tsc`、`verify:lsposed-repo`、文档/Agent 资产/架构检查、全仓 `format:check` 通过；actionlint 1.7.12（含 shellcheck）检查三个 workflow 通过。CLI 对 `v1.1.3` 输出 `module_tag=5-1.1.3`，对不一致的 tag 失败退出。
- 未执行：workflow 尚未在 GitHub 上运行，同步、证书复核与模块仓库写入都未经真实验证。
- 2026-10-27：`rainafter.cn` 根域 TXT `lsposed-modules-repo-verification=IceTeacher` 已生效（权威 DNS 与 8.8.8.8 均可查到）；已提交 [submission #2326](https://github.com/Xposed-Modules-Repo/submission/issues/2326)；已创建 `lsposed-repo` 环境（部署范围 `main` 与 `v*.*.*` tag）并配置 `GAZEKEEP_SIGNING_CERT_SHA256`（与 `release` 环境相同）。
- 2026-10-27：提审通过，bot 建立 `Xposed-Modules-Repo/cn.rainafter.app.gazekeep` 并授予 maintain；已推送四个元数据文件（空仓库网页不显示 About 设置，API 修改返回 404），维护者在网页上设置 Description 为 `GazeKeep`、Homepage 为源码地址；`lsposed-repo` 环境已配置 `LSPOSED_REPO_TOKEN`。
- 2026-10-27：PR #15 合入 `dev` 并快进 `main`（`8a948e2`）后手动触发同步 `v1.1.3`（run 40529884312）失败：流程检出 tag 本身，而 `v1.1.3` 早于同步流程、不含工具与元数据，首步即退出，未写入模块仓库。改为工具与元数据取自流程所在提交、tag 源码另检出到子目录，新增 CLI 回归用例（先确认旧实现失败），并用 `v1.1.3` 的版本文件与发布说明本地模拟得到 `5-1.1.3`。
- [ ] 手动触发同步 `v1.1.3`，确认模块仓库 Release 为 `5-1.1.3` 且不是草稿，索引 bot 的 Tag/Build 运行成功。
- [ ] 在 LSPosed 管理器的仓库页检索、下载并安装 GazeKeep；下一个正式版确认管理器能提示更新。

## 发布说明格式与模块 README（2026-10-27）

- 发布说明只保留「更新日志」「启用步骤」，Release 标题改为 tag，「安装包」由 `release.yml` 生成，构建信息只写入 Step summary；实测范围、未测与已知限制改由本清单承载，同步调整根与 app 的 AGENTS、测试规范、发布规范和 testing-e2e Skill。`v1.1.3.md` 取自维护者在 GitHub 上修改后的正文，`v1.1.2.md` 按同一格式整理（签名变更并入更新日志）。`verify:docs` 新增发布说明文件名与章节检查。
- 模块仓库 README 改为由根 README 生成：相对图片改为固定提交的 raw 地址、相对链接改为 blob 地址，不再维护单独副本；本地渲染的 5 张图片地址均返回 200。
- 主机验证：新增发布说明检查与 README 生成用例（均先确认旧实现失败）；其余结果见本次提交说明。
- [ ] 合入后把 GitHub 上 v1.1.2 的 Release 正文改为新格式（原正文已备份），重新同步 `v1.1.3` 使模块仓库 README 与源码仓库一致。

## 未完成事项

- [ ] 在 Xiaomi 12 Pro 以外的 ROM 与 Android 版本上补齐相机与传感器异常矩阵（本机结果见「真机矩阵（2026-10-25）」）。
- [ ] Shizuku 服务端重启（Sui 重启）时的超时恢复未在真机执行；其余退路场景见「真机矩阵（2026-10-25）」的 2026-10-27 复测。
- [ ] 补齐 LSPosed 隐私指示器的多 ROM 与异常降级场景，并让 APK 记录或可查询实际命中候选。
- [ ] 补齐两台设备的作用域配置和实际命中候选；完成确实选择旧登记的 legacy 框架独立回归。当前真机环境与 APK 已经直接核对，见本轮真机调试记录。
- [ ] 完成正式签名 Release 的设备运行验收（开发签名旧版需卸载重装），通过后再把预发布转为正式版。

## 候选 1.1.2 验证记录（2026-10-14）

- 候选版本为 1.1.2（versionCode=4），以下为 2026-10-14 基于 `29c27a0` 工作树的验证记录，相关实现现已入库；记录中的 APK 未正式发布，本次文档维护未重建或重新验收。相机绑定与解绑在主线程执行，每轮只解绑自身 use cases；共享采集协调器处理自检优先权与服务交接，清理排空前不开始下一轮。
- 已先确认旧实现失败：Home 后服务避让状态未释放、旧采集污染新自检状态、取消后的 Extend 仍回灌成功并更新 oracle，以及 close 没有取消排队采集。另核实 1.1.1 的两个现代 Hooker 在 Release 被裁剪，system_server 虽记录 Installed，实际调用仍抛 AbstractMethodError；改用受 entry 保留规则保护的具名回调，并逐一校验完整 intercept 签名。新 fixture 先确认旧门禁漏检，扩展后的门禁拒绝原缺陷 APK。修复保留自检已通过步骤，返回后手动重试；旧租约、旧结果和旧 finally 不能影响新会话。
- 压缩运行发现 ML Kit metadata 反射注册器的无参构造器被 R8 full mode 移除，导致启动崩溃。已用最小 fixture 确认旧门禁漏检，扩展后实际失败 APK 三个注册器均被拒绝；仅补充 SDK 已保留类的构造器规则，不禁用压缩或保留整个 SDK。静态门禁覆盖这些注册器；撤权场景又复现 camera FGS 粘性恢复的权限/资格拒绝崩溃，已加入前台启动权限检查与失败清理，失败返回 START_NOT_STICKY；1.1.1 曾验证撤权/重新授权恢复；1.1.2 仍按下表记录实际回归，不将旧二进制的设备结果计为本轮通过。
- 分类回调结束前不关闭图像，不通过线程中断提前释放在途 ML Kit 图像；相机先解绑，等待回调排空后交还所有权。分类器 close 在在途调用结束后兑现。释放耗时按下表记录；真机基线及任务异常不返回时的实际表现仍需设备证据，未新增释放时限。
- 完整主机与 shared host 共 153 项测试通过（core 41、engine 27、android 25、无障碍 2、Shizuku 14、xposed 41、shared 3），失败/错误/跳过均为 0；Node 工具测试 27 项通过。架构、文档/资产、全仓格式和 `git diff --check` 通过。Debug 与开发签名压缩 Release 构建、lint 通过（每种 lint 15 条警告、0 Error/Fatal）。设备连接与写操作已由用户授权补齐：1.1.2 开发签名压缩候选已覆盖安装并重启验收，未卸载或清空数据；重启后 LSPosed 现代入口及桥接就绪，Hooker 抽象方法异常为 0。相机自检 Home 取消实际打开前摄，取消至连续三次客户端缺席的观测上界为 1911 ms。最终结果见下表。

## 本轮设备验收交接

下表记录模拟器验收；用户新授权的真机调试记录见下文。本轮 1.1.2 修改现代框架回调，已安装最终压缩候选并重启加载，未沿用 1.1.1 的设备通过记录。模拟器使用 Android 15/API 35，镜像 `google/sdk_gphone64_x86_64/emu64xa:15/AE3A.240806.043/12960925:userdebug/dev-keys`，实际前摄为 Camera ID 10。升级前基线为 enabled=true、Auto 实际选择 Shizuku、LSPosed 未就绪、提前量 8000 ms、Loose、白名单关闭、上限无限制、隐藏指示器开启；相机已授权，通知未授权；系统超时为 `2147483647` ms，充电保持亮屏为 0，自动旋转/方向为 1/0，字体 1.0、夜间模式 1。Shizuku 为 13.6.0.r1086.2650830c；本轮从框架启动日志确认 LSPosed 2.2.1（7912）。升级后应用 UI 和运行时状态显示当前后端 Lsposed、已就绪；现代入口 startup=Accepted、hook_startup=Installed。作用域配置中的实际命中候选仍未知。

| 场景 ID    | 前提与步骤                                                              | 预期                                                             | 当前实际与缺失条件                                                                                                                               |
| ---------- | ----------------------------------------------------------------------- | ---------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| XPOSED-02  | 同签名覆盖安装最终压缩候选，整机重启后启动应用，读取入口及实际桥接/回调 | 新系统侧代码生效，LSPosed 就绪，无 Hooker 抽象方法错误，配置保留 | 通过：安装 Release 候选并重启；modern_102 Accepted/Installed，桥接就绪，未见 Hooker AbstractMethodError，配置保留；安装包 SHA-256 与本地产物相同 |
| CAM-01     | 确认前摄实际打开，自检采集中按 Home、返回、取消或关闭 Activity          | 当轮取消、无迟到结果；记录客户端消失及再次可用耗时               | 部分通过：Camera ID 10 实际打开；Home 后相机客户端缺席连续观测 3 次，上界 1911 ms；返回后迟到结果隔离未单独验证                                  |
| CAM-02     | 自检旋转、快速离页重入、撤权/重授权；服务运行时进入自检并取消           | 无跨轮次清理，无重叠绑定；返回手动重试，服务按配置恢复           | 本轮待验收；真机与完整快速重入仍待补                                                                                                             |
| CAM-03     | 采集前及采集中打开系统相机或扫码                                        | 相机让出、不保亮、不崩溃；其他应用结束后可再检测                 | 本轮待验收；需完整保亮恢复与真机补证                                                                                                             |
| HOLD-01    | 短系统超时、1 分钟保亮上限，持续注视至上限再触摸                        | 自身 Extend 不重置累计；到点实际熄一次，真实触摸开启新轮次       | 未执行；需可信注视输入及无障碍真实窗口                                                                                                           |
| SAFE-01    | 采帧触摸、手动息屏/亮屏、暗光、多脸、口袋/朝下、视频/通话               | 取消/Unknown 不保亮；息屏不采帧，门控和系统策略正确              | 本轮待验收；触摸、暗光/多脸/传感器及通话等仍待补                                                                                                 |
| BACK-01    | 切换/掉线、关闭无障碍、划掉任务及服务被杀/重连                          | 不 Extend、无 Hold 残留；通知及恢复状态可解释                    | 未执行；需三后端就绪及临时配置恢复记录                                                                                                           |
| SHIZ-01    | 实际触发临时超时退路，息屏/销毁/Binder 断开重连或 Shizuku 重启          | 原值先持久化；写回并读回成功才清记录，离线保留待恢复状态         | Debug 注入下通过（2026-10-27，见真机矩阵）；Sui/Shizuku 重启未执行                                                                               |
| XPOSED-01  | 旧 seq、作用域关闭/恢复、指示器开/关及其他应用用相机                    | 过期请求不 userActivity，其他应用绿点不受影响，恢复准确          | 未执行；需完整框架/候选记录，错误签名使用隔离测试                                                                                                |
| RELEASE-01 | 对实际压缩签名 APK 覆盖升级，回读配置，自检/后台/异常与页面矩阵         | 签名兼容、配置保留，导航/日志时间/大字体等正常                   | 本轮待验收；正式签名产物与完整页面矩阵未完成                                                                                                     |

释放耗时为取消命令发起至连续三次客户端缺席的观测上界，包含命令与轮询延迟，不能视为精确解绑时间或统一释放预算。不保存人脸预览或原始系统转储，不卸载或清空应用数据。

## 本轮真机调试（2026-10-14）

用户授权代理经 ADB 调试，已按调试任务覆盖安装候选版，并经用户确认重启真机。设备为 Xiaomi 12 Pro Dimensity（2207122MC），Android 14/API 34，HyperOS `OS2.0.6.0.ULGCNXM`，系统指纹为 `Xiaomi/daumier/daumier:14/UP1A.231005.007/OS2.0.6.0.ULGCNXM:user/release-keys`。LSPosed 模块元数据为 v2.2.1（7912）；Shizuku 为 13.6.0.r1086.2650830c；KernelSU 管理器为 v3.3.0（32601-2），LKM 工作中。

连接时实际安装版本为 1.0.0（1），与本轮候选的证书摘要相同。已同签名覆盖安装 1.1.2（4），未卸载或清空数据；重启后回读的 APK SHA-256 与下列开发签名压缩 Release 完全一致。相机授权和通知拒绝保留。最新运行时回读为 enabled=true、backend=Lsposed、提前量 8000 ms、Loose、白名单关闭、上限无限制、隐藏指示器开启；系统超时为 `60000` ms，充电保持亮屏为 7。

KernelSU 中 Shell（UID 2000）的超级用户授权原先关闭，导致 `su` 不可见。按用户的日志读取修复要求开启该授权后，`su -c id` 返回 UID/GID 0、SELinux 域 `u:r:ksu:s0`，已能读取 LSPosed 私有日志。Shell root 授权保持开启，供后续调试；设备 SELinux 仍为强制执行。原始日志仅在内存筛选，不保存至仓库。

| 场景       | 当前实际与缺失条件                                                                                                                                                                                                                                        |
| ---------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| XPOSED-02  | 真机通过：最终候选覆盖安装并重启；本次启动的模块日志确认 `entry=modern_102 startup=Accepted` 与 `startup=Installed`，应用页面和运行时实际使用 LSPosed、桥接已就绪。当前模块日志中 AbstractMethodError 为 0；实际钩点候选与作用域配置仍待补证。            |
| CAM-01     | 部分通过：升级后、重启前自检确实打开本应用相机，Home 后客户端缺席连续观测 3 次，观测上界 1277 ms；重启后再次实际打开相机，工具栏返回后客户端缺席连续观测 3 次，观测上界 1240 ms，并返回设置页。完整退出路径、再次可用耗时、迟到结果隔离与重复测量仍待补。 |
| RELEASE-01 | 部分通过：同签名覆盖升级、重启启动、最终 APK 摘要、权限与运行配置已回读；正式签名、完整页面/异常矩阵仍未完成。充电保持亮屏为 7，本轮未把保持亮屏状态作为注视延长或实际熄屏的证明。                                                                        |

用户报告注视保亮可能失效后，先对照真实服务状态、deadline、分类判定、Extend 结果和 PowerManager 屏幕状态；用户随后反馈可能没有注视屏幕、当前正常。下列受控测试未复现持续失效，因此本次未修改代码或分类阈值，版本仍为 1.1.2（4）。非受控观察曾出现一条 StaleExtend，受控正视测试未复现；不能据此推断系统进程异常。准备阶段已锁屏或在 Dozing 状态开始的尝试均排除，不计为通过或分类失败。

下列三项均使用正常光线、竖屏、宽松模式、15 秒系统超时、充电保持亮屏关闭；记录来自本应用 DecisionRecord 和系统实际 Wakefulness，不保存画面。正视轮次在测量期间没有注入触摸；转头轮次之间用 ADB 触摸重置超时，最后停止注入，等待系统自行熄屏，不能将这些轮次算作真实手指触摸取消验收。

| 场景            | 实际结果                                                                                                                                     |
| --------------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| LSPOSED-LOOK-FG | GazeKeep 前台连续正视：10/10 次 Looking、10/10 次 Extended，Stale 为 0；屏幕持续 Awake 60.7 秒，超过原 15 秒超时。                           |
| LSPOSED-LOOK-BG | 前台切到系统设置、GazeKeep 后台连续正视：10/10 次 Looking、10/10 次 Extended，Stale 为 0；屏幕持续 Awake 59.2 秒；日志前台包确认是系统设置。 |
| LSPOSED-AWAY    | 用户头转离屏幕约 90°：10/10 次 Away（NoFace），0 次 Extend；最后一轮后停止注入触摸，实际进入 Dozing，总观测 48.0 秒。                        |

用户明确要求停止矩阵，并表示多脸、暗光、眼镜、视频和通话均无法配合。闭眼、真实触摸取消、口袋/朝下、相机抢占、后端掉线、最大保亮、三后端恢复及其余完整矩阵未执行或仍缺本轮证据；相机撤权只进行到进入自检的准备阶段，没有执行撤权，不能计为通过。停止后退出自检，恢复 Loose、系统超时 `60000` ms、充电保持亮屏 7；回读确认原总开关、提前量、白名单、上限、指示器与 LSPosed 后端，检查时本应用无相机客户端。正式签名和完整矩阵尚未完成。

## 本轮本地产物

两种实际 APK 均通过扩展后的静态门禁，包含双登记/API/作用域、版本/简介、图标、禁止权限、存根/框架类未打包，两个现代具名 Hooker 的完整回调签名，以及 Manifest 登记的组件注册器类与无参构造器。签名为本地 Android 开发签名；证书 SHA-256 为 `767c4d84713b09987c7f877397319b55e1f095293bc90c6f8383ac81bfc423c0`，与升级前模拟器安装包一致。

- Debug SHA-256：`813749208c59478d13604d7151ad126651c6b1529dc5f179a66b0aa90b5e168d`。
- 开发签名压缩 Release SHA-256：`3c17ebfc186274db52f6c115c9fbbd6e5e39d3d74ae8121cc423733959eedd94`；本地文件为 `apps/app/android/build/outputs/apk/release/android-release-development.apk`。

开发签名压缩副本不作为正式发布产物，不沿用旧测试包或其他二进制的人工结果。
