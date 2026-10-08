import { join } from "node:path";
import { cli, context, run, walk } from "./common.mjs";

await cli(() => {
  const { root } = context();
  for (const file of walk(join(root, "scripts/repo-tools")).filter((n) =>
    /\.(mjs|cjs|js)$/.test(n),
  ))
    run(process.execPath, ["--check", file], { cwd: root });
  console.log("仓库 Node 脚本语法检查通过");
});
