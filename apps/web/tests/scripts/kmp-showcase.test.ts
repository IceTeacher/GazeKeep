import { readFileSync } from "node:fs";
import vm from "node:vm";
import { expect, test } from "vitest";
import { assertFixedPublicPath } from "../../scripts/kmp-showcase.ts";
import {
  buildShowcasePreloads,
  findReferencedWasm,
  hashedAssetName,
  renderShowcaseIndex,
  shouldCopyShowcaseAsset,
} from "../../scripts/kmp-preload.ts";

test("KMP 入口使用自动 publicPath 时构建失败，避免托管方注入的页尾脚本改写 wasm 地址", () => {
  // 摘自 webpack 自动 publicPath 运行时：取不到 currentScript 时回退到页面最后一个 http(s) 脚本。
  const auto = `const a=e.getElementsByTagName("script");if(!_)throw new Error("Automatic publicPath is not supported in this browser");`;
  expect(() => assertFixedPublicPath(auto, "wasmJs")).toThrow(/wasmJs.*publicPath/);
  expect(() => assertFixedPublicPath(`a.p="./",a.b=document.baseURI`, "js")).not.toThrow();
});

test("展示包构建对入口脚本生成稳定指纹、仅预加载被引用 wasm 并按语言预加载词条表", () => {
  const hashed = hashedAssetName("shared.js", "console.log(1)");
  expect(hashed).toMatch(/^shared\.[a-f0-9]{8}\.js$/);
  expect(hashedAssetName("shared.js", "console.log(1)")).toBe(hashed);
  expect(hashedAssetName("shared.js", "console.log(2)")).not.toBe(hashed);

  const webpackJs = `e.exports=n.p+"bfa5198.wasm";locateFile("skiko.wasm")`;
  expect(findReferencedWasm(webpackJs, ["bfa5198.wasm", "skiko.wasm"])).toEqual(["bfa5198.wasm"]);

  const wasmFiles = findReferencedWasm(`load("./app.wasm");load("./skiko-abc.wasm")`, [
    "app.wasm",
    "skiko-abc.wasm",
    "skiko.wasm",
  ]);
  expect(wasmFiles).toEqual(["app.wasm", "skiko-abc.wasm"]);

  const preloads = buildShowcasePreloads({
    entry: hashed,
    wasmFiles,
    hasFont: true,
    hasStrings: true,
  });
  expect(preloads).toContain(`<link rel="modulepreload" href="./${hashed}" />`);
  expect(preloads).toContain(`<link rel="preload" as="fetch" crossorigin="anonymous" href="./app.wasm" />`);
  expect(preloads).toContain("showcase_cjk.otf");
  expect(preloads).toContain("strings.commonMain.cvr");

  const rendered = renderShowcaseIndex(
    "<html><head><!--SHOWCASE_PRELOAD--></head><body><!--SHOWCASE_ENTRY--></body></html>",
    preloads,
    hashed,
  );
  expect(rendered).toContain(`<script type="module" src="./${hashed}"></script>`);
  expect(() => renderShowcaseIndex("<html><!--SHOWCASE_ENTRY--></html>", preloads, hashed)).toThrow(/SHOWCASE_PRELOAD/);
});

test("展示资源复制只保留运行时实际引用的 wasm/mjs，剔除 source map、重复 skiko.wasm 与未用图标", () => {
  const entry = `const s="file:///.../kotlin/skiko.mjs";e.exports=n.p+"bfa5198.wasm";`;
  const referencedWasm = ["bfa5198.wasm"];

  expect(shouldCopyShowcaseAsset("shared.js.map", entry, referencedWasm)).toBe(false);
  expect(shouldCopyShowcaseAsset("skiko.wasm", entry, referencedWasm)).toBe(false);
  expect(shouldCopyShowcaseAsset("bfa5198.wasm", entry, referencedWasm)).toBe(true);
  expect(shouldCopyShowcaseAsset("skikod8.mjs", entry, referencedWasm)).toBe(false);
  expect(shouldCopyShowcaseAsset("skiko.mjs", entry, referencedWasm)).toBe(false);
  expect(shouldCopyShowcaseAsset("js-reexport-symbols.mjs", entry, referencedWasm)).toBe(false);
  expect(
    shouldCopyShowcaseAsset(
      "composeResources/gazekeep.shared.generated.resources/drawable/app_icon.png",
      entry,
      referencedWasm,
    ),
  ).toBe(false);
  expect(
    shouldCopyShowcaseAsset(
      "composeResources/gazekeep.shared.generated.resources/files/showcase_cjk.otf",
      entry,
      referencedWasm,
    ),
  ).toBe(true);
  expect(
    shouldCopyShowcaseAsset(
      "composeResources/gazekeep.shared.generated.resources/values/strings.commonMain.cvr",
      entry,
      referencedWasm,
    ),
  ).toBe(true);

  // 若未来入口脚本实际通过 import/URL 引用外部 skiko.mjs，则仍会保留。
  const externalImport = `${entry};import("./skiko.mjs");`;
  expect(shouldCopyShowcaseAsset("skiko.mjs", externalImport, referencedWasm)).toBe(true);
});

test("展示页模板在独立打开且父页无 lang 时安全兜底语言标签，并监听 WebGL 上下文丢失", () => {
  const html = readFileSync(
    new URL("../../../app/shared/src/showcaseMain/resources/index.html", import.meta.url),
    "utf8",
  );
  const scriptMatch = /<script>([\s\S]*?)<\/script>/.exec(html);
  expect(scriptMatch).toBeTruthy();

  const dispatched: string[] = [];
  const navigatorMock = { language: "zh-CN" };
  const listeners = new Map<string, () => void>();
  const ctx = {
    location: { search: "" },
    navigator: navigatorMock,
    document: { documentElement: { lang: "" } },
    window: {
      parent: null as unknown,
      addEventListener: (type: string, fn: () => void) => listeners.set(type, fn),
    },
    HTMLCanvasElement: { prototype: { getContext: () => null } },
    URLSearchParams,
    CustomEvent: class {
      type: string;
      detail: unknown;
      constructor(type: string, init?: { detail?: unknown }) {
        this.type = type;
        this.detail = init?.detail;
      }
    },
    addEventListener: (type: string, fn: () => void) => listeners.set(type, fn),
  };
  ctx.window.parent = ctx.window;
  (ctx.window as { dispatchEvent?: (ev: { type: string }) => void }).dispatchEvent = (ev) => {
    dispatched.push(ev.type);
  };
  vm.runInNewContext(scriptMatch![1], ctx);

  expect((navigatorMock as { languages?: string[] }).languages).toEqual(["zh-CN"]);
  expect(html).toContain("webglcontextlost");
  expect(html).toContain("webglcontextcreationerror");
});
