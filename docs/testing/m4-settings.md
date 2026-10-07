# M4 设置与保护功能验证

> 证据说明：本页保留历史版本与日期。2026-10-08 最新结构重构后的实机回归尚未完成；当前缺口见[维护导航](../maintenance/overview.md)。

## 测试环境

- 设备：小米 12 Pro（Android 14 / HyperOS OS2.0.6.0.ULGCNXM）
- 系统熄屏超时：60000 ms（未改动用户设置）
- 测试后端：LSPosed（v2 协议）
- 构建版本：`cn.rainafter.app.gazekeep` debug

## 功能与设计验收

| 验收项                   | 设计依据             | 结果 | 证据 / 备注                                                                                                                                                        |
| ------------------------ | -------------------- | ---- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| miuix 设置页层级与交互   | 规划 §6 / 架构 §12   | 通过 | 包含总开关、当前后端、检测、范围、保护、隐私、相机自检等子页面；采用 miuix Preference 组件                                                                         |
| DataStore 单一配置中心   | 架构 §12 M4 决议     | 通过 | `ConfigStore` 替代旧 XML，覆盖 `enabled`、`backend_lock`、`lead_millis`、`sensitivity`、`whitelist_enabled`、`packages`、`max_hold_minutes`、`self_test_passed_at` |
| 旧配置平滑迁移           | 架构 §12 M4 决议     | 通过 | `LegacyMigration` 仅迁移后端选择，不继承开启状态，默认关闭总开关与启用白名单                                                                                       |
| 授权与自检前置门禁       | 规划 §6 / 架构 §12   | 通过 | 未授权相机提示相机授权；未通过自检阻断开启并提示自检；通过两步自检前总开关保持关闭                                                                                 |
| 分步相机自检向导         | 规划 §6 / 架构 §12   | 通过 | 第一步正视识别出“在看”；第二步转头识别出“没在看”；两步完成后标记自检通过；采集期间仅短时绑定预览，采集后释放相机                                                   |
| 设置下发与后端联动       | 架构 §12             | 通过 | 修改提前量（8s/5s）、检测灵敏度、保亮上限后下发服务生效；空白名单提示“不会保亮”                                                                                    |
| 总开关启停控制           | 架构 §12             | 通过 | 关闭总开关立即取消待执行任务并停止 `GazeService`；`dumpsys activity service` 确认服务销毁                                                                          |
| 应用选择与保护配置持久化 | 架构 §12             | 通过 | 在范围中选中 `cn.rainafter.app.gazekeep`，在保护中选择 `10 分钟`，`am force-stop` 并重启应用后，DataStore 成功保留上述配置                                         |
| 隐私三句话对齐           | 规划 §10 / AGENTS.md | 通过 | 隐私说明文案与规划 §10 逐字一致，不存图、不上云、未知等价 Away                                                                                                     |
| 默认熄屏与系统设置不破坏 | AGENTS.md 约束       | 通过 | 服务停止后默认按原超时熄屏；系统 `screen_off_timeout` 始终维持 60000 ms                                                                                            |

## 实机回归记录（2026-10-07）

1. **分步自检向导**：
    - 用户打开新版进入自检向导“第一步：正视手机”，相机短暂启动并识别出“在看”。
    - 用户转头进入“第二步：转头或移开视线”，识别出“没在看”，自检进度顺利通过，记录 `self_test_passed_at`。
    - 采集完成后相机立即释放，保亮引擎未在自检中抢占启动。

2. **设置下发与总开关测试**：
    - 设置传给 LSPosed 服务：提前量 8 秒、标准检测、开启白名单、保亮上限 30 分钟。
    - 空白名单时 UI 明确显示警告“白名单为空，当前不会保亮”。
    - 关闭总开关后，通过 `dumpsys activity service cn.rainafter.app.gazekeep` 确认 `GazeService` 已安全停止。

3. **设置项保存与冷启动回读**：
    - 进入“范围”搜索并勾选 `cn.rainafter.app.gazekeep`（白名单 1 个应用）。
    - 进入“保护”将单次最长保亮由 30 分钟调整为 10 分钟。
    - 执行 `am force-stop cn.rainafter.app.gazekeep` 杀进程，随后 `am start` 重新冷启动。
    - 读取设备底层 DataStore `files/datastore/gazekeep_settings.preferences_pb` 确认：
        - `enabled = false`
        - `packages = ["cn.rainafter.app.gazekeep"]`
        - `max_hold_minutes = 10`
        - `whitelist_enabled = true`
        - `lead_millis = 8000`
        - `sensitivity = Standard`
        - `self_test_passed_at` 记录有效时间戳
    - 验证配置持久化与回读机制在进程重启后完全正常。

4. **测试收尾**：
    - 应用保持处于已通过自检但总开关关闭的初始状态（`enabled = false`）。
    - `GazeService` 未在后台运行，无额外资源占用。
    - 系统 `screen_off_timeout` 保持为 60000 ms。
