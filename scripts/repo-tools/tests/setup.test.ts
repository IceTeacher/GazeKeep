import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { installKtlint } from "../scripts/setup-dev-tools.ts";

test("摘要不匹配与 HTTP 失败保留已安装工具，成功后原子替换", async (t) => {
  const root = mkdtempSync(join(tmpdir(), "gazekeep-download-"));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  const destination = join(root, "ktlint.jar");
  writeFileSync(destination, "旧工具");
  const content = Buffer.from("新工具");
  const checksum = createHash("sha256").update(content).digest("hex");
  const fetcher = (address: string) =>
    Promise.resolve(new Response(address.endsWith(".sha256") ? checksum : "错误文件"));
  await assert.rejects(installKtlint({ destination, url: "https://example.invalid/tool.jar", fetcher }), /摘要不匹配/);
  assert.equal(readFileSync(destination, "utf8"), "旧工具");
  await assert.rejects(
    installKtlint({
      destination,
      url: "https://example.invalid/tool.jar",
      retryDelayMs: 0,
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

test("主源限流或 404 时按序重试并回退镜像源，全失败保留旧文件", async (t) => {
  const root = mkdtempSync(join(tmpdir(), "gazekeep-download-fallback-"));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  const destination = join(root, "ktlint.jar");
  writeFileSync(destination, "旧工具");
  const content = Buffer.from("镜像工具");
  const checksum = createHash("sha256").update(content).digest("hex");

  // 1) 主源持续 429，回退镜像源成功下载并校验通过。
  const attempted: string[] = [];
  await installKtlint({
    destination,
    urls: ["https://primary.invalid/ktlint.jar", "https://mirror.invalid/ktlint.jar"],
    retries: 1,
    retryDelayMs: 0,
    fetcher: async (address) => {
      attempted.push(address);
      if (address.startsWith("https://primary.invalid/")) return new Response("", { status: 429 });
      return new Response(address.endsWith(".sha256") ? checksum : content);
    },
  });
  assert.deepEqual(readFileSync(destination), content);
  assert.deepEqual(attempted, [
    "https://primary.invalid/ktlint.jar.sha256",
    "https://primary.invalid/ktlint.jar.sha256",
    "https://mirror.invalid/ktlint.jar.sha256",
    "https://mirror.invalid/ktlint.jar",
  ]);

  // 2) 主源返回 404 时立即回退，不无谓重试 404。
  const content2 = Buffer.from("镜像工具v2");
  const checksum2 = createHash("sha256").update(content2).digest("hex");
  const attempted404: string[] = [];
  await installKtlint({
    destination,
    urls: ["https://primary.invalid/ktlint.jar", "https://mirror.invalid/ktlint.jar"],
    retries: 2,
    retryDelayMs: 0,
    fetcher: async (address) => {
      attempted404.push(address);
      if (address.startsWith("https://primary.invalid/")) return new Response("", { status: 404 });
      return new Response(address.endsWith(".sha256") ? checksum2 : content2);
    },
  });
  assert.deepEqual(readFileSync(destination), content2);
  assert.deepEqual(attempted404, [
    "https://primary.invalid/ktlint.jar.sha256",
    "https://mirror.invalid/ktlint.jar.sha256",
    "https://mirror.invalid/ktlint.jar",
  ]);

  // 3) 所有源都失败时报错并保留既有工具文件。
  await assert.rejects(
    installKtlint({
      destination,
      urls: ["https://primary.invalid/ktlint.jar", "https://mirror.invalid/ktlint.jar"],
      retries: 1,
      retryDelayMs: 0,
      fetcher: async () => new Response("", { status: 429 }),
    }),
    /HTTP 429/,
  );
  assert.deepEqual(readFileSync(destination), content2);
});
