# 发布门禁

当前已有 Debug 构建与历史设备测试，许可证、可分发 Release 和完整发布验收尚未完成，不编造下载链接或授权协议。

## 发布前

- 核对七条不变量、完整实机矩阵和最新 APK；小米 12 Pro 必测，扩展设备单独记录。
- 完成 Release R8/资源压缩配置与构建验证；当前 isMinifyEnabled=false，存在 ProGuard 文件不代表混淆已经验收。
- 从最终 APK 检查权限、Xposed assets/META-INF、反射入口、Shizuku Provider、AIDL 与 binder 字段顺序，禁止权限出现即失败。
- 检查运行依赖，不引入网络、广告或统计 SDK；签名只从未提交的本地配置/环境提供。
- 更新[已测系统](compat/tested-systems.md)，包括设备、ROM、Android、LSPosed 版本与实际命中候选；未知版本不猜。
- 补齐项目许可与第三方依赖说明，明确发布版本及安装包来源。

每个 Release 附已测范围、未测声明、隐私三句话、Root 与非 Root 启用步骤和已知限制。协议/系统入口更新需要重启的情况明确提示。用户未授权发布时，交付停在已验证且可审查的本地结果。
