# 任务清单与实测基线

本页仅维护当前有效实测基线（耗时、功耗、兼容表现）与未完成事项（Backlog），不累积历史调试流水。产品与开发规范见 [product.md](../product.md) 与 [development.md](../development.md)，下一版本详细设计见 [v1.1.7 迭代规划](v1.1.7.md)。

## 当前实测环境与验收基线（至 `v1.1.6` / `versionCode = 8`）

| 验证维度             | 测试环境与产物                                                                                                                        | 已验证结论与关键实测基线                                                                                                                                                                                                       |
| -------------------- | ------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **真机核心闭环**     | Xiaomi 12 Pro Dimensity（`2207122MC`），Android 14 / API 34，HyperOS `OS2.0.6.0.ULGCNXM`，LSPosed 2.2.1（7912，API 102）+ Sui/Shizuku | 正视 11/11 延长、转头与标准模式闭眼熄屏、触摸取消、`TooDark`/`MultipleFaces` 熄屏、单次保亮上限 `MaxHold` 强制熄屏（含无障碍 `Hold` 释放）、控制中心磁贴启停、省电/低电量门控跳过、日志复制与清空确认均通过。                  |
| **采集耗时与传感器** | Xiaomi 12 Pro 真机分段计时（`640×480`、ML Kit `FAST` + `ALL`）                                                                        | 相机打开至首帧 `370～430 ms`，单帧分类 `20～60 ms`，尾段 `4～23 ms`，整轮 `560～890 ms`。Elliptic Labs 超声波接近传感器注册后 `0.56 s` 首报，`1000 ms` 窗口下三项传感器 `557～613 ms` 齐备；口袋实测由朝下（`FaceDown`）拦截。 |
| **功耗 ABBA 对比**   | Xiaomi 12 Pro（Shizuku 后端，手动亮度 24/255，每秒电量计采样）                                                                        | 按需 `1000 ms` 传感器窗口 + Shizuku 息屏停轮询/亮屏锁退避后：正视 3 min 均功 `1744 → 1544 mW`（**−11%**）、视频亮屏锁 2 min `2059 → 1891 mW`（**−8%**）、息屏 4 min `843 → 676 mW`（**−20%**）。                               |
| **三后端与边界修复** | Xiaomi 12 Pro 真机 + `Pixel_8_API_35` 模拟器（Android 15）                                                                            | 白/黑名单三后端过滤、系统应用默认隐藏与已选可见、Shizuku 临时超时退路写回、Chrome 视频 `ON_AFTER_RELEASE` 释放后 `lastUserActivityTimeNoChangeLights` 顺延且保留 `MaxHold` 累计、无障碍 Activity 解析过滤传送门均通过。        |
| **自动化套件与产物** | 主机 JVM + `Pixel_8_API_35` 模拟器 + 四架构 Release APK                                                                               | `pnpm --filter @gazekeep/app verify`（含 12 张 Roborazzi 截图基线）、模拟器 `test:device` 5/5 全通（真机因设安全锁屏 4 通 1 跳过）、四架构 Release APK `verify:apk` 静态门禁全通。                                             |
| **官网与 EdgeOne**   | Node 24.15 / Playwright 1.64.1 Docker 容器 / EdgeOne Makers 生产环境 `gazekeep.rainafter.cn`                                          | `dist` 体积由 `41.5 MB` 降至 `30.7 MB`（Brotli `9.8 MB`），同构 DOM 预览与 KMP Wasm 首帧像素差 MAE `< 3.8`，`test:unit`（116 用例）与 `test:browser:docker`（58 用例 + 6 截图）全通，生产与预览部署及 Umami 代理正常。         |

## 未完成事项（Backlog）

- [ ] **推进 `v1.1.7` 迭代实施**：按 [v1.1.7 迭代规划](v1.1.7.md) 在已完成保亮后端告警时效性限频与后端切换串行化（PR #33 / #34）基础上，交付 SAF 零权限配置导入导出与连续注视稳态单帧快判（首帧 `Looking` 通过、非 `Looking` 回退 3 帧 2 票）。
- [ ] **规划与排期 `v1.1.8`**：按 [v1.1.8 迭代规划](v1.1.8.md) 承接顺延的充电豁免单次保亮上限（`unlimitedHoldWhileCharging`）、平躺仰视阅读兼容（`faceDownGateEnabled`）与多 ROM 后台引导扩充。
- [ ] **跨 ROM 与 Android 版本实机矩阵**：在 Xiaomi 12 Pro（HyperOS 2）以外的 ROM（ColorOS、OriginOS、OneUI、原生 AOSP）上补验相机抢占、传感器首报延迟、应用列表预装系统应用标志、无障碍前台识别与后台冻结策略。
- [ ] **LSPosed 与 Shizuku 深度异常矩阵**：执行 Sui/Shizuku 服务端进程重启后的超时恢复；在独立选择旧登记的框架上回归 `legacy` 入口；补充旧 `seq` 拒绝与多 ROM 相机隐私指示器降级实测。
- [ ] **正式签名 Release 实机全量验收**：由 CI 对 `v1.1.6` / `v1.1.7` tag 执行正式签名多 ABI 构建与指纹核对，在真机与 32 位环境补验正式签名包覆盖安装、锁屏/横屏磁贴交互与外接功率计/断开 ADB 的长时续航。
- [ ] **LSPosed 官方模块仓库闭环**：确认正式版触发 `lsposed-repo.yml` 同步后非草稿发布、官方索引 bot 构建成功，并在 LSPosed 管理器内验证检索与更新提示。
- [ ] **官网跨端与弱网实测**：在 Safari / iOS 真机、真实 WebGPU 120Hz 设备及弱网环境下补验触摸缩放、Shader 恢复与生产 CDN `.wasm` 的 Brotli/缓存响应头；下线旧 Cloudflare Pages 项目。
