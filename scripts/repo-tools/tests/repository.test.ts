import assert from "node:assert/strict";
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";
import test, { type TestContext } from "node:test";

const ROOT = fileURLToPath(new URL("../../../", import.meta.url));
function fixture(t: TestContext) {
  const root = mkdtempSync(join(tmpdir(), "gazekeep 仓库检查 "));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  function write(name: string, contents: string) {
    const path = join(root, name);
    mkdirSync(join(path, ".."), { recursive: true });
    writeFileSync(path, contents);
    return path;
  }
  function check(script: string) {
    return spawnSync(process.execPath, [join(ROOT, "scripts/repo-tools/scripts", script), "--root", root], {
      encoding: "utf8",
    });
  }
  function docs() {
    write(
      ".agents/docs-index.md",
      "# 索引\n\n| 文档 | 类型 | 加载时机 | 维护状态 | 最后复核 |\n| --- | --- | --- | --- | --- |\n| [development.md](../docs/development.md) | 规范 | 开发时 | 持续维护 | 2026-10-08 |\n| `../docs/index.md` | 导航 | 查阅时 | 持续维护 | 2026-10-08 |\n",
    );
    write("docs/index.md", "# 文档\n\n[开发](development.md)\n");
    write("docs/development.md", "# 开发\n");
  }
  return { root, write, check, docs };
}

test("文档登记、失效链接与未登记文档", (t) => {
  const f = fixture(t);
  f.docs();
  assert.equal(f.check("check-docs.ts").status, 0);
  f.write("docs/development.md", "# 开发\n[缺失](missing.md)\n");
  assert.match(f.check("check-docs.ts").stderr, /missing.md/);
  f.write("docs/development.md", "# 开发\n");
  f.write("docs/unregistered.md", "# 未登记\n");
  assert.match(f.check("check-docs.ts").stderr, /未登记/);
});

test("计划日期一致", (t) => {
  const f = fixture(t);
  f.docs();
  assert.equal(f.check("check-docs.ts").status, 0);
  f.write("docs/plans/20261008-sample.md", "# 计划\n\n最后维护：2026-10-07\n");
  const index = join(f.root, ".agents/docs-index.md");
  writeFileSync(
    index,
    readFileSync(index, "utf8") +
      "| [计划](../docs/plans/20261008-sample.md) | 计划 | 修改时 | 计划文档 | 2026-10-08 |\n",
  );
  assert.match(f.check("check-docs.ts").stderr, /日期/);
  f.write("docs/plans/20261008-sample.md", "# 计划\n\n最后维护：2026-10-08\n");
  assert.equal(f.check("check-docs.ts").status, 0);
});

test("代码块不检查链接，中文重复锚点、编码路径与越界引用正确处理", (t) => {
  const f = fixture(t);
  f.docs();
  f.write(
    "docs/development.md",
    "# 开发\n\n```md\n[示例](absent.md)\n```\n\n## 章节\n## 章节\n[本页](#开发) [重复](#章节-1)\n[编码路径](%E4%B8%AD%E6%96%87%20%E7%A9%BA%E6%A0%BC.json)\n",
  );
  f.write("docs/中文 空格.json", "{}\n");
  assert.equal(f.check("check-docs.ts").status, 0);
  f.write("docs/development.md", "# 开发\n[不存在](#不存在)\n[越界](../../outside.md)\n");
  const result = f.check("check-docs.ts");
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /锚点/);
  assert.match(result.stderr, /越出仓库/);
});

test("复核日期必须是真实日历日期", (t) => {
  const f = fixture(t);
  f.docs();
  const index = join(f.root, ".agents/docs-index.md");
  writeFileSync(index, readFileSync(index, "utf8").replace("2026-10-08", "2026-02-30"));
  const result = f.check("check-docs.ts");
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /日期非法/);
});

test("检查移动后的模块规则和重复 skill name", (t) => {
  const f = fixture(t);
  f.write("apps/app/settings.gradle.kts", 'include(":core")\n');
  f.write("AGENTS.md", "# 根规则\n");
  let result = f.check("check-agent-assets.ts");
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /core\/AGENTS.md/);
  for (const name of ["apps/app", "apps/web", "scripts/repo-tools", "apps/app/core"])
    f.write(`${name}/AGENTS.md`, "# 模块规则\n");
  for (const name of ["first", "second"])
    f.write(`.agents/skills/${name}/SKILL.md`, "---\nname: first\ndescription: 有效的技能描述\n---\n\n# 技能\n");
  assert.match(f.check("check-agent-assets.ts").stderr, /重复/);
});

test("检查 Claude Code Skill 适配层与真源同步", (t) => {
  const f = fixture(t);
  f.write("apps/app/settings.gradle.kts", 'include(":core")\n');
  f.write("AGENTS.md", "# 根规则\n");
  for (const name of ["apps/app", "apps/web", "scripts/repo-tools", "apps/app/core"])
    f.write(`${name}/AGENTS.md`, "# 模块规则\n");
  const skill = "---\nname: sample\ndescription: 有效的技能描述\n---\n\n# 技能\n";
  f.write(".agents/skills/sample/SKILL.md", skill);
  f.write(".claude/skills/sample/SKILL.md", skill);
  assert.equal(f.check("check-agent-assets.ts").status, 0);
  f.write(".claude/skills/sample/SKILL.md", `${skill}\n漂移\n`);
  assert.match(f.check("check-agent-assets.ts").stderr, /适配层与真源不一致/);
  f.write(".claude/skills/extra/SKILL.md", skill.replace("sample", "extra"));
  assert.match(f.check("check-agent-assets.ts").stderr, /未登记/);
});

test("架构检查阻断模块变动、非法依赖与源码目录错误", (t) => {
  const f = fixture(t);
  const modules = [
    "android",
    "e2e",
    "shared",
    "core",
    "engine",
    "backend:a11y",
    "backend:shizuku",
    "backend:lsposed",
    "xposed",
    "bridge:protocol",
    "hidden-api-stubs",
  ];
  f.write("apps/app/settings.gradle.kts", modules.map((m) => `include(":${m}")`).join("\n"));
  for (const m of modules) f.write(`apps/app/${m.replaceAll(":", "/")}/build.gradle.kts`, "");
  assert.equal(f.check("check-architecture.ts").status, 0);
  f.write("apps/app/xposed/build.gradle.kts", 'compileOnly(project(":hidden-api-stubs"))');
  assert.equal(f.check("check-architecture.ts").status, 0);
  f.write("apps/app/xposed/build.gradle.kts", 'implementation(project(":hidden-api-stubs"))');
  assert.match(f.check("check-architecture.ts").stderr, /存根只能 compileOnly/);
  f.write("apps/app/xposed/build.gradle.kts", "");
  f.write("apps/app/core/build.gradle.kts", 'implementation(project(":engine"))');
  f.write("apps/app/core/src/commonMain/kotlin/wrong/Rule.kt", "package correct\nimport android.os.PowerManager\n");
  const result = f.check("check-architecture.ts");
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /不应依赖/);
  assert.match(result.stderr, /目录与包名/);
  assert.match(result.stderr, /越界导入/);
  f.write(
    "apps/app/settings.gradle.kts",
    modules.map((m) => `include(":${m}")`).join("\n") + '\ninclude(":unexpected")\n',
  );
  assert.match(f.check("check-architecture.ts").stderr, /模块与边界规则不一致/);
});

test("分类索引识别反引号路径并拒绝缺失、重复、越界和非法日期", (t) => {
  const f = fixture(t);
  f.docs();
  const index = join(f.root, ".agents/docs-index.md");
  const base = readFileSync(index, "utf8").replace(
    "[development.md](../docs/development.md)",
    "`../docs/development.md`",
  );
  writeFileSync(index, base.replace("# 索引", "# 索引\n\n## 顶层规范"));
  assert.equal(f.check("check-docs.ts").status, 0);
  for (const [path, date, expected] of [
    ["missing.md", "2026-10-08", /不存在/],
    ["../docs/development.md", "2026-10-08", /重复登记/],
    ["../../outside.md", "2026-10-08", /越出仓库/],
    ["../docs/development.md", "2026-02-30", /日期非法/],
  ] as [string, string, RegExp][]) {
    writeFileSync(index, base + `| \`${path}\` | 规范 | 开发 | 持续维护 | ${date} |\n`);
    const result = f.check("check-docs.ts");
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, expected);
  }
  writeFileSync(index, base);
  f.write("docs/archive/history.md", "# 未登记\n[失效](gone.md)\n");
  const result = f.check("check-docs.ts");
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /未登记/);
  assert.match(result.stderr, /不存在/);
});

test("公开导航、Agent 资料与任务计划均受登记和链接检查", (t) => {
  const f = fixture(t);
  f.docs();
  const index = join(f.root, ".agents/docs-index.md");
  const base = readFileSync(index, "utf8");
  for (const name of [".agents/workflow.md", "docs/plans/todo.md", "docs/plans/20261008-sample.md"]) {
    const contents = name.endsWith("-sample.md") ? "# 计划\n\n最后维护：2026-10-08\n" : "# 资料\n";
    f.write(name, contents);
    assert.match(f.check("check-docs.ts").stderr, /未登记/);
    writeFileSync(index, readFileSync(index, "utf8") + `| \`../${name}\` | 资料 | 修改时 | 持续维护 | 2026-10-08 |\n`);
    assert.equal(f.check("check-docs.ts").status, 0);
  }
  f.write("docs/index.md", "# 文档\n[失效](missing.md)\n");
  assert.match(f.check("check-docs.ts").stderr, /不存在/);
  f.write("docs/index.md", "# 文档\n");
  writeFileSync(index, base.replace(/^.*`\.\.\/docs\/index.md`.*\n/m, ""));
  assert.match(f.check("check-docs.ts").stderr, /未登记：docs\/index.md/);
  rmSync(index);
  assert.match(f.check("check-docs.ts").stderr, /缺少 \.agents\/docs-index.md/);
});

test("活动文档变更必须同步 Agent 登记，修改读者导航不能代替登记", (t) => {
  const f = fixture(t);
  f.docs();
  const documents = [
    "docs/development.md",
    ".agents/workflow.md",
    "docs/plans/todo.md",
    "docs/plans/20261008-sample.md",
  ];
  const index = join(f.root, ".agents/docs-index.md");
  for (const name of documents.slice(1)) {
    f.write(name, name.endsWith("-sample.md") ? "# 计划\n\n最后维护：2026-10-08\n" : "# 资料\n");
    writeFileSync(index, readFileSync(index, "utf8") + `| \`../${name}\` | 资料 | 修改时 | 持续维护 | 2026-10-08 |\n`);
  }
  function git(...args: string[]) {
    const result = spawnSync(
      "git",
      [
        "-C",
        f.root,
        "-c",
        "core.hooksPath=/dev/null",
        "-c",
        "commit.gpgsign=false",
        "-c",
        "user.name=Fixture",
        "-c",
        "user.email=fixture@example.invalid",
        ...args,
      ],
      { encoding: "utf8" },
    );
    assert.equal(result.status, 0, result.stderr);
  }
  git("init", "--quiet");
  git("add", ".");
  git("commit", "--quiet", "-m", "fixture");
  assert.equal(f.check("check-docs.ts").status, 0);
  const registration = readFileSync(index, "utf8");
  const navigation = readFileSync(join(f.root, "docs/index.md"), "utf8");
  for (const name of documents) {
    const original = readFileSync(join(f.root, name), "utf8");
    f.write(name, original + "\n新增说明。\n");
    assert.match(f.check("check-docs.ts").stderr, /未同步 \.agents\/docs-index.md/);
    f.write("docs/index.md", navigation + "\n导航说明。\n");
    assert.match(f.check("check-docs.ts").stderr, /未同步 \.agents\/docs-index.md/);
    writeFileSync(index, registration.replace("开发时", "开发与贡献时"));
    assert.equal(f.check("check-docs.ts").status, 0);
    f.write(name, original);
    f.write("docs/index.md", navigation);
    writeFileSync(index, registration);
  }
});

test("发布说明只含更新日志与启用步骤两节，文件名为 tag", (t) => {
  const f = fixture(t);
  f.docs();
  const index = join(f.root, ".agents/docs-index.md");
  const register = (name: string) =>
    writeFileSync(
      index,
      readFileSync(index, "utf8") + `| \`../${name}\` | 发布说明 | 发布时 | 已发布后冻结 | 2026-10-08 |\n`,
    );
  const valid = "## 更新日志\n\n### 修复\n\n- 修复问题。\n\n## 启用步骤\n\n- 打开总开关。\n";
  f.write("docs/releases/v1.2.0.md", valid);
  register("docs/releases/v1.2.0.md");
  assert.equal(f.check("check-docs.ts").status, 0, f.check("check-docs.ts").stderr);
  for (const notes of [
    `# GazeKeep 1.2.0 预发布说明\n\n${valid}`,
    `${valid}\n## 安装包\n\n由 CI 生成。\n`,
    `${valid}\n## 实测范围\n\n- 某设备。\n`,
    "## 启用步骤\n\n- 打开总开关。\n\n## 更新日志\n\n- 修复问题。\n",
    "## 更新日志\n\n- 修复问题。\n",
  ]) {
    f.write("docs/releases/v1.2.0.md", notes);
    assert.match(f.check("check-docs.ts").stderr, /v1\.2\.0\.md.*更新日志.*启用步骤/, notes);
  }
  f.write("docs/releases/v1.2.0.md", valid);
  f.write("docs/releases/GazeKeep-1.2.1.md", valid);
  register("docs/releases/GazeKeep-1.2.1.md");
  assert.match(f.check("check-docs.ts").stderr, /GazeKeep-1\.2\.1\.md.*v<版本>/);
});
