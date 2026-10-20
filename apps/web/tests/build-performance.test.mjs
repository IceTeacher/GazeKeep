import test from "node:test";
import assert from "node:assert/strict";
import { readFile, stat } from "node:fs/promises";
import { HERO_SHADER_COMPONENTS, pruneShaderRegistry, injectModulePreload } from "../scripts/build-performance.mjs";

const registry = `import { n as componentDefinition } from "./AngularBlur-x.js";
import { n as componentDefinition$41 } from "./CursorTrail-x.js";
var createShaderRegistry = () => {
\tconst registry = {};
\tconst shaderDefinitions = {
\t\tAngularBlur: componentDefinition,
\t\tCursorTrail: componentDefinition$41,
\t\tZoomBlur: componentDefinition$200
\t};
\treturn registry;
};`;

test("首屏 shader 注册表只保留页面用到的组件", () => {
  const pruned = pruneShaderRegistry(registry, ["CursorTrail"]);
  assert.match(pruned, /CursorTrail: componentDefinition\$41/);
  assert.doesNotMatch(pruned, /AngularBlur:|ZoomBlur:/);
});

test("shader 库结构变化或缺少组件时构建失败而非静默打包全量", () => {
  assert.throws(() => pruneShaderRegistry("const other = {};", ["CursorTrail"]), /shaderDefinitions/);
  assert.throws(() => pruneShaderRegistry(registry, ["CursorTrail", "Prism"]), /Prism/);
});

test("保留列表与首屏实际创建的 shader 组件一致", async () => {
  const source = await readFile(new URL("../src/scripts/orchestrator.ts", import.meta.url), "utf8");
  const used = [...source.matchAll(/\btype: "([A-Z]\w+)"/g)].map(([, name]) => name);
  assert.deepEqual([...new Set(used)].sort(), [...HERO_SHADER_COMPONENTS].sort());
});

test("Three.js 分块以 modulepreload 提前请求，只注入一次且不影响无脚本页面", () => {
  const page = `<html><head><title>x</title></head><body><script type="module" src="/_astro/Website.js"></script></body></html>`;
  const once = injectModulePreload(page, ["/_astro/three.module.abc.js"]);
  assert.match(once, /<link rel="modulepreload" href="\/_astro\/three\.module\.abc\.js"><\/head>/);
  assert.equal(injectModulePreload(once, ["/_astro/three.module.abc.js"]), once);
  const redirect = `<html><head><meta http-equiv="refresh" content="0;url=/zh/"></head><body></body></html>`;
  assert.equal(injectModulePreload(redirect, ["/_astro/three.module.abc.js"]), redirect);
});

test("页面内图标与 favicon 使用小尺寸资源", async () => {
  const page = await readFile(new URL("../src/components/Website.astro", import.meta.url), "utf8");
  const sources = [
    ...[...page.matchAll(/<img src="(\/[^"]+)"/g)].map(([, src]) => src),
    ...[...page.matchAll(/<link rel="icon" href="(\/[^"]+)"/g)].map(([, src]) => src),
  ];
  assert.ok(sources.length >= 4);
  for (const src of new Set(sources)) {
    const { size } = await stat(new URL(`../public${src}`, import.meta.url));
    assert.ok(size <= 16 * 1024, `${src} ${size} B`);
  }
});
