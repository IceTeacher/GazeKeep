# 发布门禁

项目采用 [MIT 许可](../LICENSE)。当前具备 Debug 与 R8/资源压缩 Release 构建入口；正式签名、当前版本完整实机验收和正式分发仍受[待办](../tasks/todo.md)约束。

## 第三方与安装包

APK 内 assets/THIRD_PARTY_NOTICES.txt 记录当前 Release 解析的外部运行依赖、版本、许可链接，以及 Apache、MIT、BSD 必要文本和声明。依据实际 android-classes-jar artifact view，排除未打包的编译存根与 Xposed API；依赖变化后重新核对。Google SDK 与 ML Kit 条款独立适用，不能将项目 MIT 许可推广至它们。

最终 APK 必须检查权限、Xposed assets/META-INF、反射入口、Shizuku Provider、AIDL 和 Binder 字段顺序。现代 API 102 和 legacy 同 APK 登记；现代最低/目标版本为 102，API 100 不支持，legacy 仅用于选择旧登记的框架。更新后重启生效，关闭热重载；实际入口与钩点按设备记录，不从徽标猜测兼容性。

## Release 签名

Release 启用 R8 与资源压缩。签名从 GAZEKEEP_STORE_FILE、GAZEKEEP_STORE_PASSWORD、GAZEKEEP_KEY_ALIAS、GAZEKEEP_KEY_PASSWORD 四个环境变量读取；配置必须完整。全部未配置时产生 unsigned APK，不视为可分发版本。密码、密钥与本地签名文件不提交。

本地压缩运行验证可以使用明确标注的开发签名副本；不能将开发签名写成正式发布签名，也不能因签名不兼容卸载用户应用或清数据。

## 发布验收

- 核对七条不变量、完整实机矩阵和最新 APK；按 Android、ROM 与三后端能力记录实测范围。
- 对 Debug 与压缩 Release 执行 `pnpm verify:apk --apk <APK路径>`：检查双登记与作用域、版本与中文简介、图标引用和位图、无参入口保留、禁止权限与 compileOnly 类未打包；另核对运行依赖。
- 记录代码/APK 标识、设备与 ROM、后端/框架版本、系统超时、实际命中候选和未执行项，不从主机测试推断硬件通过。
- 每个 Release 附实测范围、未测声明、隐私三句话、Root/非 Root 启用步骤、已知限制及需要重启的入口更新。

未授权发布时交付本地可审查结果，不创建下载链接或上传安装包。

## API 102 版本验收条件

1.1.0（versionCode=2）为双入口功能更新，现代路径以 API >=102、系统进程钩子能力、系统作用域启用与重启为前提。设备记录包含代码/APK 标识、实际框架名称/版本/API、入口日志、命中候选与桥接状态；当前实际结果与未执行项以[任务清单](../tasks/todo.md)为准。

验收须分开记录管理器识别、现代或 legacy 入口加载、桥接就绪和真实注视/熄屏结果。API 100 框架不能作为现代成功证据；没有确实选择旧登记的环境则 legacy 验收记未执行。升级从原版本覆盖安装，确认用户配置保留、中文简介和新图标显示；不自动清缓存或卸载应用。

用户确认 1.1.0（2）在 Android 15 模拟器（LSPosed v2.2.1 / 7912）及 Xiaomi 12 Pro 上，模块显示/API 102 识别、重启后桥接、注视保亮与转头/看不清后熄屏、隐私指示器功能和覆盖升级/配置保留均人工通过。具体测试包标识、结果来源与环境缺项见[任务清单](../tasks/todo.md#用户人工验收2026-10-13)。真机 ROM/框架版本尚待补齐，模拟器手工测试包与本地 Debug 的 SHA-256 不同；不将人工结果直接推广到本地 unsigned Release、其他 ROM 或完整异常矩阵。
