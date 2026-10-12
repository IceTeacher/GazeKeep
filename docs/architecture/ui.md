# UI 与自检

shared 是 KMP miuix UI，commonMain 仅依赖 core 和平台契约，不 import android.*、engine 或具体后端。App 只组装主题、状态与导航，页面、复用组件和文案映射独立维护。

## 当前页面与契约

顶层底栏提供 Home（主页）与 Settings（设置）两个一级标签页，二级页面为 Backend、Apps、Logs（详细日志）、Privacy、SelfTest、About、Notices（第三方开源许可）。导航维护一级标签切换、二级页面回退栈和转场；进入二级页面时隐藏底栏，按钮与授权结果通过 controller/platform 契约进入 Android。

主页顶部采用三态状态横幅卡片（工作中 / 等待可用后端 / 未工作），支持点击直接切换总开关或引导自检；工作中状态始终展示「版本 · 最近：注视了屏幕/未注视屏幕」（不受详细日志开关影响，重配服务时不丢失最近状态）；中部卡片展示 6 项项目与系统环境信息（应用版本、后端就绪状态、相机与自检状态、Android 版本、设备型号、系统指纹）；底部卡片展示隐私承诺入口。

设置页采用无外置分组标题的卡片分组与左侧图标（`startAction`），将检测提前量（5 秒 / 8 秒 / 自定义 0～60 秒）、灵敏度、单次最长保亮（10 / 30 / 60 分钟 / 自定义 1～1440 分钟 / 无限制）下拉项与实验性「隐藏相机隐私指示器」开关（仅在 LSPosed 已就绪且后端选择为自动选择或 LSPosed 时可交互，否则置灰提示需启用 LSPosed 后端），以及白名单、详细日志开关直接内联在卡片中，点击自定义弹出带取消/确定的 `OverlayDialog` 数字输入框；开启详细日志后在下方展示「查看日志」入口；当前后端、应用列表、查看日志、相机自检、隐私、关于通过箭头进入二级页面。日志页提供关键字搜索框、顶栏四项操作（日志级别筛选 `ALL/VERBOSE/DEBUG/INFO/WARNING/ERROR/FATAL`、系统分享、SAF 文件导出保存、更多菜单：`原始视图`切换/`滚动到顶部`/`滚动到底部`/`立即清空日志`），支持结构化卡片视图与纯文本原始视图。隐私页将三条隐私承诺以标题+正文形式合入「安全与隐私」卡片顶部，并包含默认熄屏、相机避让、暗光暂停及实验性隐私指示器说明（共 7 项），不再单独展示隐私承诺卡片。关于页采用柔和渐变头部与卡片列表，展示隐私承诺、GitHub 项目及核心开源组件与内置 `THIRD_PARTY_NOTICES.txt` 许可详情。

GazeKeepController 暴露 state/selfTest StateFlow（含只读系统环境摘要、第三方声明文本与详细日志列表），updateConfig、setEnabled、beginSelfTest、captureSelfTest、cancelSelfTest、clearLogs、refresh、loadApps。setEnabled 返回 Unit，结果通过 UiNotice（NeedsCamera、NeedsSelfTest、EmptyScope、SaveFailed）发布。

PlatformActions 承担相机/通知/Shizuku 权限、无障碍/Shizuku 设置入口、外部链接打开（`openUrl`）、日志分享与 SAF 导出保存（`shareLogs` / `exportLogs`）、HandleBack、SelfTestPreview、AppIcon。平台能力由 Android 实现，不能为了方便在 UI 内直接操作 Binder、配置文件或服务，也不申请 `INTERNET` 或存储权限。

## 自检与启用

步骤为 Looking→Away→Complete，页面展示完整两步引导卡片及每步动作要求与期望效果（第一步正视手机期望「注视了屏幕」，第二步转头或移开视线期望「未注视屏幕」，按钮分别展示「检测第一步」与「检测第二步」），每次检测后展示当前结果及未达期望时的重试提示。正视得 Looking 才推进第一步；转头得 Away 才完成第二步；Unknown 不推进。通过时间存入 ConfigStore，不把开发者历史测试当作新用户资格。

进入自检、自检失败、中途返回或完成自检均不关闭已开启的「注视保亮」；已开启时完成按钮显示「完成」，未开启时显示「完成并开启」。开启总开关检查相机权限、自检和白名单范围；保存失败通过 notice 告知。设置变更先进入单一配置源，应用级 ServiceActivation 按持久化状态调度启停。

SelfTestCoordinator 拥有短期相机预览与采集任务；自检激活期间标记运行状态以避让后台引擎采样，离页、后台、取消与 Activity 关闭释放，不与运行服务争用。应用列表从启动器可见应用构建，不申请 QUERY_ALL_PACKAGES，图标预读取可取消。

## 文案与验证

字符串放 composeResources，隐私三句与[真源](../privacy-security.md)逐字一致。明暗主题、系统字体、长文换行、滚动、返回、冷启动回读与快速开关属于真实 UI 验收。

当前 shared host test 为 NO-SOURCE，没有 UI 行为测试；[testing-browser](../../.agents/skills/testing-browser/SKILL.md)中的设备场景必须实际执行后才能写通过。当前版本的 UI 回归仍待完成。
