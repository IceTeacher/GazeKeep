# GazeKeep Android 工程

`apps/app` 是 GazeKeep 的完整 Gradle 工程，包含 Android App、KMP UI、纯 Kotlin 决策逻辑、三种后端、LSPosed 桥接和真实设备测试模块。产品目标、隐私承诺和当前缺口分别以[产品定义](../../docs/product/overview.md)、[隐私与安全](../../docs/product/privacy-security.md)和[任务清单](../../docs/plans/todo.md)为准。

## 工程结构

| 路径              | 职责                                                             |
| ----------------- | ---------------------------------------------------------------- |
| `android`         | AppGraph、Activity、服务、控制器、自检、应用列表、通知和平台实现 |
| `shared`          | KMP Compose 页面、导航、平台契约、浏览器展示入口与主机 UI 测试   |
| `core`            | 不依赖 Android 的模型、接口、reducer 和策略                      |
| `engine`          | CameraX 采集、分类、门控、配置和运行时引擎                       |
| `backend/a11y`    | 无障碍事件、Hold 窗口和空闲时钟                                  |
| `backend/shizuku` | shell UserService、Binder 协议、前台查询和超时恢复               |
| `backend/lsposed` | App 侧桥接、Binder resolver 和 API 102 就绪探测                  |
| `bridge/protocol` | LSPosed 协议常量与 AIDL                                          |
| `xposed`          | `system_server` 入口、Hook、电源调度和隐私指示器过滤             |
| `e2e`             | 独立进程的 UiAutomator 实机旅程                                  |

模块依赖方向和代码定位见[架构总览](../../docs/architecture/overview.md)。接口、AIDL、descriptor、transaction 编号、协议版本和字段顺序属于兼容边界，修改前先更新对应架构专题。

## 环境与构建

要求 JDK 21、Android SDK、Node >=22.22 和根目录锁定的 pnpm。首次初始化在仓库根执行：

```bash
pnpm install --frozen-lockfile
pnpm tools:setup
pnpm hooks:install
pnpm verify:hooks
```

从仓库根运行常用门禁：

```bash
pnpm --filter @gazekeep/app verify
pnpm --filter @gazekeep/app lint
pnpm --filter @gazekeep/app test
```

也可以进入本目录执行 Gradle wrapper：

```bash
cd apps/app
./gradlew <task>
```

Debug APK 输出在 `apps/app/android/build/outputs/apk/debug/`。本地配置、签名材料和生成物不提交；Debug 构建不代表正式签名 Release 或真实设备兼容性。

## 测试与验收

测试分层、命令入口、UI/截图、实机矩阵和七条不变量见[测试与验收](../../docs/development/testing.md)。按改动风险选择最小充分验证：

```bash
pnpm --filter @gazekeep/app verify       # 主机门禁与测试 APK 构建
pnpm --filter @gazekeep/app test:device  # 已获授权且已连接设备时运行实机套件
```

主机测试、fake、构建成功或“不 Extend”不能证明真实 ROM、Hold 释放或实际熄屏。设备安装、重启、系统设置修改和权限操作必须有本次授权；测试结束后恢复自己修改的设置，不清空用户数据或卸载用户版本。实测设备、ROM、后端/框架、APK 标识、未执行项和剩余风险记录在[任务清单](../../docs/plans/todo.md)。

## 安全边界

- Unknown、暗光、多脸、相机失败和链路中断默认放行系统熄屏。
- 帧只在 analyzer 回调内处理，不保存、不上传、不跨进程；无障碍不读取窗口内容。
- App 不申请网络、存储/相册、全量应用列表或悬浮窗权限。
- `system_server` Hook 异常必须隔离；Shizuku 临时修改系统超时必须先持久化原值并在恢复条件满足后写回。

完整产品安全承诺见[Android 工程规则](AGENTS.md#产品安全承诺)。发布签名、ABI、APK 静态门禁和分发步骤见[发布门禁](../../docs/development/release.md)。
