import { appendFileSync } from "node:fs";
import { cli, context, isMain, run } from "./common.ts";

export interface ChangeSet {
  app: boolean;
  web: boolean;
  full: boolean;
}
const FULL_FILES = new Set([
  "package.json",
  "pnpm-lock.yaml",
  "pnpm-workspace.yaml",
  "turbo.json",
  ".npmrc",
  ".oxlintrc.json",
  ".oxfmtrc.json",
]);
const APP_TOOLS = new Set(["scripts/repo-tools/scripts/android-task.ts", "scripts/repo-tools/scripts/check-apk.ts"]);
// 官网构建经 Gradle 生成 KMP Showcase；shared、版本目录、JS 锁文件和 Gradle 根配置都会改变其产物。
const WEB_PREFIXES = ["apps/web/", "apps/app/shared/", "apps/app/gradle/", "apps/app/kotlin-js-store/"];
const isAppRootFile = (path: string) => path.startsWith("apps/app/") && !path.slice("apps/app/".length).includes("/");

// 基线未知（首次推送、强推或手动触发）时无法证明影响范围，按全量回归处理。
export function classifyChanges(files: readonly string[] | null): ChangeSet {
  if (files === null || files.some((path) => FULL_FILES.has(path) || path.startsWith(".github/")))
    return { app: true, web: true, full: true };
  return {
    app: files.some((path) => path.startsWith("apps/app/") || APP_TOOLS.has(path)),
    web: files.some((path) => WEB_PREFIXES.some((prefix) => path.startsWith(prefix)) || isAppRootFile(path)),
    full: false,
  };
}

function changedFiles(root: string, base: string | undefined, head: string) {
  if (!base || /^0+$/.test(base)) return null;
  for (const sha of [base, head])
    if (run("git", ["cat-file", "-e", `${sha}^{commit}`], { cwd: root, check: false, capture: true }).status !== 0)
      return null;
  return run("git", ["diff", "--name-only", "--no-renames", `${base}...${head}`], { cwd: root, capture: true })
    .stdout.split("\n")
    .filter(Boolean);
}

if (isMain(import.meta.url))
  await cli(() => {
    const { root, values } = context({
      base: { type: "string" },
      head: { type: "string" },
      output: { type: "string" },
    });
    if (!values.head) throw new Error("用法：ci-changes.ts --head <SHA> [--base <SHA>] [--output <文件>]");
    const changes = classifyChanges(changedFiles(root, values.base, values.head));
    const lines = Object.entries(changes).map(([name, value]) => `${name}=${value}`);
    console.log(lines.join("\n"));
    if (values.output) appendFileSync(values.output, `${lines.join("\n")}\n`, "utf8");
  });
