# 当前任务与验证

本页只维护未完成事项与本次真实验证；实现和主机测试不替代实机验收。

## 未完成事项

- [ ] 完成新版 Miuix UI（双 Tab 底栏导航、图1三态卡片与系统信息首页、图2内联卡片设置页、图3渐变关于页）的真机/模拟器交互与旋转/明暗主题验收；当前 `:shared:testAndroidHostTest` 为 `NO-SOURCE`，主机编译与单测不替代真机页面验收。
- [ ] 复现并排查实际采集中按 Home 后的相机释放延迟：本轮独立模拟器一次未在 3 秒检查阈值内释放，随后无残留；返回与撤销权限分别约 2.05 秒、0.45 秒释放。不能将无前置相机实例的无客户端结果作为生命周期成功证据。
- [ ] 完成当前 APK 的实机/UI 矩阵：长超时降亮前检测、连续注视、Unknown/多脸、真实触摸、相机抢占、口袋/朝下、后端切换/掉线与服务被杀。重点验证无障碍 Hold 上限释放后实际熄一次；分别记录 Android、ROM、系统超时、后端/框架版本和实际钩点。
- [ ] 完成 Shizuku 原系统超时恢复的实机验收：临时延长、屏灭、进程被杀、Binder 重连及 Shizuku 重启。同步持久化失败、恢复读回失败和重试已由隔离测试覆盖，真实恢复仍未验证。设备/框架重启不在本次授权内。
- [ ] 完成正式签名与可分发 APK 验收：提供正式签名配置后验证签名、安装升级与压缩包运行，补齐对应版本发布说明及第三方条款复核。MIT 与当前运行依赖声明已落地；未配置正式签名的 Release 为 unsigned，本次未发布。

## 当前 APK 验证（2026-10-11）

- 应用图标替换、模拟器界面截图采集与 README 排版验证：
  1. 将指定图片生成 `mdpi`～`xxxhdpi` 全密度 `ic_launcher.png`、`ic_launcher_round.png` 与自适应图标前景 `ic_launcher_foreground.png`，更新 `ic_launcher_background.xml` 为薄荷绿纯色背景并移除默认 Android 矢量前景；同时将关于页（`AboutScreen`）顶部占位 Logo 替换为内置 `app_icon.png` 应用图标。
  2. 启动 `Pixel_8_API_35`（Android 15 / API 35）模拟器安装 `:android:assembleDebug` 产物，实际采集主页、设置页、工作后端页与应用白名单页截图（均不含人脸），与圆角应用图标一并保存在 `docs/assets/` 并排版合入 `README.md`。
  3. 同步 `docs/privacy-security.md` 与 `README.md` 中的 `privacy_line_2` 真源文案与 `strings.xml` 逐字一致。
  4. `:android:testDebugUnitTest`、`:android:assembleDebug`、`:android:lintDebug` 通过；`:shared:testAndroidHostTest` 为 `NO-SOURCE`，未将模拟器静态页面截图等同于完整页面交互或真机实测验收。
- 门禁：`pnpm verify:docs`、`pnpm verify:agent-assets`、`pnpm verify:architecture`、`pnpm test:tools`、`pnpm format:check` 全部通过，`git diff --check` 无违规。
