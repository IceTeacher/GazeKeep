import { appendFileSync, existsSync, readFileSync, readdirSync, writeFileSync } from "node:fs";
import { join, resolve } from "node:path";
import { cli, context, isMain, report } from "./common.ts";

// LSPosed 官方模块仓库（Xposed-Modules-Repo）的发布元数据，由 .github/workflows/lsposed-repo.yml 推送到模块仓库根目录；
// 模块仓库的 README.md 由根 README.md 生成，与源码仓库保持一致，不另行维护。
export const METADATA_DIR = "apps/app/distribution/lsposed";
const METADATA_FILES = ["SCOPE", "SOURCE_URL", "SUMMARY"];
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

// 模块仓库与 modules.lsposed.org 不含源码，相对图片与文档链接须指向源码仓库；固定到提交，避免日后移动文件后失效。
export function renderModuleReadme(source: string, sha: string) {
  if (!/^[0-9a-f]{40}$/.test(sha)) throw new Error(`README 链接必须固定到完整提交 SHA：${sha}`);
  const absolute = (target: string, kind: "raw" | "blob") => {
    if (/^([a-z][a-z0-9+.-]*:|#|\/)/i.test(target)) return target;
    const path = target.replace(/^\.\//, "");
    return kind === "raw"
      ? `https://raw.githubusercontent.com/IceTeacher/GazeKeep/${sha}/${path}`
      : `${SOURCE_URL}/blob/${sha}/${path}`;
  };
  let fenced = false;
  return source
    .split("\n")
    .map((line) => {
      if (/^\s*(```|~~~)/.test(line)) fenced = !fenced;
      else if (!fenced)
        line = line
          .replace(/(\bsrc=")([^"]+)"/g, (_, attr, target) => `${attr}${absolute(target, "raw")}"`)
          .replace(/(\bhref=")([^"]+)"/g, (_, attr, target) => `${attr}${absolute(target, "blob")}"`)
          .replace(/(!\[[^\]]*\]\()([^)\s]+)\)/g, (_, head, target) => `${head}${absolute(target, "raw")})`)
          // 图片已在上一步改为绝对地址，剩余的相对目标都是普通链接。
          .replace(/(\]\()([^)\s]+)\)/g, (_, head, target) => `${head}${absolute(target, "blob")})`);
      return line;
    })
    .join("\n");
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

  const lines = privacyLines(root);
  if (lines.length !== 6) errors.push("无法从 docs/product/privacy-security.md 读取中英文各三句隐私文案");
  const readme = readFileSync(join(root, "README.md"), "utf8");
  for (const line of lines.slice(0, 3).filter((l) => !readme.includes(l)))
    errors.push(`README.md 缺少隐私文案：${line}`);
  return errors;
}

if (isMain(import.meta.url))
  await cli(() => {
    const { root, values } = context({
      tag: { type: "string" },
      output: { type: "string" },
      "release-root": { type: "string" },
      "readme-out": { type: "string" },
      ref: { type: "string" },
    });
    report(validateMetadata(root), "LSPosed 模块仓库元数据检查通过");
    if (values["readme-out"]) {
      if (!values.ref) throw new Error("--readme-out 需要同时提供 --ref <提交 SHA>");
      writeFileSync(
        values["readme-out"],
        renderModuleReadme(readFileSync(join(root, "README.md"), "utf8"), values.ref),
      );
    }
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
