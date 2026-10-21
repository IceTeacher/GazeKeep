/// <reference types="vitest/config" />
import { readFile } from "node:fs/promises";
import { playwright } from "@vitest/browser-playwright";
import { getViteConfig } from "astro/config";
import type { Plugin } from "vite";
import { emulateMedia } from "./tests/browser/support/commands.ts";

// 浏览器测试不依赖 KMP 产物：展示 iframe 由受控 fixture 按真实桥接协议回报就绪、绘制与失败。
const showcaseFixture = (): Plugin => ({
  name: "gazekeep-showcase-fixture",
  configureServer(server) {
    server.middlewares.use(async (req, res, next) => {
      if (!/^\/kmp-showcase\/(wasm|js)\/index\.html/.test(req.url ?? "")) return next();
      res.setHeader("content-type", "text/html; charset=utf-8");
      res.end(await readFile(new URL("tests/browser/fixtures/showcase.html", import.meta.url), "utf8"));
    });
  },
});

export default getViteConfig({
  plugins: [showcaseFixture()],
  test: {
    projects: [
      {
        extends: true,
        test: {
          name: "unit",
          environment: "node",
          include: ["tests/unit/**/*.test.ts"],
        },
      },
      {
        extends: true,
        test: {
          name: "browser",
          include: ["tests/browser/**/*.browser.test.ts"],
          globalSetup: ["tests/browser/support/render-pages.ts"],
          browser: {
            enabled: true,
            commands: { emulateMedia },
            headless: true,
            provider: playwright({ launchOptions: { args: ["--disable-gpu", "--font-render-hinting=none"] } }),
            instances: [{ browser: "chromium", viewport: { width: 1440, height: 900 } }],
            expect: {
              toMatchScreenshot: {
                comparatorName: "pixelmatch",
                comparatorOptions: { threshold: 0.2, allowedMismatchedPixelRatio: 0.01 },
              },
            },
          },
        },
      },
    ],
  },
});
