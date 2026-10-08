import { join } from "node:path";
import { cli, context, run } from "./common.mjs";

await cli(() => {
  const { root } = context();
  const directory = join(root, "scripts/repo-tools");
  for (const script of ["check-docs", "check-agent-assets", "check-architecture", "check-syntax"])
    run(process.execPath, [join(directory, `scripts/${script}.mjs`), "--root", root], {
      cwd: root,
    });
  run(process.execPath, ["--test", "tests/*.test.mjs"], { cwd: directory });
});
