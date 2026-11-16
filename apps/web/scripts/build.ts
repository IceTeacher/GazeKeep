import { spawnSync } from "node:child_process";
import { access, cp, mkdir, readdir, readFile, rename, rm, writeFile } from "node:fs/promises";
import { relative } from "node:path";
import { fileURLToPath } from "node:url";
import { assertFixedPublicPath } from "./kmp-showcase.ts";
import {
  buildShowcasePreloads,
  findReferencedWasm,
  hashedAssetName,
  renderShowcaseIndex,
  shouldCopyShowcaseAsset,
} from "./kmp-preload.ts";

async function exists(url: URL): Promise<boolean> {
  try {
    await access(url);
    return true;
  } catch {
    return false;
  }
}
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
  const sourceFiles = await readdir(source);
  const entry =
    sourceFiles.find((name) => name === "GazeKeep-shared.mjs") ??
    sourceFiles.find((name) => name === "GazeKeep-shared.js") ??
    sourceFiles.find((name) => name === "shared.js");
  if (!entry) throw new Error(`找不到 ${target} 浏览器入口：${sourceFiles.join(", ")}`);
  const entryCode = await readFile(`${source}${entry}`, "utf8");
  assertFixedPublicPath(entryCode, target);
  const wasmFiles = findReferencedWasm(entryCode, sourceFiles);
  await rm(output, { recursive: true, force: true });
  await mkdir(output, { recursive: true });
  await cp(source, output, {
    recursive: true,
    filter: (item) => {
      const rel = relative(source, item);
      return rel ? shouldCopyShowcaseAsset(rel, entryCode, wasmFiles) : true;
    },
  });
  // 对未带内容哈希的入口脚本附加内容指纹，使 CDN 与浏览器可长期强缓存并跳过重复协商。
  const hashedEntry = hashedAssetName(entry, entryCode);
  await rename(new URL(entry, output), new URL(hashedEntry, output));
  const [hasFont, hasStrings] = await Promise.all([
    exists(new URL("composeResources/gazekeep.shared.generated.resources/files/showcase_cjk.otf", output)),
    exists(new URL("composeResources/gazekeep.shared.generated.resources/values/strings.commonMain.cvr", output)),
  ]);
  const preloads = buildShowcasePreloads({ entry: hashedEntry, wasmFiles, hasFont, hasStrings });
  const html = await readFile(`${app}/shared/src/showcaseMain/resources/index.html`, "utf8");
  await writeFile(new URL("index.html", output), renderShowcaseIndex(html, preloads, hashedEntry));
}
run("pnpm", ["run", "build:site"], web);
