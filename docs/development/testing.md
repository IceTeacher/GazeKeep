# 测试与验收

## 通用规则

纯函数、reducer、解析、协议、配置和隔离协作使用主机测试；页面与平台交互用真实 UI 场景；相机、ROM 和特权后端用实机矩阵。fake 不证明硬件兼容，NO-SOURCE 不代表 UI 通过。

行为改动先列正常、边界、失败、权限与恢复场景；可自动验证的逻辑先确认旧实现失败，再实现与回归。硬件/UI 先定义可复现场景，条件不足记录未执行。文档、格式和纯重命名记无行为例外；覆盖率只用于诊断，不写无价值用例。

## 验证范围与停止条件

以下用于选择本次检查范围；工程或模块要求的门禁仍需满足。

| 改动                         | 验证范围                                                                 |
| ---------------------------- | ------------------------------------------------------------------------ |
| 文档、纯格式或无行为配置     | 文档/资产检查、受影响文件格式与引用；无需新增行为测试或运行 Android 构建 |
| 代码或脚本行为               | 先确认旧实现失败，再运行受影响模块测试和目录门禁                         |
| 页面、相机、后端、权限或恢复 | 主机验证可隔离逻辑，设备验证受影响交互与真实资源结果；缺条件记录未执行   |
| 跨模块迁移或发布             | 按开发与发布规范执行完整主机门禁、对应 APK 检查及要求的设备矩阵          |

所选检查通过且已覆盖本次风险后结束验证。只有后续改动、检查失败或具体风险未解决时，才重跑或扩大范围；失败先核对实现、断言、调用栈与环境，不弱化安全断言或跳过失败。文档/无行为变更在交付中说明例外，不制造占位测试。

## 测试分层

| 层次      | 对象                                 | 框架与位置                                                          | 规则 Skill     |
| --------- | ------------------------------------ | ------------------------------------------------------------------- | -------------- |
| 单元      | Kotlin 规则与模块隔离协作            | kotlin.test、JUnit4；`commonTest`、`src/test`                       | `testing-unit` |
| 单元      | web 纯逻辑与构建脚本                 | Vitest `unit` project；`apps/web/tests/unit/`                       | `testing-unit` |
| 单元      | 仓库工具：路径、索引、Hook、架构守卫 | `node:test`；`scripts/repo-tools/tests/`，临时隔离 fixture          | `testing-unit` |
| 界面/视觉 | Android miuix 页面、交互与截图       | Robolectric、Compose UI 测试、Roborazzi；`shared/androidHostTest`   | `testing-ui`   |
| 界面/视觉 | web DOM、滚动、生命周期与截图        | Vitest `browser` project（Chromium）；`apps/web/tests/browser/`     | `testing-ui`   |
| 完整旅程  | 相机、ROM、三后端与超时恢复          | androidx.test、UiAutomator；`android/src/androidTest`，另加人工矩阵 | `testing-e2e`  |

上表是目标分层，正在按[任务清单](../plans/todo.md)迁移；各入口当前是否已建立以下方小节为准，未建立的套件不能计作通过。

## 主机入口

```bash
pnpm --filter @gazekeep/app exec ./gradlew :core:allTests
pnpm --filter @gazekeep/app exec ./gradlew :engine:testDebugUnitTest :backend:a11y:testDebugUnitTest \
  :backend:shizuku:testDebugUnitTest :backend:lsposed:testDebugUnitTest :xposed:testDebugUnitTest \
  :android:testDebugUnitTest
pnpm --filter @gazekeep/app exec ./gradlew :shared:testAndroidHostTest
pnpm --filter web test:unit
pnpm test:tools
pnpm verify:architecture
```

shared host task 覆盖许可文本搜索与中英字符串资源一致性；backend:lsposed 使用 Robolectric 与按真实 Parcel 布局应答的本地 Binder 验证握手、注册、seq/STALE 映射与失败关闭，不证明真实 system_server 钩点；bridge:protocol 只有常量，由两端测试与实机补证。Robolectric 模块需在单元测试 JVM 参数中开放 `java.base/jdk.internal.access`。上述任务均已纳入 `pnpm --filter @gazekeep/app test`。web `test:unit` 只运行 Vitest `unit` project；`pnpm --filter web test` 同时运行 `unit` 与 `browser`。

## 界面与视觉入口

```bash
pnpm --filter web exec playwright install chromium-headless-shell  # 首次或升级 Playwright 后
pnpm --filter web test:browser
```

- web：Vitest `browser` project 在无头 Chromium 中运行。测试前由独立 Astro 开发服务渲染中英真实页面，浏览器侧注入 body 后调用 `initWebsite()`，以 `pagehide` 走页面自身的清理路径；KMP 内屏由 `tests/browser/fixtures/showcase.html` 按真实桥接协议模拟就绪、绘制与失败，不依赖 KMP 构建。截图基线位于 `apps/web/tests/browser/__screenshots__/`，按浏览器与平台命名，只覆盖页面排版并隐藏 WebGL 机身；有意改版时删除对应基线后重跑生成，逐张核对再提交。失败截图写入未提交的 `.vitest/`。
- Android：尚未建立自动化界面或截图套件，页面按[页面场景](#分类与页面场景)人工验收。

## 实机入口

当前尚未建立 UiAutomator 实机套件，完整旅程按[实机矩阵](#实机完整矩阵)人工执行。实机套件不进入默认 `pnpm test`，运行前核对设备操作授权。

## 七条必守不变量

1. Unknown 不产生 Extend。
2. 采帧期间真实触摸取消采集且不 Extend。
3. 达到保亮上限下一轮必 Yield，并用实机补证必须熄一次。
4. ScreenOff 释放，直到下一次 ScreenOn 不再采帧。
5. 多脸非 Looking。
6. 后端切换期间不 Extend。
7. 过期 seq 返回 STALE 且不调用 userActivity。

纯测试必须覆盖这些决策不变量，不得放宽默认熄屏断言；Hold 锁释放和系统熄屏是额外硬件验收，不能只靠“不 Extend”。

## 实机完整矩阵

| 场景                             | 期望                                        |
| -------------------------------- | ------------------------------------------- |
| 白名单内正视超过超时             | 可信结果延后一次超时，连续周期不先降亮      |
| 转头/离开                        | 本轮或下一轮正常熄屏                        |
| 闭眼 >2 秒                       | 不保亮                                      |
| 第二张脸、极暗                   | Unknown，不保亮，不持续连拍                 |
| 口袋、朝下                       | 门控休眠，正常熄屏；未落地输入单列缺口      |
| 系统相机/扫码抢占                | 立即让出，不崩溃                            |
| 通话、视频自身保亮               | 不抢系统逻辑                                |
| 连续注视达到上限                 | 强制熄一次，尤其检查 Hold overlay 释放      |
| 划掉任务、省电杀服务             | 可观察异常通知，原超时熄屏                  |
| LSPosed 错误签名/禁作用域/旧 seq | 正常开机，未就绪或 STALE，不保亮            |
| 拒绝/撤销相机权限                | 无法开启或中止，释放资源并说明原因          |
| 无障碍关闭、后端切换/掉线        | 无 overlay 残留，切换期不 Extend，降级/通知 |
| Shizuku 重启/重连/息屏           | 原系统超时恢复，记录只在恢复成功后清理      |
| 页面导航/返回/字体/明暗/旋转     | 状态、可访问文字和布局可用                  |
| 自检离页/后台、快速开关/冷启动   | 相机与任务释放，配置回读，关闭不意外重启    |

按 Android 版本、ROM、相机与后端能力选择设备，不以单一机型代表兼容范围。对不同系统超时分别验证降亮前检测、连续周期与停止注视后的熄屏。

## 分类与页面场景

- 分类器/阈值变更：正常光正视 10 次至少 9 次 Looking，转头 90° 十次均非 Looking，闭眼非 Looking；补充眼镜、暗光、多脸、侧脸及相机抢占。只记录计数和耗时，不保存画面或人脸特征。
- 页面：覆盖页面导航/返回、字体/明暗/旋转、授权与未就绪原因、白名单搜索/选择/滚动、配置保存/冷启动、快速开关。
- 自检：Looking→Away 才完成，Unknown 不推进；离页、后台、取消和关闭 Activity 后释放相机。
- 网站：自动化覆盖桌面与移动端逐幕吸附、减少动态、语言与主题、展示加载失败重试、卸载清理和三张排版截图；无头 Chromium 不代表 Safari、Firefox、真实 GPU、WebGL 机身渲染或实体手机触摸，这些仍按人工验收记录。

## 记录与验收状态

每条记录附日期、代码版本/APK、设备/ROM/Android、后端/框架版本、系统超时、前提、预期、实际结果和未执行原因。不保存人脸或原始 dump。

文档只维护当前版本的验收状态，不累积历史流水。当前版本完整实机回归未完成；每次交付在[任务清单](../plans/todo.md)记录实际验证和缺失条件，发布说明列明该版本实测范围与命中候选。未执行、NO-SOURCE 和旧版本结果都不能写成当前通过。

## 现代 API 102 验收

主机覆盖入口版本/能力/进程门控、Context 缺失、重复初始化、部分安装回滚及清理异常、指示器独立降级、参数替换、跳过原调用、模块异常放行、原调用异常透传且只调用一次。新增 APK 工具 fixture 覆盖缺登记、错误 API/作用域/热重载、旧简介/版本、禁止权限、入口缺失与无参构造丢失、现代具名 Hooker 缺类/回调签名错误、框架类或存根打包、图标缺失/错误引用与位图不匹配。

设备先核对支持 API >=102 与系统钩子能力，再验证管理器识别、现代入口实际加载、桥接就绪、注视延后超时、Away/Unknown 正常熄屏、触摸取消、旧 seq 拒绝、禁用系统作用域与隐私指示器隔离。legacy 使用确实选择旧登记的框架独立回归；API 100 是不兼容场景，不作为现代成功环境。

升级验收从原 APK 覆盖安装，按当前候选的 versionName/versionCode 检查版本、中文简介、品牌图与用户配置保留，重启后核对实际入口。入口改动后的结果必须对应新 APK；安装、框架升级、重启、系统配置和缓存清理先核对授权。主机、静态产物与设备结果分别记录在任务清单，缺条件明确未执行。

当前候选的实际结果、版本与环境集中维护在[任务清单](../plans/todo.md)。这里定义验收要求，不复制特定版本的通过记录；完整矩阵、legacy 与正式签名产物仍须由对应 APK 的证据支持。
