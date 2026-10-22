import { expect, test } from "vitest";
import { assertFixedPublicPath } from "../../scripts/kmp-showcase.ts";

test("KMP 入口使用自动 publicPath 时构建失败，避免托管方注入的页尾脚本改写 wasm 地址", () => {
  // 摘自 webpack 自动 publicPath 运行时：取不到 currentScript 时回退到页面最后一个 http(s) 脚本。
  const auto = `const a=e.getElementsByTagName("script");if(!_)throw new Error("Automatic publicPath is not supported in this browser");`;
  expect(() => assertFixedPublicPath(auto, "wasmJs")).toThrow(/wasmJs.*publicPath/);
  expect(() => assertFixedPublicPath(`a.p="./",a.b=document.baseURI`, "js")).not.toThrow();
});
