<div align="center">
  <img src="docs/assets/app-icon.png" width="120" height="120" alt="GazeKeep" />

# GazeKeep 注视保亮

[![GitHub stars](https://img.shields.io/github/stars/IceTeacher/GazeKeep)](https://github.com/IceTeacher/GazeKeep)
[![GitHub release](https://img.shields.io/github/v/release/IceTeacher/GazeKeep?include_prereleases&color=14795e)](https://github.com/IceTeacher/GazeKeep/releases)
[![Website](https://img.shields.io/badge/官网-gazekeep.rainafter.cn-14795e)](https://gazekeep.rainafter.cn)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![English README](https://img.shields.io/badge/README-English-blue)](README_en.md)

[🌐 官方网站](https://gazekeep.rainafter.cn) &nbsp;|&nbsp; [📥 下载最新 APK](https://github.com/IceTeacher/GazeKeep/releases/latest) &nbsp;|&nbsp; [📖 English README](README_en.md)

</div>

你有没有遇到过这样的痛点：看长文、查资料或者照着菜谱做饭时，手上有水不方便碰手机，屏幕却动不动就变暗甚至直接熄灭？
为了不被打断，你可能去系统设置里把自动熄屏时间调到了 10 分钟甚至更长。但这又带来了新问题——如果你晚上躺在床上看手机不小心睡着了，屏幕就会这样亮一整宿，不仅费电，还可能伤屏幕。

「短超时太烦人，长超时怕睡着」，GazeKeep 就是为了解决这个矛盾而诞生的。

它的设计初衷很简单：**为了给没有提供系统级注视保亮功能的 Android 设备，补齐安全、无感且可媲美系统原生体验的注视保亮功能，让旧设备也能更省心、更好地被使用。**

有了 GazeKeep，你可以放心地将系统自动熄屏时间缩短至 15 秒或 30 秒。在你专注阅读屏幕时，它仅在快要熄屏前短暂调用前置摄像头看一眼，只要你仍在注视，就默默为你延长亮屏；一旦你移开视线、或者不小心睡着了没看着屏幕，它绝不瞎干预，让系统像往常一样准时熄灭。

## 核心特性与体验

- **按需短时采样，无感低打扰**：平时绝不常驻占用摄像头，仅在临近系统降亮或熄屏前短暂采样 1 ～ 3 帧，看一眼就立刻关闭。检测与推理全程在端上离线完成；成功帮忙保亮时安静无感、没有任何弹窗，只在服务异常或后端掉线时才会发通知提醒你。
- **绝不盲目常亮**：遇到暗光、侧脸、看不清（「这次没看清」）、画面中出现多人，或摄像头正被系统相机、扫码应用占用时，它会果断放行，让系统正常熄屏。不仅如此，它还内置了单次最长保亮保护（默认 30 分钟，支持自定义或设为无限制），达到上限时释放保亮能力，目标是强制熄屏一次。
- **三种后端适配不同旧设备条件**：考虑到大家的设备条件和折腾程度不同，提供 **LSPosed**（系统框架级联动）、**Shizuku**（ADB / 无线调试授权，免 Root）和 **无障碍服务**（零门槛开箱即用）三种工作后端，支持自动按优先级优选或手动锁定。
- **白名单 / 黑名单生效范围与可视化自检**：默认仅对白名单应用生效，你可以只勾选需要的阅读或浏览类 App，不在全局盲目触发；也可以改为「排除黑名单应用」，只排除视频、相机、导航等无需保亮的 App。内置两步式相机自检向导，搭配克制、清晰的设置界面，支持自由调节检测提前量、灵敏度，以及按需开启本地枚举诊断日志。

## 界面预览

|                                         主页状态                                          |                                        参数设置                                        |
| :---------------------------------------------------------------------------------------: | :------------------------------------------------------------------------------------: |
|  <img src="docs/assets/screenshot-home.png" width="320" alt="主页运行状态与环境概览" />   | <img src="docs/assets/screenshot-settings.png" width="320" alt="检测与保护参数设置" /> |
|                                       **工作后端**                                        |                                      **应用列表**                                      |
| <img src="docs/assets/screenshot-backend.png" width="320" alt="三种工作后端与自动选择" /> | <img src="docs/assets/screenshot-apps.png" width="320" alt="白名单与黑名单应用选择" /> |

## 隐私与安全

1. 只在快要熄屏时打开前置摄像头，看完立刻关闭。
2. 画面只在内存里判断是否注视了屏幕，不保存、不上传。
3. 看不清、没看清、旁边有别人，都会让屏幕正常熄灭。

本应用不申请网络（`INTERNET`）、存储/相册、读取应用列表（`QUERY_ALL_PACKAGES`）或悬浮窗权限，不接入任何统计、广告或崩溃上报 SDK，无障碍服务不读取任何窗口文本内容。相机预览仅出现在用户主动进入的自检向导页，离开页面即刻释放。完整边界见[隐私与安全](docs/product/privacy-security.md)。

## 下载与安装

前往 [GitHub Releases](https://github.com/IceTeacher/GazeKeep/releases/latest) 下载最新发布的正式签名安装包：

| 安装包架构              | 适用设备说明                       | 包体特点                                       |
| :---------------------- | :--------------------------------- | :--------------------------------------------- |
| **`arm64-v8a`**（推荐） | 绝大多数现代主流 Android 手机/平板 | 针对 64 位架构深度优化，单包体积仅约 10~11 MB  |
| **`armeabi-v7a`**       | 早期老旧 32 位 Android 设备        | 兼顾老旧机型兼容性，体积约 10 MB               |
| **`x86_64`**            | PC Android 模拟器                  | 适用于模拟器体验与开发调试                     |
| **`universal`**         | 全架构通用安装包                   | 内置所有原生架构库，约 17 MB，不确定架构时首选 |

## 工作模式与启用

### 1. 四步启用流程

1. **下载安装并授权**：前往 [GitHub Releases](https://github.com/IceTeacher/GazeKeep/releases/latest) 下载并安装对应架构的 APK，授予前置相机权限（Android 13+ 建议允许通知权限以接收异常提醒）。
2. **两步相机自检**：在应用内进入「相机自检」，正视屏幕通过第一步（识别为“注视了屏幕”），侧头或移开视线通过第二步（识别为“未注视屏幕”）。
3. **配置生效范围**：保持默认的「仅白名单应用」并勾选需要注视保亮的阅读、浏览或烹饪类 App；或改为「排除黑名单应用」，勾选不需要保亮的 App。
4. **确认后端并开启**：确认任一后端就绪，在首页开启保亮总开关即可生效。

> 小米/HyperOS 需在设置页「后台运行」将省电策略设为「无限制」，否则使用系统相机后保亮可能失效；修改策略后需重新打开一次应用。

### 2. 三种后端对比与准备

| 后端        | 适用设备条件              | 后端特点                                          |
| :---------- | :------------------------ | :------------------------------------------------ |
| **LSPosed** | 已 Root 且安装框架        | 系统框架级事件拦截与亮屏控制，体验最贴近原生      |
| **Shizuku** | 免 Root（ADB / 无线调试） | 免 Root 环境下基于 ADB / 无线调试授权执行系统调度 |
| **无障碍**  | 免 Root 零门槛            | 零门槛开箱即用，通过模拟无感交互维持亮屏。        |

后端默认采用 **Auto（自动）** 模式，按 `LSPosed → Shizuku → 无障碍` 顺序自动优选已就绪后端；手动锁定某一后端时，若该后端失效不会静默切换到其他后端。生效范围的前台识别按后端分别使用无障碍事件、Shizuku shell 查询或 LSPosed 系统查询，详细使用与排查见[使用说明](docs/product/usage.md)。

## 当前状态与开发入口

- **兼容范围与进度**：端上检测、三种后端、UI 界面、参数配置与两步自检均已实现。Android 8.0（API 26）为安装下限，实际表现取决于系统版本、ROM 电源管理与所选后端能力，需按目标设备实测。传感器门控已接入，正式签名 APK 以 GitHub 预发布提供，当前版本的完整实机回归仍待完成，详见[产品定义](docs/product/overview.md)与[任务清单](docs/plans/todo.md)。
- **源码构建**：当前可从源码构建 Debug APK（产物位于 `apps/app/android/build/outputs/apk/debug/`）：

```bash
pnpm --filter @gazekeep/app exec ./gradlew :android:assembleDebug
```

- **文档**：完整构建与工具链要求见[开发与贡献](docs/development/contributing.md)，测试与验收矩阵见[测试与验收](docs/development/testing.md)，全部文档导航见[文档导航](docs/index.md)。

## 开源协议

本项目采用 [MIT 许可](LICENSE) 开源（内置第三方运行依赖声明见应用“关于”页或 `THIRD_PARTY_NOTICES.txt`）。
