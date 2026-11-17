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
  function check(script: string, ...args: string[]) {
    return spawnSync(process.execPath, [join(ROOT, "scripts/repo-tools/scripts", script), "--root", root, ...args], {
      encoding: "utf8",
    });
  }
  function docs() {
    write("docs/index.md", "# 文档\n\n[开发](development.md)\n");
    write("docs/development.md", "# 开发\n");
  }
  return { root, write, check, docs };
}

test("文档导航、失效链接与缺失导航检查", (t) => {
  const f = fixture(t);
  f.docs();
  assert.equal(f.check("check-docs.ts").status, 0);
  f.write("docs/development.md", "# 开发\n[缺失](missing.md)\n");
  assert.match(f.check("check-docs.ts").stderr, /missing.md/);
  f.write("docs/development.md", "# 开发\n");
  rmSync(join(f.root, "docs/index.md"));
  assert.match(f.check("check-docs.ts").stderr, /缺少 docs\/index\.md/);
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

test("检查四个核心工程 AGENTS.md 与重复 skill name", (t) => {
  const f = fixture(t);
  f.write("AGENTS.md", "# 根规则\n");
  const result = f.check("check-agent-assets.ts");
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /apps\/app\/AGENTS\.md/);
  for (const name of ["apps/app", "apps/web", "scripts/repo-tools"]) f.write(`${name}/AGENTS.md`, "# 工程规则\n");
  for (const name of ["first", "second"])
    f.write(`.agents/skills/${name}/SKILL.md`, "---\nname: first\ndescription: 有效的技能描述\n---\n\n# 技能\n");
  assert.match(f.check("check-agent-assets.ts").stderr, /重复/);
});

test("检查 Claude Code Skill 适配层与真源同步", (t) => {
  const f = fixture(t);
  f.write("AGENTS.md", "# 根规则\n");
  for (const name of ["apps/app", "apps/web", "scripts/repo-tools"]) f.write(`${name}/AGENTS.md`, "# 工程规则\n");
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

test("双语聚合 CHANGELOG 校验版本配对、章节结构与提取指定版本", (t) => {
  const f = fixture(t);
  f.docs();
  const validZh = "# v1.2.0\n\n## 更新日志\n\n### 修复\n\n- 修复问题。\n\n## 启用步骤\n\n- 打开总开关。\n";
  const validEn =
    "# v1.2.0\n\n## Changelog\n\n### Fixed\n\n- Fix an issue.\n\n## Setup\n\n- Turn on the main toggle.\n";
  f.write("docs/CHANGELOG.md", validZh);
  // 缺少配对英文 CHANGELOG 时报错。
  assert.match(f.check("check-docs.ts").stderr, /CHANGELOG\.en\.md/);
  f.write("docs/CHANGELOG.en.md", validEn);
  assert.equal(f.check("check-docs.ts").status, 0, f.check("check-docs.ts").stderr);

  // 测试 --extract-release 提取章节
  const out = join(f.root, "extracted.md");
  const extractOk = f.check("check-docs.ts", "--extract-release", "v1.2.0", "--output", out);
  assert.equal(extractOk.status, 0, extractOk.stderr);
  assert.match(readFileSync(out, "utf8"), /^## 更新日志\n/);
  assert.doesNotMatch(readFileSync(out, "utf8"), /^# v1\.2\.0/m);

  const extractMissing = f.check("check-docs.ts", "--extract-release", "v9.9.9");
  assert.notEqual(extractMissing.status, 0);
  assert.match(extractMissing.stderr, /v9\.9\.9/);

  for (const badZh of [
    `${validZh}\n## 安装包\n\n由 CI 生成。\n`,
    "# v1.2.0\n\n## 启用步骤\n\n- 打开总开关。\n\n## 更新日志\n\n- 修复问题。\n",
    "# v1.2.0\n\n## 更新日志\n\n- 修复问题。\n",
    "# GazeKeep 1.2.0\n\n## 更新日志\n\n- 修复。\n\n## 启用步骤\n\n- 开启。\n",
  ]) {
    f.write("docs/CHANGELOG.md", badZh);
    assert.notEqual(f.check("check-docs.ts").status, 0, badZh);
  }
  f.write("docs/CHANGELOG.md", validZh);

  for (const badEn of [
    "# v1.2.0\n\n## Setup\n\n- Turn on.\n\n## Changelog\n\n- Fixed.\n",
    "# v1.2.0\n\n## Changelog\n\n- Fixed.\n",
    "# v1.1.9\n\n## Changelog\n\n- Fixed.\n\n## Setup\n\n- Turn on.\n",
  ]) {
    f.write("docs/CHANGELOG.en.md", badEn);
    assert.notEqual(f.check("check-docs.ts").status, 0, badEn);
  }
});
