---
name: testing-unit
description: 编写、修复或评审 GazeKeep 纯规则、reducer、解析、协议、配置及隔离协作的主机测试时使用，强调风险覆盖与测试先行。
---

# 主机与单元测试

先加载 [测试规范](../../../docs/testing.md) 和最近模块规则。纯规则在 core/commonTest；Android 隔离适配在对应模块 src/test。当前没有测试的模块明确写缺口。

## 行为工作顺序

1. 从用户可观察结果列最小场景：正常、边界、错误、权限、恢复和取消。
2. 先写或调整失败用例，运行确认旧实现失败；再修改实现并运行受影响门禁。文档、格式、纯重命名和无行为配置记例外，不造占位测试。
3. 用可控时间、窄接口和隔离 fixture；不靠真实摄像头、随机等待或测试顺序。测试 reducer 事件与副作用，不锁定无意义 getter/私有细节。
4. 失败先查实现、fixture、断言和环境；禁止弱化 Unknown 默认熄屏断言、无理由 skip、空收集或为覆盖率制造用例。

必须保持七条不变量：Unknown 不 Extend、触摸取消、上限 Yield、ScreenOff 释放/停止采集、多脸非 Looking、切换不 Extend、旧 seq STALE 不 userActivity。

主机不 Extend 不能证明 Hold 锁已释放或系统已熄；真实硬件使用 [testing-device](../testing-device/SKILL.md)。页面行为使用 [testing-ui](../testing-ui/SKILL.md)。当前命令以测试规范/模块规则为准，不能捏造不存在的套件。
