import { existsSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { cli, context, gitPath, run } from "./common.ts";

await cli(() => {
  const { root } = context();
  const configuration = run("git", ["config", "--get", "core.hooksPath"], {
    cwd: root,
    capture: true,
    check: false,
  });
  if (configuration.status !== 0 && configuration.status !== 1) throw new Error("无法读取 core.hooksPath");
  if (configuration.status === 0)
    throw new Error(`已有 core.hooksPath=${configuration.stdout.trim()}；请先核对原管理器`);
  for (const name of ["pre-commit", "commit-msg"]) {
    const path = gitPath(root, `hooks/${name}`);
    if (
      existsSync(path) &&
      (!statSync(path).isFile() || !readFileSync(path, "utf8").toLowerCase().includes("lefthook"))
    )
      throw new Error(`已有 ${name} Hook；保留原文件，请先核对集成方式`);
  }
  run(join(root, "node_modules/.bin/lefthook"), ["install", "pre-commit", "commit-msg"], {
    cwd: root,
  });
});
