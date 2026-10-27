# GazeKeep 注视保亮

[English](#english)

GazeKeep 为没有系统级「注视不熄屏」功能的 Android 手机补上这一能力：临近系统降亮或熄屏时，短暂调用前置摄像头采样 1～3 帧，只有确认你在注视屏幕时才延后一次超时；离开、看不清或检测失败时，系统照常熄屏。

源码、完整说明与各架构安装包见 [GitHub](https://github.com/IceTeacher/GazeKeep)，官网见 [gazekeep.rainafter.cn](https://gazekeep.rainafter.cn)。

## 隐私

1. 只在快要熄屏时打开前置摄像头，看完立刻关闭。
2. 画面只在内存里判断是否注视了屏幕，不保存、不上传。
3. 看不清、没看清、旁边有别人，都会让屏幕正常熄灭。

应用不申请网络权限，不接入统计、崩溃上报或广告 SDK。

## 特性

- 绝不盲目常亮：暗光、侧脸、多人或相机被占用时都不保亮；内置单次最长保亮（默认 30 分钟，可自定义或设为无限制）。
- 三种后端：LSPosed（系统框架级）、Shizuku（免 Root）、无障碍服务，可自动选择或手动锁定。本模块仓库提供的是同一个 APK，免 Root 用户也可直接安装使用。
- 应用白名单默认开启，只对勾选的应用生效；内置相机自检向导与本地枚举诊断日志。

## LSPosed 使用说明

1. 在 LSPosed 中启用模块，作用域选择「系统框架」，重启系统。
2. 打开 GazeKeep，确认 LSPosed 后端显示已就绪，授予相机权限并完成自检。
3. 选择白名单应用后打开总开关。小米/HyperOS 需在设置页「后台运行」把省电策略设为「无限制」。

- 现代入口要求 libxposed API 102 及以上；不支持 API 100 的框架。旧版框架使用传统入口（xposedminversion 93）。
- 模块更新后需要重启系统才生效，不支持热重载。
- Android 8.0（API 26）是可安装下限，不代表所有系统与 ROM 均已验收；各版本的实测环境见对应 Release 说明。

## English

GazeKeep brings "keep the screen on while you look at it" to Android phones that lack it. Shortly before the screen dims or times out, it briefly samples 1–3 frames from the front camera and extends the timeout once only when it is confident you are looking at the screen. When you look away, the result is unclear, or detection fails, the screen sleeps as usual.

Source code, full documentation and per-ABI APKs are on [GitHub](https://github.com/IceTeacher/GazeKeep).

### Privacy

1. The front camera opens only near screen timeout and closes immediately after checking.
2. Frames are processed in memory to check whether you are looking at the screen. Nothing is saved or uploaded.
3. Unclear results, failed detection or another person in view let the screen sleep normally.

The app requests no network permission and contains no analytics, crash reporting or ad SDKs.

### Using with LSPosed

1. Enable the module in LSPosed with the System Framework scope, then reboot.
2. Open GazeKeep, confirm the LSPosed backend is ready, grant camera permission and run the self-check.
3. Pick the apps to allow, then turn on the main switch. On Xiaomi/HyperOS, set the battery saver policy to "No restrictions" from the "Background activity" entry in Settings.

- The modern entry requires libxposed API 102 or later; API 100 frameworks are not supported. Older frameworks use the legacy entry (xposedminversion 93).
- Reboot after updating the module; hot reload is not supported.
- Shizuku and Accessibility backends work without root using the same APK.
