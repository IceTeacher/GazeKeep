---
name: testing-unit
description: 编写或评审 GazeKeep Kotlin 与 Node 主机测试时加载；强调测试先行、行为风险、可重复性和默认熄屏。
---

# 单元测试

## 何时加载

纯规则、reducer、解析、协议、配置、隔离 adapter 与仓库检查工具使用本 Skill。页面交互读 [testing-browser](../testing-browser/SKILL.md)，真实硬件与完整旅程读 [testing-e2e](../testing-e2e/SKILL.md)。先按[测试规范](../../../docs/testing.md)选择受影响模块。

## 工作顺序

1. 从用户可观察行为列正常、边界、错误、权限、恢复与取消场景。
2. 先写最小测试并确认旧实现失败；文档、格式和纯重命名记录例外，不造占位测试。
3. 实现后运行受影响测试与门禁；失败先核对实现、fixture、调用栈和环境，不弱化断言、扩大 mock 或无理由 skip。

## 选择与边界

- core/commonTest 验证纯规则，Android 模块 src/test 验证隔离协作；Node 工具使用 node:test 与临时仓库，不操作真实索引。
- 固定时间与依赖，使用窄接口和隔离 fixture；不靠真实摄像头、随机等待或执行顺序。
- 测试公开行为和稳定协作，覆盖率只用于发现风险，不锁定私有调用顺序或无意义 getter。
- Unknown 不 Extend、触摸取消、上限 Yield、ScreenOff 释放、多脸非 Looking、切换不 Extend、旧 seq STALE 不 userActivity 是必守不变量。
- 不 Extend 不证明 Hold 锁释放或系统熄屏；NO-SOURCE 不代表有行为测试。

## 项目入口

```bash
pnpm test:tools
pnpm --filter @gazekeep/app exec ./gradlew :core:allTests
pnpm --filter @gazekeep/app test
pnpm verify:architecture
```

具体模块与缺失套件见测试规范，不捏造测试入口。
