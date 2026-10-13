# 发布门禁

项目采用 [MIT 许可](../LICENSE)。当前具备 Debug 与 R8/资源压缩 Release 构建入口；正式签名、当前版本完整实机验收和正式分发仍受[待办](../tasks/todo.md)约束。

## 第三方与安装包

APK 内 assets/THIRD_PARTY_NOTICES.txt 记录当前 Release 解析的外部运行依赖、版本、许可链接，以及 Apache、MIT、BSD 必要文本和声明。依据实际 android-classes-jar artifact view，排除未打包的编译存根与 Xposed API；依赖变化后重新核对。Google SDK 与 ML Kit 条款独立适用，不能将项目 MIT 许可推广至它们。

最终 APK 必须检查权限、Xposed assets/META-INF、反射入口、Shizuku Provider、AIDL 和 Binder 字段顺序。现代 API 102 入口代码保留，但当前 APK 未登记现代入口；经典框架使用 LegacyEntry；实际入口与钩点按设备记录，不猜测兼容性。

## Release 签名

Release 启用 R8 与资源压缩。签名从 GAZEKEEP_STORE_FILE、GAZEKEEP_STORE_PASSWORD、GAZEKEEP_KEY_ALIAS、GAZEKEEP_KEY_PASSWORD 四个环境变量读取；配置必须完整。全部未配置时产生 unsigned APK，不视为可分发版本。密码、密钥与本地签名文件不提交。

本地压缩运行验证可以使用明确标注的开发签名副本；不能将开发签名写成正式发布签名，也不能因签名不兼容卸载用户应用或清数据。

## 发布验收

- 核对七条不变量、完整实机矩阵和最新 APK；按 Android、ROM 与三后端能力记录实测范围。
- 从最终 DEX 确认 hidden-api 存根未打包，反射入口名称未被压缩破坏；核对禁止权限及运行依赖。
- 记录代码/APK 标识、设备与 ROM、后端/框架版本、系统超时、实际命中候选和未执行项，不从主机测试推断硬件通过。
- 每个 Release 附实测范围、未测声明、隐私三句话、Root/非 Root 启用步骤、已知限制及需要重启的入口更新。

未授权发布时交付本地可审查结果，不创建下载链接或上传安装包。
