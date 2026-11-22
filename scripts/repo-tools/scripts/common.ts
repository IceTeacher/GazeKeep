import { existsSync, readFileSync, readdirSync, realpathSync } from "node:fs";
import { isAbsolute, join, relative, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";
import { spawn, spawnSync } from "node:child_process";
import { parseArgs, type ParseArgsOptionsConfig } from "node:util";

export const SOURCE_ROOT = fileURLToPath(new URL("../../../", import.meta.url));
export const IGNORED = new Set([
  ".git",
  ".gradle",
  ".kotlin",
  "node_modules",
  "build",
  ".cache",
  ".venv",
  "__pycache__",
  ".turbo",
  ".next",
  "dist",
  "coverage",
]);
export interface RunOptions {
  cwd?: string;
  capture?: boolean;
  env?: NodeJS.ProcessEnv;
  check?: boolean;
  maxBuffer?: number;
}
export function run(
  command: string,
  args: readonly string[],
  {
    cwd = process.cwd(),
    capture = false,
    env = process.env,
    check = true,
    maxBuffer = 32 * 1024 * 1024,
  }: RunOptions = {},
) {
  const result = spawnSync(command, args, {
    cwd,
    env,
    encoding: "utf8",
    stdio: capture ? "pipe" : "inherit",
    maxBuffer,
  });
  if (result.error) throw result.error;
  if (check && result.status !== 0)
    throw new Error(
      `${command} 执行失败（${result.status ?? result.signal}）${capture ? `：${result.stderr?.trim()}` : ""}`,
    );
  return result;
}
export function runAsync(
  command: string,
  args: readonly string[],
  {
    cwd = process.cwd(),
    capture = false,
    env = process.env,
    check = true,
    maxBuffer = 32 * 1024 * 1024,
  }: RunOptions = {},
) {
  return new Promise<{ status: number | null; signal: NodeJS.Signals | null; stdout: string; stderr: string }>(
    (resolvePromise, reject) => {
      const child = spawn(command, args, {
        cwd,
        env,
        stdio: capture ? ["ignore", "pipe", "pipe"] : "inherit",
      });
      const stdoutChunks: Buffer[] = [];
      const stderrChunks: Buffer[] = [];
      let totalBytes = 0;
      let settled = false;
      const fail = (error: Error) => {
        if (settled) return;
        settled = true;
        reject(error);
      };
      if (capture) {
        const collect = (target: Buffer[]) => (chunk: Buffer) => {
          totalBytes += chunk.length;
          if (totalBytes > maxBuffer) {
            child.kill();
            fail(new Error(`${command} 输出超过缓冲区上限（${maxBuffer} 字节）`));
            return;
          }
          target.push(chunk);
        };
        child.stdout?.on("data", collect(stdoutChunks));
        child.stderr?.on("data", collect(stderrChunks));
      }
      child.on("error", fail);
      child.on("close", (status, signal) => {
        if (settled) return;
        settled = true;
        const stdout = Buffer.concat(stdoutChunks).toString("utf8");
        const stderr = Buffer.concat(stderrChunks).toString("utf8");
        if (check && status !== 0) {
          const detail = stderr.trim() || stdout.trim();
          reject(new Error(`${command} 执行失败（${status ?? signal}）${detail ? `：${detail}` : ""}`));
          return;
        }
        resolvePromise({ status, signal, stdout, stderr });
      });
    },
  );
}
export function context<T extends ParseArgsOptionsConfig = {}>(options: T = {} as T, allowPositionals = false) {
  const parsed = parseArgs({ options: { root: { type: "string" as const }, ...options }, allowPositionals });
  const git = run("git", ["rev-parse", "--show-toplevel"], { capture: true, check: false });
  const root = realpathSync(
    resolve((parsed.values as { root?: string }).root ?? (git.status === 0 ? git.stdout.trim() : SOURCE_ROOT)),
  );
  return { ...parsed, root, android: join(root, "apps/app") };
}
export function within(root: string, path: string) {
  const name = relative(root, path);
  return name !== ".." && !name.startsWith(`..${sep}`) && !isAbsolute(name);
}
export function walk(root: string): string[] {
  if (!existsSync(root)) return [];
  return readdirSync(root, { withFileTypes: true }).flatMap((entry) => {
    if (IGNORED.has(entry.name)) return [];
    const path = join(root, entry.name);
    return entry.isDirectory() ? walk(path) : entry.isFile() ? [path] : [];
  });
}
export function isMain(url: string) {
  return process.argv[1] && realpathSync(process.argv[1]) === realpathSync(fileURLToPath(url));
}
export async function cli(action: () => unknown) {
  try {
    await action();
  } catch (error) {
    console.error(error instanceof Error ? error.message : String(error));
    process.exitCode = 1;
  }
}
export function report(errors: string[], success: string) {
  if (errors.length) throw new Error(errors.join("\n"));
  console.log(success);
}
export function gitPath(root: string, name: string) {
  return resolve(root, run("git", ["rev-parse", "--git-path", name], { cwd: root, capture: true }).stdout.trim());
}
// 仅用内置能力解析 [versions].ktlint，确保 common.ts 不依赖第三方包，CI 轻量 job 可零安装启动。
export function ktlintVersion(android: string) {
  const content = readFileSync(join(android, "gradle/libs.versions.toml"), "utf8");
  let inVersions = false;
  for (const rawLine of content.split(/\r?\n/)) {
    const line = rawLine.replace(/#.*$/, "").trim();
    if (!line) continue;
    const section = /^\[([^\]]+)\]$/.exec(line);
    if (section) {
      inVersions = section[1].trim() === "versions";
      continue;
    }
    if (!inVersions) continue;
    const match = /^ktlint\s*=\s*"([^"]+)"$/.exec(line);
    if (match) return match[1];
  }
  throw new Error("gradle/libs.versions.toml 的 [versions] 中缺少 ktlint 版本号");
}
