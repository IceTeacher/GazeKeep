import assert from "node:assert/strict";
import { cpSync, mkdirSync, mkdtempSync, readFileSync, rmSync, symlinkSync, writeFileSync, chmodSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";
import test from "node:test";
import { XMLParser } from "fast-xml-parser";

const ROOT = fileURLToPath(new URL("../../../", import.meta.url));
const LEFTHOOK = join(ROOT, "node_modules/.bin/lefthook");
const xml = new XMLParser({
  ignoreAttributes: false,
  trimValues: false,
  parseTagValue: false,
  parseAttributeValue: false,
});
function fixture(t) {
  const root = mkdtempSync(join(tmpdir(), "gazekeep-hooks 中文 "));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  const env = {
    ...process.env,
    PATH: `${join(ROOT, "node_modules/.bin")}:${process.env.PATH}`,
    LEFTHOOK_OUTPUT: "failure,summary",
  };
  delete env.LEFTHOOK_BIN;
  function write(name, content) {
    const path = join(root, name);
    mkdirSync(dirname(path), { recursive: true });
    writeFileSync(path, content);
    return path;
  }
  function command(cmd, args, check = true) {
    const result = spawnSync(cmd, args, { cwd: root, env, encoding: "utf8" });
    assert.ifError(result.error);
    if (check) assert.equal(result.status, 0, result.stdout + result.stderr);
    return result;
  }
  const git = (...args) => command("git", args);
  for (const name of [
    "lefthook.yml",
    ".prettierignore",
    ".editorconfig",
    "scripts/repo-tools/config/commitlint.config.cjs",
    "scripts/repo-tools/config/prettier.json",
    "apps/app/settings.gradle.kts",
    "apps/app/gradle/libs.versions.toml",
  ]) {
    const destination = join(root, name);
    mkdirSync(dirname(destination), { recursive: true });
    cpSync(join(ROOT, name), destination);
  }
  cpSync(join(ROOT, "scripts/repo-tools/scripts"), join(root, "scripts/repo-tools/scripts"), {
    recursive: true,
  });
  symlinkSync(join(ROOT, "node_modules"), join(root, "node_modules"), "dir");
  symlinkSync(join(ROOT, "scripts/repo-tools/node_modules"), join(root, "scripts/repo-tools/node_modules"), "dir");
  mkdirSync(join(root, ".cache"), { recursive: true });
  symlinkSync(join(ROOT, ".cache/dev-tools"), join(root, ".cache/dev-tools"), "dir");
  write("package.json", JSON.stringify({ private: true, devDependencies: { lefthook: "2.1.17" } }));
  git("init", "--quiet");
  git("config", "user.name", "Hook Test");
  git("config", "user.email", "hook-test@example.invalid");
  git("config", "commit.gpgsign", "false");
  git("add", "--", "lefthook.yml", "scripts/repo-tools/config", "apps/app/settings.gradle.kts", "package.json");
  git("-c", "core.hooksPath=/dev/null", "commit", "--quiet", "-m", "测试基线");
  command(LEFTHOOK, ["install", "pre-commit", "commit-msg"]);
  const commit = (title = "docs(docs): 📝 更新测试文档") => command("git", ["commit", "--quiet", "-m", title], false);
  return { root, env, write, command, git, commit };
}
function committed(f, title) {
  const result = f.commit(title);
  assert.equal(result.status, 0, result.stdout + result.stderr);
}

test("仅格式化暂存文件，支持中文空格路径", (t) => {
  const f = fixture(t);
  const page = f.write("文档 空格.md", "#标题\n\n  说明\n");
  const unstaged = f.write("untracked.json", '{"keep":1}');
  f.git("add", "--", "文档 空格.md");
  committed(f);
  assert.equal(f.git("show", "HEAD:文档 空格.md").stdout, readFileSync(page, "utf8"));
  assert.equal(readFileSync(unstaged, "utf8"), '{"keep":1}');
});

test("拒绝不符合仓库规范的提交标题，保留 Gradle 模块 scope", (t) => {
  const f = fixture(t);
  for (const title of [
    "fix(core): no emoji",
    "feat(no-scope): ✨ 新功能",
    "unknown(core): 🐛 修复",
    "docs(core): 📝 English only",
  ]) {
    f.write("sample.md", "# 测试\n");
    f.git("add", "--", "sample.md");
    const before = f.git("rev-parse", "HEAD").stdout;
    assert.notEqual(f.commit(title).status, 0);
    assert.equal(f.git("rev-parse", "HEAD").stdout, before);
  }
  committed(f, "docs(web): 📝 预留官网");
});

test("本地锁定 runner 优先于全局版本，缺本地版本阻断", (t) => {
  const f = fixture(t);
  const executable = f.write(
    "global-bin/lefthook",
    '#!/bin/sh\nif [ "$1" = "-h" ]; then exit 0; fi\nprintf "unexpected global lefthook\\n" >&2\nexit 91\n',
  );
  chmodSync(executable, 0o755);
  f.env.PATH = `${join(f.root, "global-bin")}:${f.env.PATH}`;
  f.write("pinned.md", "# 固定版本\n");
  f.git("add", "--", "pinned.md");
  committed(f);
  rmSync(join(f.root, "node_modules"));
  f.write("pinned.md", "# 缺少本地工具\n");
  f.git("add", "--", "pinned.md");
  const result = f.commit();
  assert.notEqual(result.status, 0);
  assert.match(result.stdout + result.stderr, /node_modules\/\.bin\/lefthook/);
  assert.doesNotMatch(result.stdout + result.stderr, /unexpected global lefthook/);
});

test("部分暂存的未暂存编辑不会进入提交", (t) => {
  const f = fixture(t);
  const page = f.write("sample.json", '{"one":1,"two":2,"three":3}\n');
  f.git("add", "--", "sample.json");
  committed(f);
  writeFileSync(page, '{\n  "one": 10,\n  "two":2,\n  "three": 3\n}\n');
  f.git("add", "--", "sample.json");
  writeFileSync(page, '{\n  "one": 10,\n  "two":2,\n  "three": 99\n}\n');
  committed(f);
  assert.equal(JSON.parse(f.git("show", "HEAD:sample.json").stdout).three, 3);
  assert.equal(JSON.parse(f.git("show", "HEAD:sample.json").stdout).one, 10);
  assert.equal(JSON.parse(readFileSync(page, "utf8")).three, 99);
});

test("部分暂存格式恢复冲突阻断，暂存区和工作区均不丢失", (t) => {
  const f = fixture(t);
  const page = f.write("sample.mjs", "const values = [\n  1,\n  2,\n  3,\n];\n");
  f.git("add", "--", "sample.mjs");
  f.git("-c", "core.hooksPath=/dev/null", "commit", "--quiet", "-m", "测试基线");
  const staged = "const values = [\n  1,\n  2,\n  3\n];\n";
  const working = "const values = [\n  99,\n  2,\n  3\n];\n";
  writeFileSync(page, staged);
  f.git("add", "--", "sample.mjs");
  writeFileSync(page, working);
  const before = f.git("rev-parse", "HEAD").stdout;
  assert.notEqual(f.commit("test(dev-workflow): ✅ 验证恢复冲突").status, 0);
  assert.equal(f.git("rev-parse", "HEAD").stdout, before);
  assert.equal(readFileSync(page, "utf8"), working);
  assert.equal(f.git("show", ":sample.mjs").stdout, staged);
});

test("非法语法和缺少格式工具阻断提交", (t) => {
  const f = fixture(t);
  f.write("bad.json", '{"not valid":');
  f.git("add", "--", "bad.json");
  assert.notEqual(f.commit().status, 0);
  f.git("reset", "--", "bad.json");
  f.write("bad.cjs", "module.exports = (\n");
  f.git("add", "--", "bad.cjs");
  assert.notEqual(f.commit().status, 0);
  f.git("reset", "--", "bad.cjs");
  f.write("missing.json", "{}\n");
  f.git("add", "--", "missing.json");
  rmSync(join(f.root, "node_modules"));
  mkdirSync(join(f.root, "node_modules/.bin"), { recursive: true });
  symlinkSync(join(ROOT, "node_modules/.bin/commitlint"), join(f.root, "node_modules/.bin/commitlint"));
  const runner = f.write("node_modules/.bin/lefthook", `#!/bin/sh\nexec '${LEFTHOOK.replaceAll("'", "'\\''")}' "$@"\n`);
  chmodSync(runner, 0o755);
  const result = f.commit();
  assert.notEqual(result.status, 0);
  assert.match(result.stdout + result.stderr, /prettier/);
});

test("Hook 安装保留自定义 hooksPath 和已有 Hook", (t) => {
  const f = fixture(t);
  const installer = join(f.root, "scripts/repo-tools/scripts/install-hooks.mjs");
  f.git("config", "core.hooksPath", ".custom-hooks");
  let result = f.command(process.execPath, [installer], false);
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /core.hooksPath/);
  assert.equal(f.git("config", "--get", "core.hooksPath").stdout.trim(), ".custom-hooks");
  f.git("config", "--unset", "core.hooksPath");
  const original = '#!/bin/sh\nprintf "custom hook\\n"\n';
  const hook = f.write(".git/hooks/pre-commit", original);
  result = f.command(process.execPath, [installer], false);
  assert.notEqual(result.status, 0);
  assert.equal(readFileSync(hook, "utf8"), original);
});

test("重命名和删除正常提交，归档路径也执行格式化", (t) => {
  const f = fixture(t);
  f.write("old.md", "# 原文\n");
  f.write("deleted.md", "# 删除\n");
  f.git("add", "--", "old.md", "deleted.md");
  committed(f);
  f.git("mv", "--", "old.md", "新 名称.md");
  f.git("rm", "--", "deleted.md");
  const archive = f.write("docs/archive/history.json", '{"value":1}');
  f.git("add", "--", "docs/archive/history.json");
  committed(f);
  assert.equal(readFileSync(archive, "utf8"), '{ "value": 1 }\n');
  assert.match(f.git("ls-tree", "--name-only", "-z", "HEAD").stdout, /新 名称.md/);
});

test("XML 文本空白和隐私三句话逐字保持", (t) => {
  const f = fixture(t);
  const resource = readFileSync(
    join(ROOT, "apps/app/shared/src/commonMain/composeResources/values/strings.xml"),
    "utf8",
  );
  const sentences = xml
    .parse(resource)
    .resources.string.filter((n) => n["@_name"].startsWith("privacy_line_"))
    .map((n) => n["#text"]);
  assert.equal(sentences.length, 3);
  const original =
    '<resources>\n<string name="spaces">  保留 空白  </string>\n' +
    sentences.map((s, i) => `<string name="privacy_line_${i + 1}">${s}</string>`).join("\n") +
    "\n</resources>\n";
  const page = f.write("values/strings.xml", original);
  f.git("add", "--", "values/strings.xml");
  committed(f);
  assert.deepEqual(xml.parse(readFileSync(page, "utf8")), xml.parse(original));
  const docs = readFileSync(join(ROOT, "docs/product/privacy-security.md"), "utf8");
  assert.ok(sentences.every((s) => docs.includes(s)));
});

test("Kotlin、JavaScript 和 TOML 使用实际工具", (t) => {
  const f = fixture(t);
  const kotlin = f.write("Example.kt", 'fun example( ) {println("ok")}\n');
  const js = f.write("example.mjs", "const value= [1,2,3]\n");
  f.write("valid.toml", '[tool]\nvalue = "中文"\n');
  f.git("add", "--", "Example.kt", "example.mjs", "valid.toml");
  committed(f, "test(dev-workflow): ✅ 验证多语言门禁");
  assert.doesNotMatch(readFileSync(kotlin, "utf8"), /example\( \)/);
  assert.equal(readFileSync(js, "utf8"), "const value = [1, 2, 3];\n");
  f.write("invalid.toml", "value = [\n");
  f.git("add", "--", "invalid.toml");
  assert.notEqual(f.commit().status, 0);
});
