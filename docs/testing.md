# 测试规范与证据

## 分层与测试先行

纯函数、reducer、解析、协议、配置和隔离协作使用主机测试；页面与平台交互用真实 UI 场景；相机、ROM 和特权后端用实机矩阵。fake 不证明硬件兼容，NO-SOURCE 不代表 UI 通过。

行为改动先列正常、边界、失败、权限与恢复场景；可自动验证的逻辑先确认旧实现失败，再实现与回归。硬件/UI 先定义可复现场景，条件不足记录未执行。文档、格式和纯重命名记无行为例外；覆盖率只用于诊断，不写无价值用例。

按需加载 [testing-unit](../.agents/skills/testing-unit/SKILL.md)、[testing-ui](../.agents/skills/testing-ui/SKILL.md)、[testing-device](../.agents/skills/testing-device/SKILL.md)。

## 主机入口

```bash
./gradlew :core:allTests
./gradlew :engine:testDebugUnitTest :backend:a11y:testDebugUnitTest \
  :backend:shizuku:testDebugUnitTest :xposed:testDebugUnitTest \
  :androidApp:testDebugUnitTest
./gradlew :shared:testAndroidHostTest
python3 scripts/check_architecture.py
```

shared 当前没有行为测试，上述 host task 为 NO-SOURCE；backend:lsposed 和 bridge:protocol 当前没有专属行为测试，以组装、协议测试和实机补证，不能写成有独立测试套件。

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

基线小米 12 Pro 必测，另需非小米 Android 14 设备扩展验证。改分类器/阈值重跑 [M0 计数表](testing/m0-thresholds.md)：正视至少9/10、转头0/10 Looking、闭眼非 Looking。

## 记录边界

每条记录附日期、代码版本/APK、设备/ROM/Android、后端/框架版本、系统超时、前提、预期、实际结果和未执行原因。不保存人脸或原始 dump。

历史记录原日期与结果保留，新版本结果追加，不用历史通过覆盖新版缺证。当前最新重构后的实机回归未完成，兼容表仍描述历史实测；状态缺口见[维护导航](maintenance/overview.md)。
