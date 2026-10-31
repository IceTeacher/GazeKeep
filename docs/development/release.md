# 发布门禁

项目采用 [MIT 许可](../../LICENSE)。当前具备 Debug 与 R8/资源压缩 Release 构建入口，tag 由 CI 以正式签名构建并发布 GitHub 预发布；正式版另同步到 [LSPosed 模块仓库](#lsposed-模块仓库)；当前版本完整实机验收和正式版分发仍受[待办](../plans/todo.md)约束。

## 第三方与安装包

APK 内 assets/THIRD_PARTY_NOTICES.txt 记录当前 Release 解析的外部运行依赖、版本、许可链接，以及 Apache、MIT、BSD 必要文本和声明。依据实际 android-classes-jar artifact view，排除未打包的编译存根与 Xposed API；依赖变化后重新核对。Google SDK 与 ML Kit 条款独立适用，不能将项目 MIT 许可推广至它们。

最终 APK 必须检查权限、Xposed assets/META-INF、反射入口、Shizuku Provider、AIDL 和 Binder 字段顺序。ML Kit 注册器由 Manifest metadata 反射发现；库的 consumer 规则保留类，应用额外保留这些注册器的 public 无参构造器，防止 R8 full mode 移除构造器后检测组件缺失。详细日志与 dumpsys 按类名输出引擎状态、效果、门控与就绪等类型，Release 只保留这些类的名称（仍可裁剪），日志短名取二进制名末段，不依赖会被 R8 移除的内部类元数据。APK 门禁从实际 metadata 推导登记类并核对 DEX 类与无参构造器，压缩运行验收仍需设备补证。现代 API 102 和 legacy 同 APK 登记；现代最低/目标版本为 102，API 100 不支持，legacy 仅用于选择旧登记的框架。更新后重启生效，关闭热重载；实际入口与钩点按设备记录，不从徽标猜测兼容性。

## Release 签名

Release 启用 R8 与资源压缩，原生库压缩存放（`useLegacyPackaging`，安装时解压），并按 ABI 拆分为 `arm64-v8a`、`armeabi-v7a`、`x86_64` 与通用包；32 位 x86 不打包。各包共用同一 versionCode，可互相覆盖安装。签名从 GAZEKEEP_STORE_FILE、GAZEKEEP_STORE_PASSWORD、GAZEKEEP_KEY_ALIAS、GAZEKEEP_KEY_PASSWORD 四个环境变量读取；配置必须完整。全部未配置时产生 unsigned APK，不视为可分发版本。密码、密钥与本地签名文件不提交。

正式签名自 1.1.2 起启用：RSA 4096 自签名证书（`CN=GazeKeep`），证书 SHA-256 为 `bbf8c24ed81ccf9856f290598a328e6046eff3a31c934423b4f51fe47514d94f`。密钥库与密码只保存在维护者本机仓库外并由维护者离线备份，CI 副本存于仅允许 `v*.*.*` tag 部署的 `release` 环境；指纹登记为该环境变量，发布时核对。丢失密钥库将无法覆盖升级已安装版本，轮换需另行规划。此前的 1.0.0、1.1.0 与 1.1.2 候选使用本地开发签名（证书 SHA-256 `767c4d84…bfc423c0`），与正式签名不兼容，升级须卸载重装并重新配置。

本地压缩运行验证可以使用明确标注的开发签名副本；不能将开发签名写成正式发布签名，也不能因签名不兼容卸载用户应用或清数据。

## 发布验收

- 每次交付保持 versionCode 递增，避免同版本覆盖安装复用管理器图标缓存。
- 核对七条不变量、完整实机矩阵和最新 APK；按 Android、ROM 与三后端能力记录实测范围。
- 对 Debug 与压缩 Release 的每个 ABI 包执行 `pnpm verify:apk --apk <APK路径> --abi <ABI>`（通用包列出全部 ABI）：检查双登记与作用域、版本与中文简介、图标引用和位图、无参入口保留、禁止权限、compileOnly 类未打包、不可调试包不含调试故障注入、详细日志输出的引擎类型保留类名、ABI 集合与原生库压缩；另核对运行依赖。
- 记录代码/APK 标识、设备与 ROM、后端/框架版本、系统超时、实际命中候选和未执行项，不从主机测试推断硬件通过。
- 实测范围、未测声明与已知限制记录在[任务清单](../plans/todo.md)，不进入 Release 正文。

未授权发布时交付本地可审查结果，不创建下载链接或上传安装包。`v*.*.*` tag 须指向 `main` 上的提交，并先合入发布说明 `docs/releases/<tag>.md`；流水线以正式签名构建 Release，确认 `main` 上同一提交的流水线已通过，各 ABI 包通过 APK 静态门禁与证书指纹核对后创建标题为 tag（如 `v1.1.3`）的 GitHub 预发布，附各 APK 与 SHA-256 文件。设备验收未完成前不转为正式版。规则见[持续集成](contributing.md#持续集成)。

## 发布说明

发布说明 `docs/releases/v<版本>.md` 就是 GitHub Release 正文的前两节，只依次包含「## 更新日志」与「## 启用步骤」，不写一级标题；`verify:docs` 检查文件名与章节。更新日志按「新增 / 优化 / 修复」分组，面向用户描述可感知的变化，涉及签名或权限变化时在更新日志中单列说明，不把未验证能力写成已验证；启用步骤写 Root 与非 Root 环境的启用、自检与必要的 ROM 设置。「## 安装包」（架构选择说明与 APK、ABI、大小、SHA-256 表）由 `release.yml` 生成追加；签名证书与提交只写入 CI 的 Step summary。发布后如在 GitHub 上修改正文，同步修改该文件。

## LSPosed 模块仓库

LSPosed 官方模块仓库（Xposed-Modules-Repo）为每个模块单独建立仓库，GazeKeep 对应 `Xposed-Modules-Repo/cn.rainafter.app.gazekeep`，提审通过后由其 bot 创建。该仓库只放元数据，不放源码：`SUMMARY`、`SCOPE`、`SOURCE_URL` 在本仓库 `apps/app/distribution/lsposed/` 维护并随代码评审修改；`README.md` 由根 `README.md` 生成，与源码仓库一致，相对图片改为 `raw.githubusercontent.com`、相对文档链接改为 `blob` 地址并固定到同步提交，不另行维护。`pnpm verify:lsposed-repo` 检查文件集合、单行摘要、作用域与 `scope.list` 一致（`system` 对应 `android`）、源码地址，以及根 README 逐字包含中文隐私三句。仓库 Description 与 Homepage 无法通过 API 修改，由维护者在网页上设置。

`.github/workflows/lsposed-repo.yml` 在本仓库 Release 转为正式版时同步，预发布不会触发；也可以手动触发并输入 tag 补发，同样只接受正式版。工具与元数据取自本流程所在提交（手动触发为 `main`），被同步 tag 的源码另行检出，只读取版本号，因此可补发早于本流程的旧 tag。流程：校验 tag 等于该 tag 源码中的 `v<versionName>`，读取本仓库 Release 正文；下载四个 APK，重新核对 SHA-256 与签名证书指纹；生成 README 并与其余元数据一起推送到模块仓库默认分支；以 `<versionCode>-<versionName>` 为 tag、versionName 为标题创建 Release，正文与本仓库 Release 正文一致（更新日志、启用步骤与安装包），附四个 APK。tag 规则与索引 bot 一致，bot 会另行解析 APK 的 Manifest 与 Xposed 登记，校验失败时把 Release 改为草稿；通过后在索引下一次构建后出现在 modules.lsposed.org 与 LSPosed 管理器中（2026-10-27 观察到构建为每日定时运行）。重跑时若 Release 已存在（含被 bot 改成的草稿），先覆盖 APK，再更新正文并显式发布（`--draft=false`），由这次编辑触发 bot 重新校验；仅替换 APK 不会通知 bot。

凭据放在 `lsposed-repo` 环境（部署范围为 `main` 与 `v*.*.*` tag）：`LSPOSED_REPO_TOKEN` 为维护者账号的 classic PAT，只授予 `public_repo`（模块仓库属于外部组织，fine-grained token 需要组织批准）；该 token 能写维护者的全部公开仓库，泄露时立即吊销。`GAZEKEEP_SIGNING_CERT_SHA256` 与 `release` 环境登记值相同。模块仓库可见或可下载只说明分发链路可用，不代表实机验收。

## API 102 版本验收条件

1.1.0（versionCode=2）为双入口功能更新，现代路径以 API >=102、系统进程钩子能力、系统作用域启用与重启为前提。设备记录包含代码/APK 标识、实际框架名称/版本/API、入口日志、命中候选与桥接状态；当前实际结果与未执行项以[任务清单](../plans/todo.md)为准。

验收须分开记录管理器识别、现代或 legacy 入口加载、桥接就绪和真实注视/熄屏结果。API 100 框架不能作为现代成功证据；没有确实选择旧登记的环境则 legacy 验收记未执行。升级从原版本覆盖安装，确认用户配置保留、中文简介和新图标显示；不自动清缓存或卸载应用。
