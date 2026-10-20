import { existsSync, readFileSync, readdirSync, realpathSync } from "node:fs";
import { isAbsolute, join, relative, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";
import { parseArgs, type ParseArgsOptionsConfig } from "node:util";
import { parse } from "smol-toml";

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
export function ktlintVersion(android: string) {
  const catalog = parse(readFileSync(join(android, "gradle/libs.versions.toml"), "utf8")) as {
    versions: Record<string, string>;
  };
  return catalog.versions.ktlint;
}
