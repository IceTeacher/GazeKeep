import { join } from "node:path";
import { cli, context, run } from "./common.ts";

await cli(() => {
  const { root } = context();
  const directory = join(root, "scripts/repo-tools");
  for (const script of ["check-docs", "check-agent-assets", "check-architecture", "lsposed-repo"])
    run(process.execPath, [join(directory, `scripts/${script}.ts`), "--root", root], {
      cwd: root,
    });
  run(
    join(root, "node_modules/.bin/oxlint"),
    ["--config", join(root, ".oxlintrc.json"), "--deny-warnings", directory],
    {
      cwd: root,
    },
  );
  run(join(root, "node_modules/.bin/tsc"), ["-p", join(directory, "tsconfig.json")], { cwd: root });
  run(process.execPath, ["--test", "tests/*.test.ts"], { cwd: directory });
});
