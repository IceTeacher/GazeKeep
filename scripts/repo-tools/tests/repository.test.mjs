import assert from "node:assert/strict";
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";
import test from "node:test";

const ROOT = fileURLToPath(new URL("../../../", import.meta.url));
function fixture(t) {
  const root = mkdtempSync(join(tmpdir(), "gazekeep 仓库检查 "));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  function write(name, contents) {
    const path = join(root, name);
    mkdirSync(join(path, ".."), { recursive: true });
    writeFileSync(path, contents);
    return path;
  }
  function check(script) {
    return spawnSync(process.execPath, [join(ROOT, "scripts/repo-tools/scripts", script), "--root", root], {
      encoding: "utf8",
    });
  }
  function docs() {
    write(
      "docs/index.md",
      "# 索引\n\n| 文档 | 类型 | 加载时机 | 维护状态 | 最后复核 |\n| --- | --- | --- | --- | --- |\n| [development.md](development.md) | 规范 | 开发时 | 持续维护 | 2026-10-08 |\n",
    );
    write("docs/development.md", "# 开发\n");
  }
  return { root, write, check, docs };
}

test("文档登记、失效链接与未登记文档", (t) => {
  const f = fixture(t);
  f.docs();
  assert.equal(f.check("check-docs.mjs").status, 0);
  f.write("docs/development.md", "# 开发\n[缺失](missing.md)\n");
  assert.match(f.check("check-docs.mjs").stderr, /missing.md/);
  f.write("docs/development.md", "# 开发\n");
  f.write("docs/unregistered.md", "# 未登记\n");
  assert.match(f.check("check-docs.mjs").stderr, /未登记/);
});

test("计划日期一致", (t) => {
  const f = fixture(t);
  f.docs();
  assert.equal(f.check("check-docs.mjs").status, 0);
  f.write("docs/plans/20261008-sample.md", "# 计划\n\n最后维护：2026-10-07\n");
  const index = join(f.root, "docs/index.md");
  writeFileSync(
    index,
    readFileSync(index, "utf8") + "| [计划](plans/20261008-sample.md) | 计划 | 修改时 | 计划文档 | 2026-10-08 |\n",
  );
  assert.match(f.check("check-docs.mjs").stderr, /日期/);
  f.write("docs/plans/20261008-sample.md", "# 计划\n\n最后维护：2026-10-08\n");
  assert.equal(f.check("check-docs.mjs").status, 0);
});

test("代码块不检查链接，中文重复锚点、编码路径与越界引用正确处理", (t) => {
  const f = fixture(t);
  f.docs();
  f.write(
    "docs/development.md",
    "# 开发\n\n```md\n[示例](absent.md)\n```\n\n## 章节\n## 章节\n[本页](#开发) [重复](#章节-1)\n[编码路径](%E4%B8%AD%E6%96%87%20%E7%A9%BA%E6%A0%BC.json)\n",
  );
  f.write("docs/中文 空格.json", "{}\n");
  assert.equal(f.check("check-docs.mjs").status, 0);
  f.write("docs/development.md", "# 开发\n[不存在](#不存在)\n[越界](../../outside.md)\n");
  const result = f.check("check-docs.mjs");
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /锚点/);
  assert.match(result.stderr, /越出仓库/);
});

test("复核日期必须是真实日历日期", (t) => {
  const f = fixture(t);
  f.docs();
  const index = join(f.root, "docs/index.md");
  writeFileSync(index, readFileSync(index, "utf8").replace("2026-10-08", "2026-02-30"));
  const result = f.check("check-docs.mjs");
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /日期非法/);
});

test("检查移动后的模块规则和重复 skill name", (t) => {
  const f = fixture(t);
  f.write("apps/app/settings.gradle.kts", 'include(":core")\n');
  f.write("AGENTS.md", "# 根规则\n");
  let result = f.check("check-agent-assets.mjs");
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /core\/AGENTS.md/);
  for (const name of ["apps/app", "apps/web", "scripts/repo-tools", "apps/app/core"])
    f.write(`${name}/AGENTS.md`, "# 模块规则\n");
  for (const name of ["first", "second"])
    f.write(`.agents/skills/${name}/SKILL.md`, "---\nname: first\ndescription: 有效的技能描述\n---\n\n# 技能\n");
  assert.match(f.check("check-agent-assets.mjs").stderr, /重复/);
});

test("架构检查阻断模块变动、非法依赖与源码目录错误", (t) => {
  const f = fixture(t);
  const modules = [
    "android",
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
  assert.equal(f.check("check-architecture.mjs").status, 0);
  f.write("apps/app/xposed/build.gradle.kts", 'compileOnly(project(":hidden-api-stubs"))');
  assert.equal(f.check("check-architecture.mjs").status, 0);
  f.write("apps/app/xposed/build.gradle.kts", 'implementation(project(":hidden-api-stubs"))');
  assert.match(f.check("check-architecture.mjs").stderr, /存根只能 compileOnly/);
  f.write("apps/app/xposed/build.gradle.kts", "");
  f.write("apps/app/core/build.gradle.kts", 'implementation(project(":engine"))');
  f.write("apps/app/core/src/commonMain/kotlin/wrong/Rule.kt", "package correct\nimport android.os.PowerManager\n");
  const result = f.check("check-architecture.mjs");
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /不应依赖/);
  assert.match(result.stderr, /目录与包名/);
  assert.match(result.stderr, /越界导入/);
  f.write(
    "apps/app/settings.gradle.kts",
    modules.map((m) => `include(":${m}")`).join("\n") + '\ninclude(":unexpected")\n',
  );
  assert.match(f.check("check-architecture.mjs").stderr, /模块与边界规则不一致/);
});

test("分类索引识别反引号路径并拒绝缺失、重复、越界和非法日期", (t) => {
  const f = fixture(t);
  f.docs();
  const index = join(f.root, "docs/index.md");
  const base = readFileSync(index, "utf8").replace("[development.md](development.md)", "`development.md`");
  writeFileSync(index, base.replace("# 索引", "# 索引\n\n## 顶层规范"));
  assert.equal(f.check("check-docs.mjs").status, 0);
  for (const [path, date, expected] of [
    ["missing.md", "2026-10-08", /不存在/],
    ["development.md", "2026-10-08", /重复登记/],
    ["../../outside.md", "2026-10-08", /越出仓库/],
    ["development.md", "2026-02-30", /日期非法/],
  ]) {
    writeFileSync(index, base + `| \`${path}\` | 规范 | 开发 | 持续维护 | ${date} |\n`);
    const result = f.check("check-docs.mjs");
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, expected);
  }
  writeFileSync(index, base);
  f.write("docs/archive/history.md", "# 未登记\n[失效](gone.md)\n");
  const result = f.check("check-docs.mjs");
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /未登记/);
  assert.match(result.stderr, /不存在/);
});
