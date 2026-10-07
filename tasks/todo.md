# 工作与验收记录

只追加实际执行与未执行事实；详细设备证据保留在 docs/testing。本流水不替代测试记录。

## 2026-10-08 仓库规范与开发门禁重组

- 范围：文档、九模块规则、六个 skills、readme 与 Lefthook；应用行为不变。
- 实施与最终验证结果将在本轮完成后追加，当前不预写通过。
- 后续：最新重构实机回归、Hold 上限/GateSkip 释放、Shizuku 持久化顺序、独立前台来源、传感器、退避、日志、现代入口与 Release R8。详见[维护导航](../docs/maintenance/overview.md)。

### 实施与工具验收追加

- 已完成：根与九模块 AGENTS、活动专题与索引、五份 ADR、领域术语、英文维护入口、旧中文章节导航、三份原文归档、小写 readme、六个仓库 skills 和 Lefthook 门禁。
- 历史证据：测试记录与兼容表沿用稳定路径、原日期和结果；增加新版缺证说明。归档比对确认除迁移链接外正文一致，十条安全承诺保留。
- 工具准备：精确锁定 Node 工具、Ruff 与 ktlint；pnpm install --frozen-lockfile 和 tools:setup 成功。已执行 hooks:install、verify:hooks，本仓库 pre-commit / commit-msg 生效。
- pnpm test:tools：13 项通过，包含 9 项真实临时 Git Hook 场景和 4 项文档/资产检查场景。覆盖合法/非法标题、重新暂存、部分暂存恢复、恢复冲突、工具缺失、删除/重命名、中文空格路径、实际多语言工具与 XML 文本保持。
- 六个 skills 通过 skill-creator quick_validate；独立临时仓库验证单条与分组提交只读取暂存快照，未带入未暂存/未跟踪内容，未改变 HEAD 或暂存摘要。
- pnpm verify:docs、verify:agent-assets、verify:architecture 通过；本次文件的 Prettier、Ruff、JS 语法与 TOML 解析检查通过，git diff --check 通过。
- 测试先行：文档/资产检查用例先确认缺失实现失败，再实现通过；Hook 用隔离 Git fixture 验证实际提交行为。文档与纯路径迁移按无应用行为例外处理。
- 未执行：应用行为单测、UI 自动化、设备安装/系统配置、实机矩阵和 Release 验证。本轮未修改应用行为或 Android 运行依赖，未新增 UI 测试依赖；最新重构实机回归仍待完成。
- 后续产品任务保持前述待办；工具验收不替代保亮上限释放、Shizuku 恢复顺序或真实设备安全验收。

### Android 组装验证追加

- ./gradlew :androidApp:assembleDebug :androidApp:lintDebug：BUILD SUCCESSFUL，包含 bridge:protocol 的 AIDL 编译。现有 CameraX 和权限回调的弃用提示保留，未扩大本轮范围修改应用。
- 本地 HEAD 与暂存区保持原状；本轮变更留在工作树供审查，未提交或推送。

## 2026-10-08 提交与历史说明规范化

- 用户明确要求提交本次更改，并在提交后按当前规范重写历史说明；该授权覆盖“默认不改写历史”的例外。
- 实际首次提交被旧全局 Lefthook 2.1.10 阻断，HEAD 未变。随后在 lefthook.yml 显式绑定本地 2.1.17，入口检查补充本地绑定校验。
- 新增入口抢占与本地执行器缺失回归场景，旧配置复现失败，新配置验证通过；pnpm test:tools 当前 14 项全部通过，pnpm verify:hooks 通过。
- 历史范围为 main 的 7 条线性提交；无配置远端。改写前保存恢复引用，保留原始文件快照、作者、提交者、时间及父子关系，仅规范提交说明。

### 提交与历史改写完成

- 本次重组以 chore(dev-workflow) 提交，pre-commit 与 commit-msg 均实际通过；首次版本冲突修正包含在同一提交。
- 已将 main 的 7 条历史说明改为当前 type(scope): emoji 中文格式，并将正文整理为基于原差异的中文说明；未重新宣称历史验证在本轮执行。
- 恢复引用：refs/backup/commit-messages-20261008，保存本次提交后、历史改写前的完整链。
- 改写阶段逐一核对 8 个提交的 tree、作者、提交者、时间和父子关系；文件快照与原元数据保持一致，8 条说明全部通过 commitlint。
- 本完成记录补入最新提交，历史 7 个文件快照保持原状；没有推送操作，也未配置远端。
