import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const web = fileURLToPath(new URL("../", import.meta.url));
function run(command: string, args: string[]) {
  const result = spawnSync(command, args, { cwd: web, stdio: "inherit" });
  if (result.error) throw result.error;
  if (result.status !== 0) process.exit(result.status ?? 1);
}
run(process.execPath, [fileURLToPath(new URL("./build-kmp.ts", import.meta.url))]);
run("pnpm", ["run", "build:site"]);
