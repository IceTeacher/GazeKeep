import { spawnSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";

// 截图基线依赖字体与渲染栈：在与锁定 Playwright 同版本的官方镜像中运行，本机与 CI 使用同一套字体和 Chromium。
const require = createRequire(import.meta.url);
const { version } = JSON.parse(readFileSync(require.resolve("playwright/package.json"), "utf8")) as { version: string };
const root = fileURLToPath(new URL("../../../", import.meta.url));
const web = fileURLToPath(new URL("../", import.meta.url));
const user = `${process.getuid?.() ?? 0}:${process.getgid?.() ?? 0}`;
// pnpm 的 bin 包装与依赖链接引用绝对路径，因此按宿主机原路径挂载；以当前用户运行，避免生成 root 所有的缓存与失败截图。
const args = [
  "run",
  "--rm",
  "--init",
  "--ipc=host",
  "--user",
  user,
  "--env",
  "HOME=/tmp",
  "--env",
  "CI",
  "--volume",
  `${root}:${root}`,
  "--workdir",
  web,
  `mcr.microsoft.com/playwright:v${version}-noble`,
  "node_modules/.bin/vitest",
  "run",
  "--project",
  "browser",
  ...process.argv.slice(2),
];
const result = spawnSync("docker", args, { stdio: "inherit" });
if (result.error) throw result.error;
process.exit(result.status ?? 1);
