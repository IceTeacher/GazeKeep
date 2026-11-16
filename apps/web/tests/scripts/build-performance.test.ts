import { expect, test } from "vitest";
import { readdir, readFile, stat } from "node:fs/promises";
import { pruneShaderRegistry, injectModulePreload, selectFirstFramePreloads } from "../../scripts/build-performance.ts";

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
  expect(pruned).toMatch(/CursorTrail: componentDefinition\$41/);
  expect(pruned).not.toMatch(/AngularBlur:|ZoomBlur:/);
});

test("shader 库结构变化或缺少组件时构建失败而非静默打包全量", () => {
  expect(() => pruneShaderRegistry("const other = {};", ["CursorTrail"])).toThrow(/shaderDefinitions/);
  expect(() => pruneShaderRegistry(registry, ["CursorTrail", "Prism"])).toThrow(/Prism/);
});

test("Three.js 分块以 modulepreload 提前请求，只注入一次且不影响无脚本页面", () => {
  const page = `<html><head><title>x</title></head><body><script type="module" src="/_astro/Website.js"></script></body></html>`;
  const once = injectModulePreload(page, ["/_astro/three.module.abc.js"]);
  expect(once).toMatch(/<link rel="modulepreload" href="\/_astro\/three\.module\.abc\.js"><\/head>/);
  expect(injectModulePreload(once, ["/_astro/three.module.abc.js"])).toBe(once);
  const redirect = `<html><head><meta http-equiv="refresh" content="0;url=/zh/"></head><body></body></html>`;
  expect(injectModulePreload(redirect, ["/_astro/three.module.abc.js"])).toBe(redirect);
});

test("Three.js 与站点入口分块会作为首帧并行预加载候选被选出", () => {
  const assets = ["Website.astro_astro_type_script_index_0_lang.abc.js", "three.module.xyz.js", "js.other.js"];
  const page = `<script type="module" src="/_astro/Website.astro_astro_type_script_index_0_lang.abc.js"></script>`;
  expect(selectFirstFramePreloads(assets, page)).toEqual([
    "/_astro/Website.astro_astro_type_script_index_0_lang.abc.js",
    "/_astro/three.module.xyz.js",
  ]);
  expect(
    selectFirstFramePreloads(assets, `<script type="module" src="/_astro/ChangelogPage.def.js"></script>`),
  ).toEqual(["/_astro/three.module.xyz.js"]);
});

test("页面内图标与 favicon 使用小尺寸资源", async () => {
  const src = new URL("../../src/", import.meta.url);
  const files = (await readdir(src, { recursive: true })).filter((file) => file.endsWith(".astro"));
  const page = (await Promise.all(files.map((file) => readFile(new URL(file, src), "utf8")))).join("\n");
  const sources = [
    ...[...page.matchAll(/<img src="(\/[^"]+)"/g)].map(([, src]) => src),
    ...[...page.matchAll(/<link rel="icon" href="(\/[^"]+)"/g)].map(([, src]) => src),
  ];
  expect(sources.length).toBeGreaterThanOrEqual(4);
  for (const src of new Set(sources)) {
    const { size } = await stat(new URL(`../../public${src}`, import.meta.url));
    expect(size, `${src} ${size} B`).toBeLessThanOrEqual(16 * 1024);
  }
});

test("主页复用并预加载 KMP 子集字体，与预渲染样式 @font-face 同源同属性避免重复下载", async () => {
  const layout = await readFile(new URL("../../src/layouts/BaseLayout.astro", import.meta.url), "utf8");
  const css = await readFile(new URL("../../src/styles/sections/showcase-preview.css", import.meta.url), "utf8");
  const expectedPath = "/kmp-showcase/wasm/composeResources/gazekeep.shared.generated.resources/files/showcase_cjk.otf";
  expect(layout).toContain(`page === "home"`);
  expect(layout).toContain(`rel="preload"`);
  expect(layout).toContain(`as="font"`);
  expect(layout).toContain(`crossorigin="anonymous"`);
  expect(layout).toContain(expectedPath);
  expect(css).toContain(expectedPath);
  expect(css).toContain(`font-display: swap`);
});
