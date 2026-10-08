import { existsSync, readdirSync, realpathSync } from "node:fs";
import { isAbsolute, join, relative, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";
import { parseArgs } from "node:util";

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
export function run(
  command,
  args,
  { cwd = process.cwd(), capture = false, env = process.env, check = true } = {},
) {
  const result = spawnSync(command, args, {
    cwd,
    env,
    encoding: "utf8",
    stdio: capture ? "pipe" : "inherit",
    maxBuffer: 32 * 1024 * 1024,
  });
  if (result.error) throw result.error;
  if (check && result.status !== 0)
    throw new Error(
      `${command} 执行失败（${result.status ?? result.signal}）${capture ? `：${result.stderr?.trim()}` : ""}`,
    );
  return result;
}
export function context(options = {}, allowPositionals = false) {
  const parsed = parseArgs({ options: { root: { type: "string" }, ...options }, allowPositionals });
  const git = run("git", ["rev-parse", "--show-toplevel"], { capture: true, check: false });
  const root = realpathSync(
    resolve(parsed.values.root ?? (git.status === 0 ? git.stdout.trim() : SOURCE_ROOT)),
  );
  return { ...parsed, root, android: join(root, "apps/app") };
}
export function within(root, path) {
  const name = relative(root, path);
  return name !== ".." && !name.startsWith(`..${sep}`) && !isAbsolute(name);
}
export function walk(root) {
  if (!existsSync(root)) return [];
  return readdirSync(root, { withFileTypes: true }).flatMap((entry) => {
    if (IGNORED.has(entry.name)) return [];
    const path = join(root, entry.name);
    return entry.isDirectory() ? walk(path) : entry.isFile() ? [path] : [];
  });
}
export function isMain(url) {
  return process.argv[1] && realpathSync(process.argv[1]) === realpathSync(fileURLToPath(url));
}
export async function cli(action) {
  try {
    await action();
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
export function report(errors, success) {
  if (errors.length) throw new Error(errors.join("\n"));
  console.log(success);
}
export function gitPath(root, name) {
  return resolve(
    root,
    run("git", ["rev-parse", "--git-path", name], { cwd: root, capture: true }).stdout.trim(),
  );
}
