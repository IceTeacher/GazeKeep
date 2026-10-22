import { spawnSync } from "node:child_process";
import { cp, mkdir, rm, access, readdir, readFile, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { assertFixedPublicPath } from "./kmp-showcase.ts";
const web = fileURLToPath(new URL("../", import.meta.url));
const app = fileURLToPath(new URL("../../app/", import.meta.url));
function run(command: string, args: string[], cwd: string) {
  // Kotlin 的生成目录使用自己的 Yarn；仅跳过其对外层 pnpm workspace 的 Corepack 检查。
  const env = command === "./gradlew" ? { ...process.env, SKIP_YARN_COREPACK_CHECK: "1" } : process.env;
  const result = spawnSync(command, args, { cwd, stdio: "inherit", env });
  if (result.error) throw result.error;
  if (result.status !== 0) process.exit(result.status ?? 1);
}
run("./gradlew", [":shared:wasmJsBrowserDistribution", ":shared:jsBrowserDistribution", "--console=plain"], app);
for (const [target, folder] of [
  ["wasmJs", "wasm"],
  ["js", "js"],
]) {
  const output = new URL(`../public/kmp-showcase/${folder}/`, import.meta.url);
  const source = `${app}/shared/build/dist/${target}/productionExecutable/`;
  await access(source);
  await rm(output, { recursive: true, force: true });
  await mkdir(output, { recursive: true });
  await cp(source, output, { recursive: true, filter: (path) => !path.endsWith(".map") });
  const files = await readdir(output);
  const entry =
    files.find((name) => name === "GazeKeep-shared.mjs") ??
    files.find((name) => name === "GazeKeep-shared.js") ??
    files.find((name) => name === "shared.js");
  if (!entry) throw new Error(`找不到 ${target} 浏览器入口：${files.join(", ")}`);
  assertFixedPublicPath(await readFile(new URL(entry, output), "utf8"), target);
  const html = await readFile(`${app}/shared/src/showcaseMain/resources/index.html`, "utf8");
  await writeFile(
    new URL("index.html", output),
    html.replace("<!--SHOWCASE_ENTRY-->", `<script type="module" src="./${entry}"></script>`),
  );
}
run("pnpm", ["run", "build:site"], web);
