import { createHash, randomUUID } from "node:crypto";
import { existsSync, mkdirSync, readFileSync, renameSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { setTimeout as delay } from "node:timers/promises";
import { cli, context, isMain, ktlintVersion, run } from "./common.ts";

const digest = (data: string | Buffer) => createHash("sha256").update(data).digest("hex");
const isRetryableStatus = (status: number) => status === 408 || status === 429 || status >= 500;

class TransferError extends Error {}

// 校验完成才替换旧工具；下载失败不能破坏已有可用文件。
export async function installKtlint({
  destination,
  url,
  urls = url ? [url] : [],
  fetcher = fetch,
  retries = 2,
  retryDelayMs = 1_000,
}: {
  destination: string;
  url?: string;
  urls?: readonly string[];
  fetcher?: (address: string, init?: RequestInit) => Promise<Response>;
  retries?: number;
  retryDelayMs?: number;
}) {
  if (!urls.length) throw new Error("ktlint 下载地址为空");

  async function request(address: string) {
    let lastError: unknown;
    for (let attempt = 0; attempt <= retries; attempt++) {
      try {
        const response = await fetcher(address, { signal: AbortSignal.timeout(60_000) });
        if (response.ok) return response;
        const error = new TransferError(`ktlint 下载失败：HTTP ${response.status}`);
        if (!isRetryableStatus(response.status) || attempt === retries) throw error;
        lastError = error;
      } catch (error) {
        if (error instanceof TransferError && !/HTTP (408|429|5\d\d)$/.test(error.message)) throw error;
        lastError = error instanceof TransferError ? error : new TransferError(String(error));
        if (attempt === retries) throw lastError;
      }
      if (retryDelayMs > 0) await delay(retryDelayMs * (attempt + 1));
    }
    throw lastError;
  }

  let lastTransferError: unknown;
  for (const candidate of urls) {
    let checksum: string;
    try {
      checksum = (await (await request(`${candidate}.sha256`)).text()).trim().split(/\s+/)[0].toLowerCase();
    } catch (error) {
      if (error instanceof TransferError) {
        lastTransferError = error;
        continue;
      }
      throw error;
    }
    if (!/^[a-f0-9]{64}$/.test(checksum)) throw new Error("ktlint 下载摘要格式非法");
    if (existsSync(destination) && digest(readFileSync(destination)) === checksum) return;
    let data: Buffer;
    try {
      data = Buffer.from(await (await request(candidate)).arrayBuffer());
    } catch (error) {
      if (error instanceof TransferError) {
        lastTransferError = error;
        continue;
      }
      throw error;
    }
    if (digest(data) !== checksum) throw new Error("ktlint 下载摘要不匹配");
    mkdirSync(dirname(destination), { recursive: true });
    const temporary = `${destination}.${randomUUID()}.tmp`;
    try {
      writeFileSync(temporary, data, { flag: "wx" });
      renameSync(temporary, destination);
    } finally {
      rmSync(temporary, { force: true });
    }
    return;
  }
  throw lastTransferError ?? new Error("ktlint 下载失败");
}

if (isMain(import.meta.url))
  await cli(async () => {
    const { root, android } = context();
    const version = ktlintVersion(android);
    const destination = join(root, ".cache/dev-tools", `ktlint-${version}.jar`);
    const artifact = `com/pinterest/ktlint/ktlint-cli/${version}/ktlint-cli-${version}-all.jar`;
    await installKtlint({
      destination,
      urls: [
        `https://repo.maven.apache.org/maven2/${artifact}`,
        `https://maven-central.storage-download.googleapis.com/maven2/${artifact}`,
        `https://repo1.maven.org/maven2/${artifact}`,
      ],
    });
    run("java", ["-jar", destination, "--version"], { cwd: root });
    console.log("本地工具准备完成；使用 pnpm hooks:install 显式启用 Hook");
  });
