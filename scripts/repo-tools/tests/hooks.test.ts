import assert from "node:assert/strict";
import { cpSync, mkdirSync, mkdtempSync, readFileSync, rmSync, symlinkSync, writeFileSync, chmodSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, test, type TestContext } from "node:test";
import { XMLParser } from "fast-xml-parser";
import { runAsync } from "../scripts/common.ts";

const ROOT = fileURLToPath(new URL("../../../", import.meta.url));
const LEFTHOOK = join(ROOT, "node_modules/.bin/lefthook");
const xml = new XMLParser({
  ignoreAttributes: false,
  trimValues: false,
  parseTagValue: false,
  parseAttributeValue: false,
});
function fixture(t: TestContext) {
  const root = mkdtempSync(join(tmpdir(), "gazekeep-hooks 中文 "));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  const env: NodeJS.ProcessEnv = {
    ...process.env,
    PATH: `${join(ROOT, "node_modules/.bin")}:${process.env.PATH}`,
    LEFTHOOK_OUTPUT: "failure,summary",
  };
  delete env.LEFTHOOK_BIN;
  function write(name: string, content: string) {
    const path = join(root, name);
    mkdirSync(dirname(path), { recursive: true });
    writeFileSync(path, content);
    return path;
  }
  async function command(cmd: string, args: string[], check = true) {
    const result = await runAsync(cmd, args, { cwd: root, env, capture: true, check: false });
    if (check) assert.equal(result.status, 0, result.stdout + result.stderr);
    return result;
  }
  const git = (...args: string[]) => command("git", args);
  for (const name of [
    "lefthook.yml",
    ".oxfmtrc.json",
    ".oxlintrc.json",
    ".editorconfig",
    "apps/web/src/styles/tailwind.css",
    "scripts/repo-tools/config/commitlint.config.ts",
    "scripts/repo-tools/package.json",
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
  const commit = (title = "docs(docs): 📝 更新测试文档") => command("git", ["commit", "--quiet", "-m", title], false);
  async function init() {
    await git("init", "--quiet");
    await git("config", "user.name", "Hook Test");
    await git("config", "user.email", "hook-test@example.invalid");
    await git("config", "commit.gpgsign", "false");
    await git("add", "--", "lefthook.yml", "scripts/repo-tools/config", "apps/app/settings.gradle.kts", "package.json");
    await git("-c", "core.hooksPath=/dev/null", "commit", "--quiet", "-m", "测试基线");
    await command(LEFTHOOK, ["install", "pre-commit", "commit-msg"]);
  }
  return { root, env, write, command, git, commit, init };
}
async function committed(f: ReturnType<typeof fixture>, title?: string) {
  const result = await f.commit(title);
  assert.equal(result.status, 0, result.stdout + result.stderr);
}

describe("Git hooks 与格式检查", { concurrency: 4 }, () => {
  test("仅格式化暂存文件，支持中文空格路径", async (t) => {
    const f = fixture(t);
    await f.init();
    const page = f.write("文档 空格.md", "#标题\n\n  说明\n");
    const unstaged = f.write("untracked.json", '{"keep":1}');
    await f.git("add", "--", "文档 空格.md");
    await committed(f);
    assert.equal((await f.git("show", "HEAD:文档 空格.md")).stdout, readFileSync(page, "utf8"));
    assert.equal(readFileSync(unstaged, "utf8"), '{"keep":1}');
  });

  test("拒绝不符合仓库规范的提交标题，保留 Gradle 模块 scope", async (t) => {
    const f = fixture(t);
    await f.init();
    for (const title of [
      "fix(core): no emoji",
      "feat(no-scope): ✨ 新功能",
      "unknown(core): 🐛 修复",
      "docs(core): 📝 English only",
    ]) {
      f.write("sample.md", "# 测试\n");
      await f.git("add", "--", "sample.md");
      const before = (await f.git("rev-parse", "HEAD")).stdout;
      assert.notEqual((await f.commit(title)).status, 0);
      assert.equal((await f.git("rev-parse", "HEAD")).stdout, before);
    }
    await committed(f, "docs(web): 📝 预留官网");
  });

  test("本地锁定 runner 优先于全局版本，缺本地版本阻断", async (t) => {
    const f = fixture(t);
    await f.init();
    const executable = f.write(
      "global-bin/lefthook",
      '#!/bin/sh\nif [ "$1" = "-h" ]; then exit 0; fi\nprintf "unexpected global lefthook\\n" >&2\nexit 91\n',
    );
    chmodSync(executable, 0o755);
    f.env.PATH = `${join(f.root, "global-bin")}:${f.env.PATH}`;
    f.write("pinned.md", "# 固定版本\n");
    await f.git("add", "--", "pinned.md");
    await committed(f);
    rmSync(join(f.root, "node_modules"));
    f.write("pinned.md", "# 缺少本地工具\n");
    await f.git("add", "--", "pinned.md");
    const result = await f.commit();
    assert.notEqual(result.status, 0);
    assert.match(result.stdout + result.stderr, /node_modules\/\.bin\/lefthook/);
    assert.doesNotMatch(result.stdout + result.stderr, /unexpected global lefthook/);
  });

  test("部分暂存的未暂存编辑不会进入提交", async (t) => {
    const f = fixture(t);
    await f.init();
    const page = f.write("sample.json", '{"one":1,"two":2,"three":3}\n');
    await f.git("add", "--", "sample.json");
    await committed(f);
    writeFileSync(page, '{\n  "one": 10,\n  "two":2,\n  "three": 3\n}\n');
    await f.git("add", "--", "sample.json");
    writeFileSync(page, '{\n  "one": 10,\n  "two":2,\n  "three": 99\n}\n');
    await committed(f);
    assert.equal(JSON.parse((await f.git("show", "HEAD:sample.json")).stdout).three, 3);
    assert.equal(JSON.parse((await f.git("show", "HEAD:sample.json")).stdout).one, 10);
    assert.equal(JSON.parse(readFileSync(page, "utf8")).three, 99);
  });

  test("部分暂存格式恢复冲突阻断，暂存区和工作区均不丢失", async (t) => {
    const f = fixture(t);
    await f.init();
    const page = f.write("sample.mjs", "export const values = [\n  1,\n  2,\n  3,\n];\n");
    await f.git("add", "--", "sample.mjs");
    await f.git("-c", "core.hooksPath=/dev/null", "commit", "--quiet", "-m", "测试基线");
    const staged = "export const values = [\n  1,\n  2,\n  3\n];\n";
    const working = "export const values = [\n  99,\n  2,\n  3\n];\n";
    writeFileSync(page, staged);
    await f.git("add", "--", "sample.mjs");
    writeFileSync(page, working);
    const before = (await f.git("rev-parse", "HEAD")).stdout;
    assert.notEqual((await f.commit("test(dev-workflow): ✅ 验证恢复冲突")).status, 0);
    assert.equal((await f.git("rev-parse", "HEAD")).stdout, before);
    assert.equal(readFileSync(page, "utf8"), working);
    assert.equal((await f.git("show", ":sample.mjs")).stdout, staged);
  });

  test("非法语法和缺少格式工具阻断提交", async (t) => {
    const f = fixture(t);
    await f.init();
    f.write("bad.json", '{"not valid":');
    await f.git("add", "--", "bad.json");
    assert.notEqual((await f.commit()).status, 0);
    await f.git("reset", "--", "bad.json");
    f.write("bad.cjs", "module.exports = (\n");
    await f.git("add", "--", "bad.cjs");
    assert.notEqual((await f.commit()).status, 0);
    await f.git("reset", "--", "bad.cjs");
    f.write("missing.json", "{}\n");
    await f.git("add", "--", "missing.json");
    rmSync(join(f.root, "node_modules"));
    mkdirSync(join(f.root, "node_modules/.bin"), { recursive: true });
    symlinkSync(join(ROOT, "node_modules/.bin/commitlint"), join(f.root, "node_modules/.bin/commitlint"));
    const runner = f.write(
      "node_modules/.bin/lefthook",
      `#!/bin/sh\nexec '${LEFTHOOK.replaceAll("'", "'\\''")}' "$@"\n`,
    );
    chmodSync(runner, 0o755);
    const result = await f.commit();
    assert.notEqual(result.status, 0);
    assert.match(result.stdout + result.stderr, /oxfmt/);
  });

  test("Hook 安装保留自定义 hooksPath 和已有 Hook", async (t) => {
    const f = fixture(t);
    await f.init();
    const installer = join(f.root, "scripts/repo-tools/scripts/install-hooks.ts");
    await f.git("config", "core.hooksPath", ".custom-hooks");
    let result = await f.command(process.execPath, [installer], false);
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /core.hooksPath/);
    assert.equal((await f.git("config", "--get", "core.hooksPath")).stdout.trim(), ".custom-hooks");
    await f.git("config", "--unset", "core.hooksPath");
    const original = '#!/bin/sh\nprintf "custom hook\\n"\n';
    const hook = f.write(".git/hooks/pre-commit", original);
    result = await f.command(process.execPath, [installer], false);
    assert.notEqual(result.status, 0);
    assert.equal(readFileSync(hook, "utf8"), original);
  });

  test("重命名和删除正常提交，归档路径也执行格式化", async (t) => {
    const f = fixture(t);
    await f.init();
    f.write("old.md", "# 原文\n");
    f.write("deleted.md", "# 删除\n");
    await f.git("add", "--", "old.md", "deleted.md");
    await committed(f);
    await f.git("mv", "--", "old.md", "新 名称.md");
    await f.git("rm", "--", "deleted.md");
    const archive = f.write("docs/archive/history.json", '{"value":1}');
    await f.git("add", "--", "docs/archive/history.json");
    await committed(f);
    assert.equal(readFileSync(archive, "utf8"), '{ "value": 1 }\n');
    assert.match((await f.git("ls-tree", "--name-only", "-z", "HEAD")).stdout, /新 名称.md/);
  });

  test("XML 文本空白和隐私三句话逐字保持", async (t) => {
    const f = fixture(t);
    await f.init();
    const resource = readFileSync(
      join(ROOT, "apps/app/shared/src/commonMain/composeResources/values/strings.xml"),
      "utf8",
    );
    const sentences: string[] = xml
      .parse(resource)
      .resources.string.filter((n: Record<string, string>) => n["@_name"].startsWith("privacy_line_"))
      .map((n: Record<string, string>) => n["#text"]);
    assert.equal(sentences.length, 3);
    const original =
      '<resources>\n<string name="spaces">  保留 空白  </string>\n' +
      sentences.map((s, i) => `<string name="privacy_line_${i + 1}">${s}</string>`).join("\n") +
      "\n</resources>\n";
    const page = f.write("values/strings.xml", original);
    await f.git("add", "--", "values/strings.xml");
    await committed(f);
    assert.equal(readFileSync(page, "utf8"), original);
    assert.deepEqual(xml.parse(readFileSync(page, "utf8")), xml.parse(original));
    const docs = readFileSync(join(ROOT, "docs/product.md"), "utf8");
    assert.ok(sentences.every((s) => docs.includes(s)));
  });

  test("Kotlin、JavaScript 和 TOML 使用实际工具", async (t) => {
    const f = fixture(t);
    await f.init();
    const kotlin = f.write("Example.kt", 'fun example( ) {println("ok")}\n');
    const js = f.write("example.mjs", "export const value= [1,2,3]\n");
    f.write("valid.toml", '[tool]\nvalue = "中文"\n');
    await f.git("add", "--", "Example.kt", "example.mjs", "valid.toml");
    await committed(f, "test(dev-workflow): ✅ 验证多语言门禁");
    assert.doesNotMatch(readFileSync(kotlin, "utf8"), /example\( \)/);
    assert.equal(readFileSync(js, "utf8"), "export const value = [1, 2, 3];\n");
    f.write("invalid.toml", "value = [\n");
    await f.git("add", "--", "invalid.toml");
    assert.notEqual((await f.commit()).status, 0);
  });

  test("非法 XML 阻断提交，合法 XML 不重排", async (t) => {
    const f = fixture(t);
    await f.init();
    f.write("values/broken.xml", '<resources><string name="a">未闭合</resources>\n');
    await f.git("add", "--", "values/broken.xml");
    assert.notEqual((await f.commit()).status, 0);
    await f.git("reset", "--", "values/broken.xml");
    const original = '<resources><string   name="a">单行</string></resources>\n';
    const page = f.write("values/single.xml", original);
    await f.git("add", "--", "values/single.xml");
    await committed(f);
    assert.equal(readFileSync(page, "utf8"), original);
  });

  test("TypeScript 经 oxlint 检查后由 oxfmt 格式化，Tailwind 类名排序", async (t) => {
    const f = fixture(t);
    await f.init();
    f.write("bad.ts", "export function stop(): void {\n  debugger;\n}\n");
    await f.git("add", "--", "bad.ts");
    const blocked = await f.commit();
    assert.notEqual(blocked.status, 0);
    assert.match(blocked.stdout + blocked.stderr, /debugger/);
    await f.git("reset", "--", "bad.ts");
    const ts = f.write("example.ts", "export const value:number[]= [1,2,3]\n");
    const page = f.write("Card.astro", '---\n---\n<div class="p-4 flex mt-2 card">卡片</div>\n');
    await f.git("add", "--", "example.ts", "Card.astro");
    await committed(f, "test(dev-workflow): ✅ 验证 TypeScript 与 Astro 门禁");
    assert.equal(readFileSync(ts, "utf8"), "export const value: number[] = [1, 2, 3];\n");
    assert.match(readFileSync(page, "utf8"), /class="card mt-2 flex p-4"/);
  });
});
