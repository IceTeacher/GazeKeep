import { randomBytes } from "node:crypto";
import { realpathSync } from "node:fs";
import { mkdir, readFile, rename, rm, writeFile } from "node:fs/promises";
import { dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { assertPrivacyInPolicy, extractPrivacy } from "./privacy.ts";

export interface SyncPrivacyOptions {
  resourcesRoot?: URL;
  policyUrl?: URL;
  outputUrl?: URL;
}

const DEFAULT_RESOURCES_ROOT = new URL("../../app/shared/src/commonMain/composeResources/", import.meta.url);
const DEFAULT_POLICY_URL = new URL("../../../docs/product.md", import.meta.url);
const DEFAULT_OUTPUT_URL = new URL("../src/data/privacy.generated.json", import.meta.url);

export async function syncPrivacyFile({
  resourcesRoot = DEFAULT_RESOURCES_ROOT,
  policyUrl = DEFAULT_POLICY_URL,
  outputUrl = DEFAULT_OUTPUT_URL,
}: SyncPrivacyOptions = {}): Promise<{ written: boolean }> {
  const [zhXml, enXml, policy] = await Promise.all([
    readFile(new URL("values/strings.xml", resourcesRoot), "utf8"),
    readFile(new URL("values-en/strings.xml", resourcesRoot), "utf8"),
    readFile(policyUrl, "utf8"),
  ]);
  const zh = extractPrivacy(zhXml);
  const en = extractPrivacy(enXml);
  assertPrivacyInPolicy(policy, zh, en);

  const next = JSON.stringify({ zh, en }, null, 2) + "\n";
  const outputPath = fileURLToPath(outputUrl);
  try {
    const current = await readFile(outputPath, "utf8");
    if (current === next) return { written: false };
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code !== "ENOENT") throw error;
  }

  await mkdir(dirname(outputPath), { recursive: true });
  // 先写同目录唯一临时文件再原子重命名，避免并行进程读取时遇到正在截断覆写的不完整 JSON。
  const tempPath = `${outputPath}.${process.pid}.${randomBytes(6).toString("hex")}.tmp`;
  try {
    await writeFile(tempPath, next, "utf8");
    await rename(tempPath, outputPath);
  } catch (error) {
    await rm(tempPath, { force: true }).catch(() => {});
    throw error;
  }
  return { written: true };
}

function isMainEntry(url: string) {
  return Boolean(process.argv[1]) && realpathSync(process.argv[1]) === realpathSync(fileURLToPath(url));
}

if (isMainEntry(import.meta.url)) {
  await syncPrivacyFile();
}
