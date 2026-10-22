import { defineConfig } from "astro/config";
import tailwindcss from "@tailwindcss/vite";
import { site } from "./src/data/site.config.ts";
import { preloadFirstFrameChunks, shaderRegistryPlugin } from "./scripts/build-performance.ts";
export default defineConfig({
  output: "static",
  site: site.url,
  i18n: { defaultLocale: "zh", locales: ["zh", "en"], routing: { prefixDefaultLocale: true } },
  // 样式约 9 KB（gzip），内联省去一次渲染阻塞请求。
  build: { inlineStylesheets: "always" },
  integrations: [preloadFirstFrameChunks()],
  vite: { plugins: [tailwindcss(), shaderRegistryPlugin()] },
});
