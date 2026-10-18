import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const root = new URL("../../app/shared/src/commonMain/composeResources/", import.meta.url);
const strings = async (path) => {
  const xml = await readFile(new URL(path, root), "utf8");
  const entries = [...xml.matchAll(/<string\b[^>]*\bname="([^"]+)"[^>]*>([\s\S]*?)<\/string>/g)];
  const map = new Map(entries.map(([, key, value]) => [key, value]));
  assert.equal(map.size, entries.length, `${path} 存在重复字符串键`);
  return map;
};

test("英文页面资源覆盖默认资源且格式占位符一致", async () => {
  const zh = await strings("values/strings.xml");
  const en = await strings("values-en/strings.xml");
  assert.deepEqual([...en.keys()].sort(), [...zh.keys()].sort());
  for (const [key, value] of zh) {
    const placeholders = (text) => [...text.matchAll(/%\d+\$[ds]/g)].map(([match]) => match).sort();
    assert.deepEqual(placeholders(en.get(key)), placeholders(value), key);
  }
});
