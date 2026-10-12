# 使用 system_server AppOps 活跃通知拦截实现实验性隐藏相机隐私指示器

在 Android 12+ 上，GazeKeep 短时打开前置摄像头会触发状态栏右上角闪烁绿色隐私指示器。我们决定提供默认关闭、仅限 LSPosed 后端生效的实验性“隐藏相机隐私指示器”功能，并采用在 `system_server` 进程内拦截 `com.android.server.appop.AppOpsService` 相机活跃通知（`OP_CAMERA = 26`）的方案，而非注入 `com.android.systemui` 或修改全局 `device_config`。这样无需扩展模块作用域，避免了各 ROM 状态栏魔改引发的 `SystemUI` 崩溃风险，并能直接通过 `BridgeProtocol` 实时同步开关状态。

## Considered Options

- **方案一（已选）：在 `system_server` 内拦截 `AppOpsService` 活跃回调与活跃查询**
  - 仅在 `system_server`（`android` / `system` 作用域）解析 `AppOpsService` 的活跃状态通知方法（`scheduleOpActiveChangedIfNeededLocked` / `notifyOpActiveChanged`）与 `isOperationActive`；
  - 仅当 `code == 26 (OP_CAMERA)`、目标属于 `cn.rainafter.app.gazekeep`（按 UID/包名校验）且实验开关处于开启状态时，拦截 `active = true` 的通知并将 `isOperationActive` 置为 `false`；
  - 不拦截 `startOp` / `checkOperation` 的权限返回值（保持 `MODE_ALLOWED`），避免 `cameraserver` 输出黑帧；
  - 与电源超时钩子解耦，独立解析签名与熔断保护（`HookCallbackGuard`），失败仅降级绿点隐藏，不影响保亮主链路。
- **方案二（已拒绝）：Hook `com.android.systemui` 的 `PrivacyItemController`**
  - 需要将 `com.android.systemui` 加入 `scope.list`，扩大注入攻击面；各家 OEM 状态栏高度魔改易引发 `SystemUI` 崩溃及软重启，且在 `xposed` 模块禁止文件 I/O 与 DataStore 的约束下缺少实时同步开关状态的通道。
- **方案三（已拒绝）：通过 Shizuku / Shell 修改 `device_config` 或 `appops set CAMERA ignore`**
  - 关闭全局指示器会破坏整机所有应用的隐私提醒，违背不改系统全局设置的安全承诺；将 `OP_CAMERA` 设为 `ignore` 会导致相机返回黑帧使注视检测失效。

## Consequences

- 用户开启实验开关且 LSPosed 后端可用时，通过 `BridgeProtocol.TX_SET_HIDE_INDICATOR` 与 `TX_REGISTER` 将状态同步至 `PowerHookCore`，后台保亮采样与前台自检均直接隐藏状态栏绿点。
- 关闭实验开关或切换至 Shizuku / 无障碍后端时，无需重启系统即可立即恢复系统默认绿点展示。
