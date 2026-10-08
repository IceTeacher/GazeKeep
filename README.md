# GazeKeep 注视保亮

超时前用前置摄像头短暂判断你是否仍在看屏幕。只有可信注视才延后一次超时；离开或没看清就放行系统熄屏。

为缺少系统级注视保亮能力的 Android 手机设计，提供 LSPosed、Shizuku、无障碍三种后端。推理在端上，画面不保存、不上传。

## 隐私

1. 只在快要熄屏时打开前置摄像头，看完立刻关闭。
2. 画面只在内存里判断「在不在看」，不保存、不上传。
3. 看不清、没看清、旁边有别人，都会让屏幕正常熄灭。

不申请网络、存储/相册或悬浮窗权限，不读取无障碍窗口内容。完整承诺见[隐私与安全](docs/privacy-security.md)。

## 当前状态与兼容范围

检测、三后端、miuix 设置页、DataStore 配置和分步自检已落地。Android 8（API26）是安装下限，不代表所有系统已验证。

最新结构重构后的完整实机回归仍待完成。无障碍保亮上限释放、Shizuku 恢复值落盘顺序、独立前台来源、部分传感器、退避、完整日志和 Release R8 尚有缺口，详见[任务清单](tasks/todo.md)。兼容性需按目标 Android、ROM 与后端能力实测。

## 安装与启用

当前可从源码构建 Debug APK，尚未提供经完整验收的 Release 下载和项目许可协议。

1. 安装 APK，授予相机权限。
2. 完成自检：正视识别「在看」，转头识别「没在看」；未知结果不算通过。
3. 保持默认白名单开启，选择需要生效的应用。
4. 配好后端，确认就绪，再打开总开关；允许通知以接收异常提醒。

| 后端    | 准备                                                           |
| ------- | -------------------------------------------------------------- |
| LSPosed | 已有框架的用户启用模块，作用域选择系统框架并重启               |
| Shizuku | 启动 Shizuku 并授权本应用，重启后的启动条件按 Shizuku 配置处理 |
| 无障碍  | 在系统设置启用 GazeKeep 服务，不需要单独申请悬浮窗权限         |

Auto 优先 LSPosed→Shizuku→无障碍；锁定后端失效时不改选其他后端。当前应用白名单来源仍依赖无障碍事件，详见[使用说明](docs/usage.md)。

无法开启时先检查相机权限、自检、白名单和后端状态。相机被占用或没看清时应让系统正常熄屏；异常查看通知和就绪原因。成功保亮不弹提示。

## 开发

JDK21、Android SDK37，编译目标 JVM11。Android 工程在 apps/app，官网预留在 apps/web；Node 仓库工具在 scripts/repo-tools。根级 pnpm/Turbo 提供统一命令，版本以版本目录、wrapper 和锁文件为准。

```bash
pnpm --filter @gazekeep/app exec ./gradlew :android:assembleDebug
pnpm --filter @gazekeep/app exec ./gradlew :core:allTests
pnpm --filter @gazekeep/app exec ./gradlew :android:lintDebug
```

APK 位于 apps/app/android/build/outputs/apk/debug/。开发工具与 Hook 的准备见[开发规范](docs/development.md)。

```text
android → shared / engine / 三种后端 → core
backend:lsposed → bridge:protocol ← xposed
```

开发入口为根 [AGENTS.md](AGENTS.md)、[文档索引](docs/index.md)、[任务清单](tasks/todo.md)。测试与已测范围见[测试规范](docs/testing.md)。项目许可和第三方依赖说明需在首次可发布版本前补齐。
