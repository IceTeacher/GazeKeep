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

// 候选为 head 本身与合并提交的第二父提交（被合入的分支头）；只有树完全一致且该提交的 push 运行成功才算已验证。
// WHY：dev 推送、dev→main 的 PR、合并到 main 与合并后同步 dev 往往是同一棵树，复用结果避免重复的长构建。
export async function verifiedSource(root: string, head: string, passed: (sha: string) => Promise<boolean>) {
  const parents = run("git", ["rev-list", "--parents", "-n", "1", head], { cwd: root, capture: true })
    .stdout.trim()
    .split(" ");
  for (const sha of [parents[0], parents[2]]) {
    if (!sha) continue;
    if (sha !== parents[0] && run("git", ["diff", "--quiet", sha, head], { cwd: root, check: false }).status !== 0)
      continue;
    if (await passed(sha)) return sha;
  }
  return undefined;
}

// 查询本流水线在该提交上是否有成功的 push 运行；当前运行尚未结束，不会把自己计入。
async function pipelinePassed(sha: string) {
  const { GITHUB_API_URL, GITHUB_REPOSITORY, GITHUB_TOKEN, GITHUB_WORKFLOW_REF } = process.env;
  if (!GITHUB_API_URL || !GITHUB_REPOSITORY || !GITHUB_TOKEN || !GITHUB_WORKFLOW_REF)
    throw new Error("--reuse 需要 GITHUB_API_URL、GITHUB_REPOSITORY、GITHUB_TOKEN 与 GITHUB_WORKFLOW_REF");
  const workflow = GITHUB_WORKFLOW_REF.split("@")[0].split("/").pop();
  const url = `${GITHUB_API_URL}/repos/${GITHUB_REPOSITORY}/actions/workflows/${workflow}/runs?head_sha=${sha}&event=push&status=success&per_page=1`;
  const response = await fetch(url, {
    headers: { accept: "application/vnd.github+json", authorization: `Bearer ${GITHUB_TOKEN}` },
  });
  if (!response.ok) throw new Error(`查询流水线运行失败：${response.status}`);
  return ((await response.json()) as { total_count: number }).total_count > 0;
}

if (isMain(import.meta.url))
  await cli(async () => {
    const { root, values } = context({
      base: { type: "string" },
      head: { type: "string" },
      output: { type: "string" },
      reuse: { type: "boolean" },
    });
    if (!values.head) throw new Error("用法：ci-changes.ts --head <SHA> [--base <SHA>] [--reuse] [--output <文件>]");
    const source = values.reuse ? await verifiedSource(root, values.head, pipelinePassed) : undefined;
    const changes = {
      ...classifyChanges(changedFiles(root, values.base, values.head)),
      verified: source !== undefined,
    };
    if (source) console.log(`复用已验证提交：${source}`);
    const lines = Object.entries(changes).map(([name, value]) => `${name}=${value}`);
    console.log(lines.join("\n"));
    if (values.output) appendFileSync(values.output, `${lines.join("\n")}\n`, "utf8");
  });
