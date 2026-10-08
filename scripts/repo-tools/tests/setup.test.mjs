import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { installKtlint } from "../scripts/setup-dev-tools.mjs";

test("摘要不匹配与 HTTP 失败保留已安装工具，成功后原子替换", async (t) => {
  const root = mkdtempSync(join(tmpdir(), "gazekeep-download-"));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  const destination = join(root, "ktlint.jar");
  writeFileSync(destination, "旧工具");
  const content = Buffer.from("新工具");
  const checksum = createHash("sha256").update(content).digest("hex");
  const fetcher = (address) =>
    Promise.resolve(new Response(address.endsWith(".sha256") ? checksum : "错误文件"));
  await assert.rejects(
    installKtlint({ destination, url: "https://example.invalid/tool.jar", fetcher }),
    /摘要不匹配/,
  );
  assert.equal(readFileSync(destination, "utf8"), "旧工具");
  await assert.rejects(
    installKtlint({
      destination,
      url: "https://example.invalid/tool.jar",
      fetcher: async () => new Response("", { status: 503 }),
    }),
    /HTTP 503/,
  );
  assert.equal(readFileSync(destination, "utf8"), "旧工具");
  await installKtlint({
    destination,
    url: "https://example.invalid/tool.jar",
    fetcher: async (address) => new Response(address.endsWith(".sha256") ? checksum : content),
  });
  assert.deepEqual(readFileSync(destination), content);
});
