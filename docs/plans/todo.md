# 任务清单与验证

只维护当前候选仍有效的验收证据、未完成事项和剩余风险；主机测试和构建不替代设备验收，历史调试流水不在此累积。

当前按[v1 稳定性与发布规划](20261014-v1-stability-release.md)推进。产品与发布设计分别以[产品定义](../product/overview.md)、[隐私与安全](../product/privacy-security.md)、[测试规范](../development/testing.md)和[发布门禁](../development/release.md)为准；本页只记录当前候选事实。

## 当前候选与真机矩阵（2026-10-25 至 2026-10-30）

环境：Xiaomi 12 Pro Dimensity（2207122MC），Android 14/API 34，HyperOS `OS2.0.6.0.ULGCNXM`，KernelSU + LSPosed 2.2.1（7912，API 102）+ Sui；Wi-Fi ADB。主要被测为 arm64 Debug 包 `1734c1c`，开发签名，覆盖原正式签名 1.1.2 安装并保留数据；另以 `e7c735c`、`cfd055d` 和正式签名 `192e32b` 构建验证修复与产物。未注明时使用系统超时 15 s、提前量 8 s、宽松灵敏度、上限无限制、后端 Auto（LSPosed）。

| 场景                             | 当前结果                                                                                                                  |
| -------------------------------- | ------------------------------------------------------------------------------------------------------------------------- |
| 正视、转头、闭眼                 | LSPosed 正视 11/11 延长；转头 2/2 Away 且 15 s 熄屏；标准灵敏度闭眼 2/2 Away 并熄屏；宽松模式按设计跳过眼睛检查           |
| 采样中触摸、朝下/口袋、多脸/暗光 | 触摸取消与不延长通过；朝下门控通过；TooDark 与 MultipleFaces 均为 Unknown 并熄屏；Pocket 分支未单独触发                   |
| 相机抢占、视频/通话              | 让出与不崩溃通过；HyperOS「智能」策略会冻结后台，改为「无限制」后相机占用门控和退出恢复通过；视频自身保亮与通话不被抢占   |
| 保亮上限、无障碍                 | 连续 Extend 后 MaxHold，真实熄一次；无障碍 Hold 窗口和亮屏锁释放；解锁后可开启新一轮                                      |
| Shizuku                          | 正视/转头、UserService 重连、临时超时退路的息屏/关闭/重启写回在 Debug 注入下通过；Sui/Shizuku 服务端重启仍未执行          |
| LSPosed                          | 禁用作用域后开机正常并回落 Shizuku，恢复后入口重新加载；旧 seq、实际命中候选和完整指示器异常矩阵未完成                    |
| 自检、权限、页面                 | 离页、返回、移除任务、旋转和撤权释放通过；重新授权可恢复；浅色/深色/大字体/横屏、主页、设置、后端、自检和应用列表场景可用 |
| 实机套件                         | `BackendReadinessTest` 修复后连续通过；其余场景按下方未完成项记录                                                         |

测试后已恢复系统超时、无障碍列表、夜间模式、字体、旋转、应用语言、相机权限和应用配置；保留用户明确的省电策略「无限制」及 Debug 包安装。测试不保存人脸、截图、序列号或原始系统转储。

### 当前修复证据

- HyperOS 后台冻结：真机确认「智能」策略下系统相机使用后会冻结服务，「无限制」下不冻结；使用说明已保留引导，不把该结果推广到其他 ROM。
- Shizuku：UserService 被杀后按退避重新绑定，后端在约 2 s 内恢复；临时超时退路在 Debug 注入下持久化原值，息屏、关闭、重连和重启后写回并读回确认。
- 权限与状态：撤销相机权限后主页显示等待授权，重新授权回前台后服务恢复；后端掉线、撤权和无障碍关闭时状态与通知降级。
- 日志与恢复：日志时间取事件发生时间，列表/导出最新在前并使用 `.txt`；锁屏微调不会反复门控；Hidden API 并发崩溃已改为进程内串行且成功一次后复用。
- 产物：Debug 与开发签名压缩 Release 通过 APK 静态门禁；正式签名 1.1.3 arm64 产物已覆盖升级并保留配置，完整页面矩阵与所有 ABI 的真机安装仍未完成。

## 应用内语言（2026-10-24）

设置页支持跟随系统、简体中文和 English；Android 13+ 使用 LocaleManager，Android 12 及以下使用独立偏好；通知正文、渠道名和常驻通知按应用语言更新，系统无障碍与 LSPosed 描述仍跟随系统语言。主机、API 31/35 和 API 27 模拟器场景已验证；Android 8.1 运行中通知刷新因相机输入条件失败未执行，真实 ROM 的应用语言入口未验收。

## 生效范围白名单 / 黑名单（2026-10-31，issue #11）

分支 `feat/app-filter-mode` 的未提交工作树，x86_64 Debug 包；环境为 `Pixel_8_API_35` 模拟器（Android 15/API 35，Magisk 与 LSPosed 兼容框架），旧版对照为 `main`（`fe2f805`）构建。

- 主机：`pnpm --filter @gazekeep/app verify` 通过，含 `GatePolicyTest` 三种范围、前台未知、相机类应用与判定顺序，`ConfigStoreTest` 旧开关推导、名单分离、降级兼容与 `LegacyMigration`，以及设置页和应用页交互；3 张截图基线有意重录并逐张核对。
- 升级与降级：旧版「白名单开启 + 2 个应用」覆盖升级为仅白名单应用且名单保留；旧版关闭白名单后升级为全部应用；新版写入后不再保存 `whitelist_enabled`，降级回旧版显示白名单开启与原名单。降级后在旧版改动开关，重新升级以旧版最后的选择为准。
- 运行门控：直接写入自检时间戳以绕过真人自检（只改模拟器上的数据，不改产品代码），后端 Auto 实际使用无障碍，系统超时 30 s。黑名单内的 Maps 门控记录 `skip=Blacklisted`，不启动相机，约 30.7 s 按原超时熄屏；名单外的 Chrome 门控放行并采样，虚拟相机无脸记为 Away，不延后，约 30.2 s 熄屏。
- 实机套件：`test:device` 退出码 0；新增的黑名单回读用例连续通过并恢复原范围与名单。`ScreenTimeoutRestoreTest` 因开启后主页停在「等待可用后端」而按前提跳过，`main` 构建在同一模拟器上结果相同，记为未执行。
- 观察：用 `settings put` 在应用运行期间开启无障碍后，主页与后端页仍显示无障碍未就绪，但服务已用无障碍进入 Armed，重装后恢复一致；`ScreenTimeoutRestoreTest` 按前提跳过时没有关闭它刚开启的服务。两者均与本次改动无关，待单独排查。
- 真机结果见下节「生效范围与应用列表真机验收」。

## 应用列表系统应用显示与批量操作（2026-10-31）

分支 `feat/app-filter-mode`（基于 `1d0bac9`）的未提交工作树，x86_64 Debug 包覆盖安装并保留数据；环境为 `Pixel_8_API_35` 模拟器（Android 15/API 35），系统语言中文，启动器可见应用 22 个。

- 主机：`AppListViewTest` 与 `LaunchableAppsRepositoryTest` 的系统应用判定在旧实现上编译失败后通过；`AppsUiTest` 新增默认隐藏与偏好保留、已选系统应用保持可见、隐藏命中提示、选中搜索结果和清空确认；`apps_light_zh` 因顶栏新增菜单按钮有意重录并核对，其余截图不变。
- 系统应用判定：Chrome 带 `UPDATED_SYSTEM_APP`，默认显示；Maps、YouTube、设置、时钟等未更新的预装应用默认隐藏；已选的 Gmail（未更新的系统应用）在隐藏状态下仍显示。
- 交互：搜索 `maps` 时提示「已隐藏 1 个匹配的系统应用」，点按后显示 Maps 并写入 `gazekeep_ui_prefs`；重装（进程重启）后开关仍为开启。搜索非空时「选中搜索结果」把 Maps 并入白名单（2→3）；清空经确认对话框，取消保持 3 个，确认后为 0 且菜单不再提供清空；关闭开关后本页操作过的 Maps、Gmail 仍可见，其余系统应用隐藏。
- 修复：首轮验收发现搜索时打开菜单，输入法候选栏覆盖菜单；改为打开菜单前清除焦点，复测 `mInputShown` 由 true 变为 false。日志页菜单有相同的打开方式，未在本次处理。
- 测试后恢复白名单 Chrome、Gmail 并删除 `gazekeep_ui_prefs`。真机结果见下节。

## 生效范围与应用列表真机验收（2026-11-01）

环境同上方真机矩阵（Xiaomi 12 Pro，HyperOS `OS2.0.6.0.ULGCNXM`，LSPosed 2.2.1 + Sui/Shizuku），arm64 Debug 包为 `f0ee8b2` 加本节修复的未提交工作树，覆盖安装并保留数据；系统超时 60 s、提前量 8 s、宽松灵敏度、上限无限制。门控结论取自调试日志的 `DecisionRecord` 与 `dumpsys power` 的 `mWakefulness`，Chrome 为黑名单应用，便签（`com.miui.notes`）为名单外应用。

| 后端（锁定）     | 黑名单内 Chrome                                                               | 名单外便签                        |
| ---------------- | ----------------------------------------------------------------------------- | --------------------------------- |
| LSPosed          | `skip=Blacklisted` ×2，按原超时熄屏                                           | 放行、真人注视 Looking、延长 3 次 |
| Shizuku          | `skip=Blacklisted`，约 58 s 熄屏                                              | 放行、Looking、延长 4 次          |
| 无障碍（修复前） | 首轮 Blacklisted，随后被换成名单外                                            | —                                 |
| 无障碍（修复后） | `skip=Blacklisted`，约 60 s 熄屏；中途注入滑动后仍 Blacklisted 并约 59 s 熄屏 | 放行、Looking、延长 2 次          |

- 无障碍前台缺陷：修复前无障碍后端把任何包的窗口状态事件当作前台，HyperOS 传送门 `com.miui.contentextension` 的事件把仍在前台的 Chrome 换成名单外包名，于是放行并延长 2 次。现在只接受能解析为该包 Activity 的事件（`A11yForegroundTest` 在旧实现下失败后通过），修复后复测见上表；传送门事件的触发条件未定位，修复后未再出现。
- 系统应用判定（HyperOS）：启动器可见 234 个应用，带 `FLAG_SYSTEM` 的 19 个中 13 个为 `UPDATED_SYSTEM_APP`（浏览器、日历、相册、视频、应用商店等）默认显示；未更新的相机、文件管理、短信、下载管理、设置与 `com.miui.miservice` 6 个默认隐藏，打开「显示系统应用」后出现。`/product/data-app` 的可卸载预装（笔记、计算器、天气、多看等）安装为普通应用，不带系统标志，按用户应用显示。
- 走查：深色模式下中文与英文、字体 1.3 倍的设置页、应用页更多菜单、清空确认对话框均完整显示；清空确认后名单为 0 且菜单不再提供清空。英文发现「1 apps excluded」等单复数错误，计数文案改为「Excluded: 1」「Selected: 1」等写法，自定义提前量与上限改用 `s`/`min`。英文 1.3 倍字体下生效范围说明被右侧取值挤成窄列，与灵敏度等行同一布局，未改。搜索相关操作因输入法会改写 `input text` 未在真机执行，以模拟器结果为准。
- 无障碍开启：`settings put` 写入的 GazeKeep 服务会被系统从列表剔除，系统设置中开关为灰色并提示受限制的设置；临时把 `ACCESS_RESTRICTED_SETTINGS` 设为 allow 后由用户手动开启，测试后关闭并恢复为 deny。使用说明已补充该限制。
- 观察：一次在用户开启无障碍后，按应用语言为英文时设置页与后端页显示为中文（少数文案仍为英文）；之后杀进程由服务拉起、改字体、往返系统设置和重新开启无障碍均未复现，原因未定位。
- 官网与 README：重新生成 kmp-showcase 预编译产物，官网生效范围、常见问题与开始步骤文案补充黑名单；README 设置页与应用列表截图（中英各 2 张）在 `Pixel_8_API_35` 模拟器以演示状态栏重拍。
- 测试后恢复：无障碍列表、受限设置、字体、应用语言、悬浮通知、输入法和 GazeKeep 配置文件（逐字节比对一致），调试日志已关闭并清空，删除 `gazekeep_ui_prefs`。

## 暗光门控累计（2026-11-05，issue #14）

缺陷：暗光 Unknown 不保亮，下一轮只能由触摸或重新亮屏开启，而两条路径都把 `darkUnknownStreak` 清零，`TooDarkRepeatedly` 在真实事件序列下无法触发，暗光每轮仍开相机。现在触摸与息屏保留暗光累计，非暗光采集结果与后端切换仍清零。

分支 `feat/gaze-sensor-gating` 的未提交工作树，x86_64 Debug 包；环境为 `Pixel_8_API_35` 模拟器（Android 15/API 35，LSPosed 后端），前摄以 `-camera-front imagefile:` 加载纯黑图片，`adb emu sensor set light 0`，系统超时 30 s、提前量 8 s、标准灵敏度、生效范围临时改为全部应用。

- 主机：`EngineReducerTest` 新增触摸与息屏两条真实事件序列用例，旧实现下分别得到 1 与 0 后修复通过；`pnpm --filter @gazekeep/app verify` 通过。
- 模拟器：触摸间隔的两轮均为 2 帧 TooDark 的 Unknown（采集 416 ms、276 ms），第三轮 `skip=TooDarkRepeatedly` 且未开相机，屏幕按原超时熄灭；另一组在两轮之间经历一次超时息屏后，下一轮同样 `skip=TooDarkRepeatedly`。
- 观察：修改任意配置会经 `ServiceActivation.reconfigure` 重建会话，暗光累计随之归零；不影响默认熄屏。
- 未执行：真机暗光门控、屏下光感在暗处的 lux 读数与阈值 3 的关系、亮度回升后的重新检测。
- 测试后恢复：生效范围、系统超时与无障碍列表；模拟器调试日志在走查中被误关一次并清空，已重新开启。

## 不改变判定的降耗（2026-11-07，issue #14）

改动：Shizuku PowerDumpOracle 息屏暂停轮询、他人持亮屏锁时在检测窗口外 5 s 退避（PR #23 复查后补上：窗口内仍按 1 s 读取，`PowerDumpOracleTest` 的窗口内释放用例在仅退避的实现下于熄屏点才发出 deadline）；接近/光照/加速度只在其余门控放行后的 ≤1000 ms 窗口内注册（真机实测后由 500 ms 调整）；详细日志开启时 logcat 输出采集分段耗时与门控窗口耗时（只含枚举、毫秒数和读数是否取得）。原计划的「宽松灵敏度关闭 ML Kit 睁眼分类」经真机验证会让 HeadTurned 失效，已撤回，分类固定为 ALL。

- 主机：`PowerDumpOracleTest` 新增息屏与亮屏锁用例，旧实现 10 s 内均读取 11 次；`DefaultEngineEffectRunnerTest` 新增「其余门控跳过不开传感器窗口」「窗口读数仍可跳过」，后者旧实现失败；另增 `SensorWindowTest`、采集分段耗时用例与 GatePolicy 缺失读数不跳过用例。`pnpm --filter @gazekeep/app verify`、文档/资产/架构检查、ktlint 与 `git diff --check` 通过。窗口时长与 ML Kit 分类模式依赖设备与模型运行时，无法用主机测试复现，以下真机结果为依据。
- 模拟器（`Pixel_8_API_35`、黑图前摄、light 0、系统超时 30 s、标准灵敏度）：三轮门控各在同一秒内注册并注销三个传感器，第三轮仍为 `skip=TooDarkRepeatedly`；Shizuku 息屏 16 s 未见 `dumpsys` 子进程。
- 真机环境：Xiaomi 12 Pro、HyperOS 2 / Android 14，开发签名 Debug 包覆盖安装；全部应用、宽松、提前量 8 s、系统超时 60 s；功耗与场景测试临时锁定 Shizuku 后端，旧版为 `d5c1e9f` 的 Debug 构建。
- 传感器窗口：超声波接近传感器（Elliptic Labs，on-change、wakeUp）注册后约 0.56 s 才首报，500 ms 窗口下接近读数每轮缺失；1000 ms 窗口下各轮 `sensorMs` 557–613、三项读数齐全，注册历史显示每轮同一秒内注册并注销（`dumpsys sensorservice` 活动连接段不含包名，不能用于判断）。锁屏跳过时 `sensorMs=null`，不开窗口。
- 接近传感器：每次注册只上报一次 5.0（远），手掌遮挡与真实口袋中均未上报接近；旧版常驻监听同样如此。该机口袋跳过实际由朝下判定完成：真实口袋场景新旧版均为 `skip=FaceDown`，未开相机。
- 判定一致性（宽松）：接近侧脸（约 60–90°）新旧版均为 `HeadTurned`→Away；两人同时入镜新旧版均为 `MultipleFaces`→Unknown。撤回前的 NONE 分类构建在同一侧脸姿势下 4 轮仍判 Looking，同构建改标准灵敏度（ALL）即判 `HeadTurned`，确认 FAST + 无 landmark/contour + NONE 时欧拉角恒为 0。
- 采集分段（宽松）：首帧多为约 370–430 ms，每帧分类多为约 20–60 ms，尾段 4–23 ms，整次约 560–890 ms。
- 新旧版交替功耗对比（ABBA 顺序：新、旧、旧、新；root 读取电量计 `current_now`/`voltage_now` 每秒采样，固定手动亮度 24/255，Wi-Fi ADB 连接中）：

  | 场景（每轮时长）         | 平均功率 新（R1/R4）      | 平均功率 旧（R2/R3）      | 应用 CPU 新/旧（均值） | shell 自身+子进程 新/旧（均值） | 传感器开启 新/旧 |
  | ------------------------ | ------------------------- | ------------------------- | ---------------------- | ------------------------------- | ---------------- |
  | 正视 3 min，持续 Looking | 1624 / 1464 mW（均 1544） | 1650 / 1838 mW（均 1744） | 4.7 s / 8.4 s          | 1.2 s / 1.1 s                   | 7 s / 452 s      |
  | 视频持亮屏锁 2 min       | 1821 / 1961 mW（均 1891） | 2059 mW（R3 无效）        | 1.4 s / 4.4 s          | 0.8 s / 3.6 s                   | 1 s / 245 s      |
  | 息屏 4 min               | 691 / 661 mW（均 676）    | 874 / 812 mW（均 843）    | 1.4 s / 2.7 s          | 0.1 s / 7.9 s                   | 0 / 7 s          |

  三个场景新版平均功率分别约低 11%、8%、20%，且新版两轮均低于旧版两轮。R3 视频阶段播放器未持有亮屏锁（期间触发 3 次检测），已剔除；R1 息屏阶段 shell 计数快照失败，shell 均值只用 R4。息屏电流只在 CPU 唤醒时采到（唤醒比例 68–79%），Wi-Fi ADB 抬高了息屏基线；每组仅 2 轮（视频旧版 1 轮），正视阶段受用户姿态与画面影响，功率差值只作方向性证据。库仑计 1 mAh 步进且更新滞后，未采用。

- 未执行：外接功率计或断开 ADB 的长时功耗、其他机型的传感器首报延迟与欧拉角行为、LSPosed 后端下的同类交替对比。
- 真机测试后恢复：装回新版 Debug 包，后端自动选择、宽松灵敏度，设置与测试前逐项一致；自动亮度已恢复；设备上的采样脚本、电流记录、测试视频与临时 dump 已删除。Shell 的 KernelSU 超级用户授权需用户在管理器中关闭。

## 官网当前验证（2026-10-30）

官网已完成双语数据、七模块页面、Three.js 设备、Shader/连续滚动、miuix Wasm/JS 展示壳、语言/主题桥接、隐私资源校验、加载失败恢复、内屏底栏与访客触摸/拖动交互。当前构建和部署架构见[官网架构](../architecture/web/website.md)，命令见[官网 README](../../apps/web/README.md)。

- 容器内 Chromium、web unit、Astro 检查和完整构建已多轮通过；桌面与移动模拟器已核对中英、明暗、设置演示、内屏滚动、底栏点击、触摸取消和访客操作后的演示复位。
- Xiaomi 12 Pro 的 Chrome 145 与 Firefox Fenix 已实测底栏点击、长按滑动、快速滑动和设备像素比坐标修复；Firefox 超过约 500 ms 才开始滑动仍受浏览器触摸流限制。
- 未执行：Safari、iOS 触摸与缩放、真实 GPU/WebGPU、实体手机完整视觉矩阵、KMP JS 回退演示、移动端逐帧观感和真实慢网首帧。深色主题小号绿色文字对比度约 3.6:1，是否单独加深待产品决定。
- 仍需观察：完整 browser 运行中的偶发计时失败、首次打开内屏偶发下滚，以及不同字号/英文页的演示布局。

## 官网改用 EdgeOne Makers 部署（2026-11-07）

分支 `feat/web-deployment` 的未提交工作树，`edgeone` CLI 1.6.47，Node 24.15.0、pnpm 12.14.0，本机 Linux；EdgeOne 中国站直接上传项目 `gazekeep`（加速区域 global）。

- 本地：`package:edgeone` 对 `build:site` 产物生成 `.edgeone/`，`routes.json` 含根路径到 `/zh/` 的 302 与 `/stats/` 边缘路由；边缘函数包只含 CLI 自带的 7 个开关变量，不含本机路径或凭据。不传 `ENV_STR` 时 CLI 会写入整个进程环境，已由打包脚本固定规避并在上传前检查。
- 本机生产发布：完整构建（含 KMP）后以 API Token 直传成功（部署 `dpr5q6ukk0eo`），`--json` 返回 `type: preset` 与带 `eo_token`、`eo_time` 的默认域名地址；以 `edgeone login` 凭据部署三次均在上传凭据接口返回 `ResourceNotFound: Project is not exist`。
- 线上冒烟：以令牌 Cookie 访问默认域名，根路径 302 到 `/zh/`（先于静态 `index.html` 生效），`/zh/`、`/en/`、两个 KMP 目录 200，`/stats/script.js` 经边缘函数返回 Umami 脚本；`check-web-deploy.ts` 改为 Cookie 携带令牌后对该部署通过。
- 域名切换：维护者将 `gazekeep.rainafter.cn` 切到 EdgeOne（CNAME 至 `pages.dnsoe4.com`），响应头为 `Server: edgeone makers`，`check-web-deploy.ts` 对生产域名通过；`web-preview`、`web-production` 环境中的 Cloudflare secret 与变量已删除。
- CI：PR #25 的预览发布一次通过，冒烟与 PR 评论正常；合并后 `dev` 预览与 `main` 生产发布在 12:39–12:47 间共失败 3 次，分别为 `Invalid EDGEONE_PAGES_API_TOKEN`、`Makers project name exists` 与部署成功后查询地址返回 522，均为 CLI 接口选址或 EdgeOne API 的间歇故障；重跑后预览成功，生产部署 `dp5r43y6ptux` 为 Success 且 `UsedInProd`，生产域名冒烟通过。为此给部署命令加了最多 3 次的整体重试，尚未在 CI 中运行。
- 统计：维护者确认切换后 Umami 能按访客 IP 正确判断地区，`request.eo.clientIp` 写入 `X-Forwarded-For` 生效。
- 未执行：控制台回滚。
- 依赖：移除 Wrangler 后 Vite 的可选 peer `esbuild` 改由 edgeone 依赖的 0.19.12 满足（要求 ^0.27 或 ^0.28），`pnpm peers check` 报未满足；Vite 8 默认不使用 esbuild，`build:site` 正常，未做其他处理。

## 发布与模块仓库

`v1.1.2` 与 `v1.1.3` 发布说明已按「更新日志 / 启用步骤」格式维护。LSPosed 模块仓库元数据、同步工具和 `lsposed-repo` 流程已接入，`v1.1.3` 的本地模拟 tag 得到 `5-1.1.3`；真实 workflow 同步、索引 bot 构建和管理器安装更新仍未完成。

## 未完成事项

- [ ] 在 Xiaomi 12 Pro 以外的 ROM 与 Android 版本上补齐相机、传感器、后台限制和权限异常矩阵。
- [ ] 执行 Sui/Shizuku 服务端重启后的超时恢复；完成 legacy 独立框架回归、实际命中候选记录和多 ROM LSPosed 隐私指示器降级场景。
- [ ] 完成正式签名 Release 的完整设备运行验收：所有 ABI 安装、完整页面矩阵、暗光/多脸/口袋/相机抢占、Hold 释放和正式发布前的异常恢复。
- [ ] 核对 CI 的 dev→main 复用、生产发布、tag 签名包和四 ABI 产物；在 arm64 与 32 位设备/模拟器确认 ABI 包安装和 ML Kit 自检；评估未使用 ML Kit 模型裁剪。
- [ ] 在支持 WebGPU 的真实设备补验 Shader、主题切换、离屏/后台恢复和 120 Hz 表现；补 Safari/Firefox、实体触摸、地址栏伸缩和系统缩放矩阵。
- [ ] 完成 `v1.1.3` 模块仓库真实同步，确认 Release 非草稿、索引 bot 成功，并在 LSPosed 管理器中验证安装和更新提示。
- [ ] 维护者离线备份正式签名密钥库与密码；密钥丢失后无法覆盖升级已安装版本。
- [ ] 在 HyperOS 以外的 ROM 上核对应用列表的系统应用判定（预装应用的标志因 ROM 而异）与无障碍后端的前台识别。
- [ ] 排查按应用语言为英文时页面偶发显示中文的问题（触发条件未知，见上方真机验收）。
- [ ] Shizuku 解析未读取 `lastUserActivityTimeNoChangeLights`（HyperOS dump 中存在该字段）：按 AOSP 规则，带 ON_AFTER_RELEASE 的亮屏锁（如视频窗口）释放后熄屏点顺延到释放时刻 + 超时，现有 deadline 偏早会提前 TooLate，之后不再检测；新旧版都受影响，需真机验证视频自然播完的场景后再修。
- [ ] issue #14 剩余项：用外接功率计或断开 ADB 做长时功耗对比；真机验收暗光门控；决定是否做自适应提前量与连续注视后单帧判定；对比 640×480 与 480×360 分析分辨率的分类计数场景。
- [ ] 官网 EdgeOne 收尾：观察 CI 部署重试的实际效果并核对控制台回滚；在 Cloudflare 侧下线旧 Pages 项目与 Web Analytics 站点。
- [ ] 排查外部开启无障碍后页面就绪状态不刷新的问题，以及 `ScreenTimeoutRestoreTest` 按前提跳过时残留已开启服务的问题。

以上未执行项不计为当前版本通过；实际 APK、设备、ROM、框架和权限条件变化时必须重新标识产物并重跑受影响验收。
