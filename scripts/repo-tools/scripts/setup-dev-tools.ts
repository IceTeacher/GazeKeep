import { createHash, randomUUID } from "node:crypto";
import { existsSync, mkdirSync, readFileSync, renameSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { cli, context, isMain, ktlintVersion, run } from "./common.ts";

const digest = (data: string | Buffer) => createHash("sha256").update(data).digest("hex");
// 校验完成才替换旧工具；下载失败不能破坏已有可用文件。
export async function installKtlint({
  destination,
  url,
  fetcher = fetch,
}: {
  destination: string;
  url: string;
  fetcher?: (address: string, init?: RequestInit) => Promise<Response>;
}) {
  async function request(address: string) {
    const response = await fetcher(address, { signal: AbortSignal.timeout(60_000) });
    if (!response.ok) throw new Error(`ktlint 下载失败：HTTP ${response.status}`);
    return response;
  }
  const checksum = (await (await request(`${url}.sha256`)).text()).trim().split(/\s+/)[0].toLowerCase();
  if (!/^[a-f0-9]{64}$/.test(checksum)) throw new Error("ktlint 下载摘要格式非法");
  if (existsSync(destination) && digest(readFileSync(destination)) === checksum) return;
  const data = Buffer.from(await (await request(url)).arrayBuffer());
  if (digest(data) !== checksum) throw new Error("ktlint 下载摘要不匹配");
  mkdirSync(dirname(destination), { recursive: true });
  const temporary = `${destination}.${randomUUID()}.tmp`;
  try {
    writeFileSync(temporary, data, { flag: "wx" });
    renameSync(temporary, destination);
  } finally {
    rmSync(temporary, { force: true });
  }
}
if (isMain(import.meta.url))
  await cli(async () => {
    const { root, android } = context();
    const version = ktlintVersion(android);
    const destination = join(root, ".cache/dev-tools", `ktlint-${version}.jar`);
    await installKtlint({
      destination,
      url: `https://repo.maven.apache.org/maven2/com/pinterest/ktlint/ktlint-cli/${version}/ktlint-cli-${version}-all.jar`,
    });
    run("java", ["-jar", destination, "--version"], { cwd: root });
    console.log("本地工具准备完成；使用 pnpm hooks:install 显式启用 Hook");
  });
