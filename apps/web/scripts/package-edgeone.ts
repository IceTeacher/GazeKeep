/** 把已完成完整构建的 dist/ 与 edge-functions/ 打包为 .edgeone/，供 `edgeone makers deploy .edgeone` 直传。 */
import { spawnSync } from "node:child_process";
import { existsSync, readFileSync, rmSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { EDGEONE_BUILD_ARGS, assertEdgeoneRoutes, edgeoneChildEnv, findLeakedEnv } from "./edgeone.ts";

const web = fileURLToPath(new URL("../", import.meta.url));
const at = (path: string) => fileURLToPath(new URL(path, new URL("../", import.meta.url)));
const cli = at("node_modules/.bin/edgeone");

if (!existsSync(at("dist/zh/index.html"))) throw new Error("dist/ 缺少站点页面，请先运行 pnpm --filter web build");
// 边缘函数构建可能读取 .env/.env.local 并写入包内；存在时直接拒绝，避免本地变量随包上传。
for (const file of [".env", ".env.local"])
  if (existsSync(at(file))) throw new Error(`${file} 会被写入边缘函数包，打包前请移走`);

const env = edgeoneChildEnv(process.env);
rmSync(at(".edgeone"), { recursive: true, force: true });
for (const args of [EDGEONE_BUILD_ARGS, ["makers", "generate-routes"]]) {
  const result = spawnSync(cli, args, { cwd: web, stdio: "inherit", env });
  if (result.error) throw result.error;
  if (result.status !== 0) process.exit(result.status ?? 1);
}

assertEdgeoneRoutes(JSON.parse(readFileSync(at(".edgeone/routes.json"), "utf8")));
if (!existsSync(at(".edgeone/assets/zh/index.html"))) throw new Error(".edgeone/assets 缺少站点页面");
const leaked = findLeakedEnv(readFileSync(at(".edgeone/edge-functions/index.js"), "utf8"), process.env, env);
if (leaked.length) {
  rmSync(at(".edgeone"), { recursive: true, force: true });
  throw new Error(`边缘函数包含有环境变量的值，已删除 .edgeone：${leaked.join(", ")}`);
}
console.log("EdgeOne 上传目录已生成：.edgeone/");
