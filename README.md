# GazeKeep 注视保亮

GazeKeep 为没有系统级“注视不熄屏”能力的 Android 手机，提供接近原生系统级的注视保亮体验：系统即将按超时熄屏时，使用前置摄像头短暂判断用户是否仍在看屏幕；在看就重置一次超时，没在看或没看清就让系统正常熄屏。

项目面向不同厂商和 Android 版本设计，不绑定某一款手机。小米 12 Pro（HyperOS、Android 13/14）是首要验证基线，用来收敛相机、时序、功耗和特权后端的首批适配；其他设备需要通过兼容性实测确认。

## 主要特性

- **默认熄屏**：离开、暗光、侧脸、多张脸、相机失败或任意链路中断，均按系统原超时熄屏。
- **本地处理**：检测只取前置摄像头 1～3 帧，帧不落盘、不上传、不进入日志。
- **多种后端**：按设备能力支持 LSPosed、Shizuku 和无障碍服务，并在运行时探测可用后端。
- **跨应用工作**：在用户选定的前台应用中生效；相机、通话、导航等场景会主动让出。
- **安全上限**：单次连续保亮到达上限后，下一轮强制让系统熄屏，避免误判导致长时间亮屏。

## 当前进度

仓库目前处于 **M0**：先完成前置摄像头三帧采集和“在看 / 没在看 / 这次没看清”调试页。注视阈值尚未在首要验证机上冻结，暂不实现任何保亮后端。

目标模块结构、状态机、不变量和手工验证矩阵见：

- [产品与技术规划](./docs/architecture/GazeKeep-规划文档.md)
- [架构设计](./docs/architecture/GazeKeep-架构设计.md)

## 架构概览

```text
androidApp ─▶ shared ─▶ core
androidApp ─▶ engine ─▶ core
androidApp ─▶ backend:{a11y,shizuku,lsposed} ─▶ core
backend:lsposed ─▶ xposed(protocol)
```

`core` 保存与 Android 无关的状态机、投票、门控判定和保亮策略；`engine` 负责相机、分类、传感器和超时估算；后端只负责执行保亮动作。运行在 `system_server` 的 LSPosed 代码保持极薄，不运行模型、不打开相机。

## 构建与测试

环境版本统一在 `gradle/libs.versions.toml`，当前目标为 compileSdk/targetSdk 37、JVM target 11、minSdk 26。

```bash
# 构建 Debug APK
./gradlew :androidApp:assembleDebug

# shared host 测试
./gradlew :shared:testAndroidHostTest

# core 加入后的纯逻辑测试
./gradlew :core:allTests

# Android lint
./gradlew :androidApp:lintDebug
```

设备相关的相机、无障碍、Shizuku、LSPosed 验证需要按规划文档中的手工矩阵执行，并记录到 `docs/testing/`。

## 隐私与安全边界

GazeKeep 不申请网络、存储、相册、读取电话状态或悬浮窗权限，不读取无障碍窗口内容。日志只保留时间、包名、判定枚举、后端和结果等必要信息；调试日志默认关闭并按 24 小时滚动删除。

## 许可

项目许可协议和第三方依赖说明将在首个可发布版本前补充。
