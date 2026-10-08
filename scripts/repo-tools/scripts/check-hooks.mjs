import { existsSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { parse } from "smol-toml";
import { cli, context, gitPath, run } from "./common.mjs";

await cli(() => {
  const { root, android } = context();
  for (const name of ["pre-commit", "commit-msg"]) {
    const path = gitPath(root, `hooks/${name}`);
    if (!existsSync(path) || !statSync(path).isFile() || !readFileSync(path, "utf8").toLowerCase().includes("lefthook"))
      throw new Error(`${name} 未安装 Lefthook；运行 pnpm hooks:install`);
    if (!readFileSync(path, "utf8").includes('./node_modules/.bin/lefthook "$@"'))
      throw new Error(`${name} 未绑定本地锁定版本；重新运行 pnpm hooks:install`);
    if (!(statSync(path).mode & 0o111)) throw new Error(`${name} 缺少执行权限`);
  }
  const expected = JSON.parse(readFileSync(join(root, "package.json"), "utf8")).devDependencies.lefthook;
  const actual = run(join(root, "node_modules/.bin/lefthook"), ["version"], {
    cwd: root,
    capture: true,
  }).stdout.trim();
  if (actual !== expected) throw new Error(`Lefthook 版本不符：需要 ${expected}，实际 ${actual}`);
  const version = parse(readFileSync(join(android, "gradle/libs.versions.toml"), "utf8")).versions.ktlint;
  for (const name of [
    `.cache/dev-tools/ktlint-${version}.jar`,
    "node_modules/.bin/commitlint",
    "node_modules/.bin/prettier",
  ])
    if (!existsSync(join(root, name))) throw new Error(`缺少本地工具：${name}；运行 pnpm install 和 pnpm tools:setup`);
  console.log("Lefthook 入口、版本与本地工具检查通过");
});
