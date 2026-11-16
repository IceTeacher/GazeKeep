import { expect, test } from "vitest";
import { assertFixedPublicPath } from "../../scripts/kmp-showcase.ts";
import {
  buildShowcasePreloads,
  findReferencedWasm,
  hashedAssetName,
  renderShowcaseIndex,
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
