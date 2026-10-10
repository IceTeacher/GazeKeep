# UI 与自检

shared 是 KMP miuix UI，commonMain 仅依赖 core 和平台契约，不 import android.*、engine 或具体后端。App 只组装主题、状态与导航，页面、复用组件和文案映射独立维护。

## 当前页面与契约

顶层底栏提供 Home（主页）与 Settings（设置）两个一级标签页，二级页面为 Backend、Apps、Privacy、SelfTest、About、Notices（第三方开源许可）。导航维护一级标签切换、二级页面回退栈和转场；进入二级页面时隐藏底栏，按钮与授权结果通过 controller/platform 契约进入 Android。

主页顶部采用三态状态横幅卡片（工作中 / 等待可用后端 / 未工作），支持点击直接切换总开关或引导自检；中部卡片展示 6 项项目与系统环境信息（应用版本、后端就绪状态、相机与自检状态、Android 版本、设备型号、系统指纹）；底部卡片展示隐私承诺入口。

设置页采用无外置分组标题的卡片分组与左侧图标（`startAction`），将检测提前量、灵敏度、单次最长保亮下拉项及白名单、决策日志开关直接内联在卡片中；当前后端、应用列表、相机自检、隐私与安全、关于通过箭头进入二级页面。关于页采用柔和渐变头部与卡片列表，展示隐私承诺、GitHub 项目及核心开源组件与内置 `THIRD_PARTY_NOTICES.txt` 许可详情。

GazeKeepController 暴露 state/selfTest StateFlow（含只读系统环境摘要与第三方声明文本），updateConfig、setEnabled、beginSelfTest、captureSelfTest、cancelSelfTest、refresh、loadApps。setEnabled 返回 Unit，结果通过 UiNotice（NeedsCamera、NeedsSelfTest、EmptyScope、SaveFailed）发布。

PlatformActions 承担相机/通知/Shizuku 权限、无障碍/Shizuku 设置入口、外部链接打开（`openUrl`）、HandleBack、SelfTestPreview、AppIcon。平台能力由 Android 实现，不能为了方便在 UI 内直接操作 Binder、配置文件或服务，也不申请 `INTERNET` 权限。

## 自检与启用

步骤为 Looking→Away→Complete。正视得 Looking 才推进第一步；转头得 Away 才完成第二步；Unknown 不推进。通过时间存入 ConfigStore，不把开发者历史测试当作新用户资格。

开启总开关检查相机权限、自检和白名单范围；保存失败通过 notice 告知。设置变更先进入单一配置源，应用级 ServiceActivation 按持久化状态调度启停。

SelfTestCoordinator 拥有短期相机预览与采集任务；离页、后台、取消与 Activity 关闭释放，不与运行服务争用。应用列表从启动器可见应用构建，不申请 QUERY_ALL_PACKAGES，图标预读取可取消。

## 文案与验证

字符串放 composeResources，隐私三句与[真源](../privacy-security.md)逐字一致。明暗主题、系统字体、长文换行、滚动、返回、冷启动回读与快速开关属于真实 UI 验收。

当前 shared host test 为 NO-SOURCE，没有 UI 行为测试；[testing-browser](../../.agents/skills/testing-browser/SKILL.md)中的设备场景必须实际执行后才能写通过。当前版本的 UI 回归仍待完成。
