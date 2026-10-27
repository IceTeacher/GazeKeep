import { appendFileSync, existsSync, readFileSync, readdirSync } from "node:fs";
import { join, resolve } from "node:path";
import { cli, context, isMain, report } from "./common.ts";

// LSPosed 官方模块仓库（Xposed-Modules-Repo）的发布元数据，由 .github/workflows/lsposed-repo.yml 推送到模块仓库根目录。
export const METADATA_DIR = "apps/app/distribution/lsposed";
const METADATA_FILES = ["README.md", "SCOPE", "SOURCE_URL", "SUMMARY"];
const SOURCE_URL = "https://github.com/IceTeacher/GazeKeep";

export interface AppVersion {
  versionCode: string;
  versionName: string;
}
export function readAppVersion(root: string): AppVersion {
  const build = readFileSync(join(root, "apps/app/android/build.gradle.kts"), "utf8");
  const versionName = build.match(/versionName\s*=\s*"([^"]+)"/)?.[1];
  const versionCode = build.match(/versionCode\s*=\s*(\d+)/)?.[1];
  if (!versionName || !versionCode) throw new Error("android/build.gradle.kts 缺少 versionCode 或 versionName");
  return { versionCode, versionName };
}
// 与索引 bot（Xposed-Modules-Repo/modules src/index.ts）逐字一致；不一致时 bot 会另建空提交改写 tag。
export function moduleTag({ versionCode, versionName }: AppVersion) {
  return `${versionCode}-${versionName.replace(" ", "-")}`;
}
export function moduleRelease(root: string, tag: string) {
  const version = readAppVersion(root);
  if (tag !== `v${version.versionName}`) throw new Error(`tag ${tag} 与 versionName ${version.versionName} 不一致`);
  return { ...version, moduleTag: moduleTag(version) };
}

function privacyLines(root: string) {
  const page = readFileSync(join(root, "docs/product/privacy-security.md"), "utf8");
  const section = page.split(/^## /m).find((part) => part.startsWith("隐私文案真源")) ?? "";
  return [...section.matchAll(/^\d+\.\s+(.+)$/gm)].map((match) => match[1].trim());
}
// LSPosed 静态作用域中 system 表示系统框架，模块仓库沿用旧作用域的包名 android。
function expectedScope(root: string) {
  return readFileSync(join(root, "apps/app/android/src/main/resources/META-INF/xposed/scope.list"), "utf8")
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean)
    .map((name) => (name === "system" ? "android" : name));
}
export function validateMetadata(root: string) {
  const dir = join(root, METADATA_DIR);
  const errors: string[] = [];
  const present = existsSync(dir) ? readdirSync(dir) : [];
  for (const name of present.filter((n) => !METADATA_FILES.includes(n)))
    errors.push(`${METADATA_DIR}: 多余文件 ${name}，模块仓库只放元数据`);
  const missing = METADATA_FILES.filter((n) => !present.includes(n));
  for (const name of missing) errors.push(`${METADATA_DIR}: 缺少 ${name}`);
  const read = (name: string) => (missing.includes(name) ? undefined : readFileSync(join(dir, name), "utf8"));

  const summary = read("SUMMARY");
  if (summary !== undefined && (!summary.trim() || summary.trim().includes("\n")))
    errors.push(`${METADATA_DIR}/SUMMARY 必须为非空单行`);

  const source = read("SOURCE_URL");
  if (source !== undefined && source.trim() !== SOURCE_URL)
    errors.push(`${METADATA_DIR}/SOURCE_URL 必须为 ${SOURCE_URL}`);

  const scope = read("SCOPE");
  if (scope !== undefined) {
    let parsed: unknown;
    try {
      parsed = JSON.parse(scope);
    } catch {
      parsed = undefined;
    }
    const expected = expectedScope(root);
    if (!Array.isArray(parsed) || !parsed.every((n) => typeof n === "string"))
      errors.push(`${METADATA_DIR}/SCOPE 必须是包名字符串的 JSON 数组`);
    else if ([...parsed].sort().join("\n") !== [...expected].sort().join("\n"))
      errors.push(`${METADATA_DIR}/SCOPE 与 scope.list 不一致，应为 ${JSON.stringify(expected)}`);
  }

  const readme = read("README.md");
  if (readme !== undefined) {
    const lines = privacyLines(root);
    if (lines.length !== 6) errors.push("无法从 docs/product/privacy-security.md 读取中英文各三句隐私文案");
    for (const line of lines.filter((l) => !readme.includes(l)))
      errors.push(`${METADATA_DIR}/README.md 缺少隐私文案：${line}`);
  }
  return errors;
}

if (isMain(import.meta.url))
  await cli(() => {
    const { root, values } = context({
      tag: { type: "string" },
      output: { type: "string" },
      "release-root": { type: "string" },
    });
    report(validateMetadata(root), "LSPosed 模块仓库元数据检查通过");
    if (!values.tag) return;
    // 元数据与工具取自同步流程所在提交；版本取自被同步 tag 的源码，补发早于同步流程的旧 tag 时二者不同。
    const release = moduleRelease(resolve(values["release-root"] ?? root), values.tag);
    const lines = [
      `module_tag=${release.moduleTag}`,
      `version_code=${release.versionCode}`,
      `version_name=${release.versionName}`,
    ];
    if (values.output) appendFileSync(values.output, `${lines.join("\n")}\n`);
    else console.log(lines.join("\n"));
  });
