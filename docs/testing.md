# 测试规范

## 通用规则

纯函数、reducer、解析、协议、配置和隔离协作使用主机测试；页面与平台交互用真实 UI 场景；相机、ROM 和特权后端用实机矩阵。fake 不证明硬件兼容，NO-SOURCE 不代表 UI 通过。

行为改动先列正常、边界、失败、权限与恢复场景；可自动验证的逻辑先确认旧实现失败，再实现与回归。硬件/UI 先定义可复现场景，条件不足记录未执行。文档、格式和纯重命名记无行为例外；覆盖率只用于诊断，不写无价值用例。

按需加载 [testing-unit](../.agents/skills/testing-unit/SKILL.md)、[testing-browser](../.agents/skills/testing-browser/SKILL.md)、[testing-e2e](../.agents/skills/testing-e2e/SKILL.md)。

## 测试分层

| 层次        | 对象                         | 入口与限制                              |
| ----------- | ---------------------------- | --------------------------------------- |
| Kotlin 主机 | core 规则及模块隔离协作      | Gradle 测试；不证明硬件兼容             |
| Node 工具   | 路径、索引、Hook 与架构守卫  | `pnpm test:tools`，使用临时隔离 fixture |
| 页面交互    | Android miuix 页面与生命周期 | 当前按设备场景验收，无自动化 UI 套件    |
| 完整旅程    | 相机、ROM、三后端与超时恢复  | 真实组件，先核对设备操作授权            |

## 主机入口

```bash
pnpm --filter @gazekeep/app exec ./gradlew :core:allTests
pnpm --filter @gazekeep/app exec ./gradlew :engine:testDebugUnitTest :backend:a11y:testDebugUnitTest \
  :backend:shizuku:testDebugUnitTest :xposed:testDebugUnitTest \
  :android:testDebugUnitTest
pnpm --filter @gazekeep/app exec ./gradlew :shared:testAndroidHostTest
pnpm verify:architecture
```

shared host task 覆盖许可文本搜索的纯逻辑，没有 UI 行为测试；backend:lsposed 和 bridge:protocol 当前没有专属行为测试，以组装、协议测试和实机补证，不能写成有独立测试套件。

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
| 8 页导航/返回/字体/明暗/旋转     | 状态、可访问文字和布局可用                  |
| 自检离页/后台、快速开关/冷启动   | 相机与任务释放，配置回读，关闭不意外重启    |

按 Android 版本、ROM、相机与后端能力选择设备，不以单一机型代表兼容范围。对不同系统超时分别验证降亮前检测、连续周期与停止注视后的熄屏。

## 分类与页面场景

- 分类器/阈值变更：正常光正视 10 次至少 9 次 Looking，转头 90° 十次均非 Looking，闭眼非 Looking；补充眼镜、暗光、多脸、侧脸及相机抢占。只记录计数和耗时，不保存画面或人脸特征。
- 页面：覆盖八页导航/返回、字体/明暗/旋转、授权与未就绪原因、白名单搜索/选择/滚动、配置保存/冷启动、快速开关。
- 自检：Looking→Away 才完成，Unknown 不推进；离页、后台、取消和关闭 Activity 后释放相机。
- 网站：apps/web 尚未实现，无浏览器测试入口；不得把未建立的测试计作通过。

## 记录与验收状态

每条记录附日期、代码版本/APK、设备/ROM/Android、后端/框架版本、系统超时、前提、预期、实际结果和未执行原因。不保存人脸或原始 dump。

文档只维护当前版本的验收状态，不累积历史流水。当前版本完整实机回归未完成；每次交付在[任务清单](../tasks/todo.md)记录实际验证和缺失条件，发布说明列明该版本实测范围与命中候选。未执行、NO-SOURCE 和旧版本结果都不能写成当前通过。
