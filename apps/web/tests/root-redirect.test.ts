import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

test("根路径由 Cloudflare Pages 服务端 302 跳转到中文站", async () => {
  const rules = (await readFile(new URL("../public/_redirects", import.meta.url), "utf8"))
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith("#"))
    .map((line) => line.split(/\s+/));
  assert.deepEqual(
    rules.find(([from]) => from === "/"),
    ["/", "/zh/", "302"],
  );
});
