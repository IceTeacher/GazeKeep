import { expect, inject, test } from "vitest";

const origin = "https://gazekeep.rainafter.cn";

test.each(["zh", "en"] as const)("%s 页面提供完整的 Open Graph 与 Twitter 分享信息", async (lang) => {
  const { meta } = inject("pages")[lang];
  const url = `${origin}/${lang}/`;
  const image = `${origin}/og/og-${lang}.png`;
  expect(meta["og:url"]).toBe(url);
  expect(meta["og:type"]).toBe("website");
  expect(meta["og:site_name"]).toBe("GazeKeep");
  expect(meta["og:title"]).toBe(meta["twitter:title"]);
  expect(meta["og:title"]).toMatch(/^GazeKeep/);
  expect(meta["og:description"]).toBe(meta["description"]);
  expect(meta["twitter:description"]).toBe(meta["description"]);
  expect(meta["og:image"]).toBe(image);
  expect(meta["twitter:image"]).toBe(image);
  expect(meta["og:image:width"]).toBe("1200");
  expect(meta["og:image:height"]).toBe("630");
  expect(meta["og:image:alt"]).toBeTruthy();
  expect(meta["twitter:card"]).toBe("summary_large_image");
  expect(meta["og:locale"]).toBe(lang === "zh" ? "zh_CN" : "en_US");
  expect(meta["og:locale:alternate"]).toBe(lang === "zh" ? "en_US" : "zh_CN");
  // 图片需随站点发布，尺寸与声明一致。
  const response = await fetch(`/og/og-${lang}.png`);
  expect(response.ok).toBe(true);
  const bitmap = await createImageBitmap(await response.blob());
  expect([bitmap.width, bitmap.height]).toEqual([1200, 630]);
});
