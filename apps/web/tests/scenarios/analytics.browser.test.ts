import { expect, inject, test } from "vitest";

const websiteId = "35f452cf-4935-4581-9b9a-fac2c27cdd07";

test.each(["zh", "en"] as const)("%s 页面经同站 /stats 代理加载 Umami 统计与会话录制，统计只计生产域名", (lang) => {
  const { scripts } = inject("pages")[lang];
  const umami = scripts.filter((script) => script.src?.startsWith("/stats/"));
  expect(umami).toEqual([
    { src: "/stats/script.js", defer: "", "data-website-id": websiteId, "data-domains": "gazekeep.rainafter.cn" },
    { src: "/stats/recorder.js", defer: "", "data-website-id": websiteId },
  ]);
});
