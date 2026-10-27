import assert from "node:assert/strict";
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test, { type TestContext } from "node:test";
import {
  moduleRelease,
  moduleTag,
  readAppVersion,
  renderModuleReadme,
  validateMetadata,
} from "../scripts/lsposed-repo.ts";
import { run, SOURCE_ROOT } from "../scripts/common.ts";

const PRIVACY = [
  "只在快要熄屏时打开前置摄像头，看完立刻关闭。",
  "画面只在内存里判断是否注视了屏幕，不保存、不上传。",
  "看不清、没看清、旁边有别人，都会让屏幕正常熄灭。",
  "The front camera opens only near screen timeout and closes immediately after checking.",
  "Frames are processed in memory to check whether you are looking at the screen. Nothing is saved or uploaded.",
  "Unclear results, failed detection or another person in view let the screen sleep normally.",
];

function fixture(t: TestContext) {
  const root = mkdtempSync(join(tmpdir(), "gazekeep-lsposed-"));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  const write = (name: string, content: string) => {
    mkdirSync(join(root, name, ".."), { recursive: true });
    writeFileSync(join(root, name), content);
  };
  write(
    "apps/app/android/build.gradle.kts",
    'android {\n  defaultConfig {\n    versionCode = 5\n    versionName = "1.1.3"\n  }\n}\n',
  );
  write("apps/app/android/src/main/resources/META-INF/xposed/scope.list", "system\n");
  write(
    "docs/product/privacy-security.md",
    `# 隐私与安全\n\n## 隐私文案真源\n\n${PRIVACY.slice(0, 3)
      .map((line, i) => `${i + 1}. ${line}`)
      .join("\n")}\n\n英文文案：\n\n${PRIVACY.slice(3)
      .map((line, i) => `${i + 1}. ${line}`)
      .join("\n")}\n\n## 数据与权限边界\n`,
  );
  write(
    "README.md",
    `# GazeKeep\n\n${PRIVACY.slice(0, 3)
      .map((line, i) => `${i + 1}. ${line}`)
      .join("\n")}\n`,
  );
  write("apps/app/distribution/lsposed/SUMMARY", "注视屏幕时延后熄屏。\n");
  write("apps/app/distribution/lsposed/SCOPE", '["android"]\n');
  write("apps/app/distribution/lsposed/SOURCE_URL", "https://github.com/IceTeacher/GazeKeep\n");
  return { root, write };
}

test("模块仓库 tag 与索引 bot 的规则一致", (t) => {
  const { root, write } = fixture(t);
  assert.deepEqual(readAppVersion(root), { versionCode: "5", versionName: "1.1.3" });
  assert.equal(moduleTag({ versionCode: "5", versionName: "1.1.3" }), "5-1.1.3");
  // WHY：bot 使用 String.replace 只替换首个空格，tag 不一致时会另建空提交并改写 tag。
  assert.equal(moduleTag({ versionCode: "6", versionName: "1.2 beta 1" }), "6-1.2-beta 1");
  write("apps/app/android/build.gradle.kts", 'versionName = "1.1.3"\n');
  assert.throws(() => readAppVersion(root), /versionCode/);
});

test("同步前要求 tag 与当前 versionName 一致", (t) => {
  const { root } = fixture(t);
  assert.deepEqual(moduleRelease(root, "v1.1.3"), { versionCode: "5", versionName: "1.1.3", moduleTag: "5-1.1.3" });
  for (const tag of ["v1.1.2", "1.1.3", "v1.1.3-rc"]) assert.throws(() => moduleRelease(root, tag), /versionName/, tag);
});

test("补发旧 tag 时元数据取自当前仓库，版本取自 tag 的源码", (t) => {
  const { root } = fixture(t);
  // WHY：早于同步流程的 tag 不含元数据与工具，只提供版本号与发布说明。
  const release = mkdtempSync(join(tmpdir(), "gazekeep-lsposed-tag-"));
  t.after(() => rmSync(release, { recursive: true, force: true }));
  mkdirSync(join(release, "apps/app/android"), { recursive: true });
  writeFileSync(join(release, "apps/app/android/build.gradle.kts"), 'versionCode = 4\nversionName = "1.1.2"\n');
  const cli = (...args: string[]) =>
    run(process.execPath, [join(SOURCE_ROOT, "scripts/repo-tools/scripts/lsposed-repo.ts"), "--root", root, ...args], {
      capture: true,
      check: false,
    });
  const ok = cli("--release-root", release, "--tag", "v1.1.2");
  assert.equal(ok.status, 0, ok.stderr);
  assert.match(ok.stdout, /^module_tag=4-1\.1\.2$/m);
  assert.match(ok.stdout, /^version_name=1\.1\.2$/m);
  const mismatch = cli("--release-root", release, "--tag", "v1.1.3");
  assert.notEqual(mismatch.status, 0);
  assert.match(mismatch.stderr, /versionName 1\.1\.2/);
});

test("元数据通过校验", (t) => {
  const { root } = fixture(t);
  assert.deepEqual(validateMetadata(root), []);
});

test("元数据目录只放 SCOPE、SOURCE_URL 与 SUMMARY，README 由根 README 生成", (t) => {
  const { root, write } = fixture(t);
  write("apps/app/distribution/lsposed/LICENSE", "MIT\n");
  write("apps/app/distribution/lsposed/README.md", "# 另一份\n");
  rmSync(join(root, "apps/app/distribution/lsposed/SUMMARY"));
  const errors = validateMetadata(root).join("\n");
  assert.match(errors, /多余.*LICENSE/);
  assert.match(errors, /多余.*README\.md/);
  assert.match(errors, /缺少.*SUMMARY/);
});

test("SUMMARY 必须为非空单行，SOURCE_URL 指向本仓库", (t) => {
  const { root, write } = fixture(t);
  for (const summary of ["", "\n", "第一行\n第二行\n"]) {
    write("apps/app/distribution/lsposed/SUMMARY", summary);
    assert.match(validateMetadata(root).join("\n"), /SUMMARY/, JSON.stringify(summary));
  }
  write("apps/app/distribution/lsposed/SUMMARY", "注视屏幕时延后熄屏。\n");
  write("apps/app/distribution/lsposed/SOURCE_URL", "https://github.com/someone/fork\n");
  assert.match(validateMetadata(root).join("\n"), /SOURCE_URL/);
});

test("SCOPE 必须是字符串数组且与模块作用域登记一致", (t) => {
  const { root, write } = fixture(t);
  for (const scope of [
    "android",
    '"android"',
    "[1]",
    '["com.android.systemui"]',
    '["android","com.android.systemui"]',
  ]) {
    write("apps/app/distribution/lsposed/SCOPE", scope);
    assert.match(validateMetadata(root).join("\n"), /SCOPE/, scope);
  }
  write("apps/app/distribution/lsposed/SCOPE", '["com.android.systemui"]');
  write("apps/app/android/src/main/resources/META-INF/xposed/scope.list", "com.android.systemui\n");
  assert.deepEqual(validateMetadata(root), []);
});

test("根 README 必须逐字包含隐私文案真源的中文三句", (t) => {
  const { root, write } = fixture(t);
  write("README.md", `# GazeKeep\n\n${PRIVACY.slice(0, 3).join("\n").replace("不保存、不上传", "不保存")}\n`);
  assert.match(validateMetadata(root).join("\n"), /README.*不保存、不上传/);
  write("docs/product/privacy-security.md", "# 隐私与安全\n");
  assert.match(validateMetadata(root).join("\n"), /隐私文案/);
});

test("模块 README 与根 README 一致，相对图片与链接改为固定提交的绝对地址", () => {
  const sha = "0123456789abcdef0123456789abcdef01234567";
  const raw = `https://raw.githubusercontent.com/IceTeacher/GazeKeep/${sha}`;
  const blob = `https://github.com/IceTeacher/GazeKeep/blob/${sha}`;
  const source = [
    '<img src="docs/assets/app-icon.png" width="120" alt="GazeKeep" />',
    "[![License](https://img.shields.io/badge/MIT-yellow.svg)](LICENSE) [English](./README_en.md)",
    "![截图](docs/assets/home.png) 见[隐私](docs/product/privacy-security.md#隐私文案真源)与[本节](#下载)。",
    '<a href="docs/index.md">文档</a> [官网](https://gazekeep.rainafter.cn) [邮件](mailto:a@b.c)',
    "```",
    "[代码块](docs/keep.md)",
    "```",
  ].join("\n");
  assert.equal(
    renderModuleReadme(source, sha),
    [
      `<img src="${raw}/docs/assets/app-icon.png" width="120" alt="GazeKeep" />`,
      `[![License](https://img.shields.io/badge/MIT-yellow.svg)](${blob}/LICENSE) [English](${blob}/README_en.md)`,
      `![截图](${raw}/docs/assets/home.png) 见[隐私](${blob}/docs/product/privacy-security.md#隐私文案真源)与[本节](#下载)。`,
      `<a href="${blob}/docs/index.md">文档</a> [官网](https://gazekeep.rainafter.cn) [邮件](mailto:a@b.c)`,
      "```",
      "[代码块](docs/keep.md)",
      "```",
    ].join("\n"),
  );
  assert.throws(() => renderModuleReadme(source, "main"), /提交/);
});

test("仓库内的模块仓库元数据通过校验", () => {
  assert.deepEqual(validateMetadata(SOURCE_ROOT), []);
  assert.match(readFileSync(join(SOURCE_ROOT, "apps/app/distribution/lsposed/SCOPE"), "utf8"), /"android"/);
});
