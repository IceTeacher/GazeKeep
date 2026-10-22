import { afterEach, expect, test } from "vitest";
import { page } from "vitest/browser";
import { mountWebsite, nextFrame } from "./support/page.ts";

let site: Awaited<ReturnType<typeof mountWebsite>> | undefined;
let freeze: HTMLStyleElement | undefined;
afterEach(() => {
  site?.unmount();
  freeze?.remove();
  site = freeze = undefined;
});

/** 截图只覆盖页面自身排版：关闭动画过渡并等字体就绪；WebGL 机身逐帧绘制且受 GPU 影响，隐藏后不进入基线。 */
async function settle(lang: "zh" | "en", width: number, height: number) {
  await page.viewport(width, height);
  site = await mountWebsite(lang);
  freeze = document.head.appendChild(document.createElement("style"));
  freeze.textContent =
    "*,*::before,*::after{animation:none!important;transition:none!important;caret-color:transparent!important}#phone-stage{visibility:hidden!important}";
  await document.fonts.ready;
  await nextFrame();
  await nextFrame();
}

test("桌面首屏文案", async () => {
  await settle("zh", 1440, 900);
  await expect(page.elementLocator(document.querySelector(".hero-copy")!)).toMatchScreenshot("desktop-hero-zh");
});

test("桌面运行方式卡片（英文）", async () => {
  await settle("en", 1440, 900);
  const list = document.querySelector<HTMLElement>(".backend-list")!;
  list.scrollIntoView({ behavior: "instant", block: "center" });
  await nextFrame();
  await expect(page.elementLocator(list)).toMatchScreenshot("desktop-backends-en");
});

test("移动端首屏文案", async () => {
  await settle("zh", 390, 844);
  await expect(page.elementLocator(document.querySelector(".hero-copy")!)).toMatchScreenshot("mobile-hero-zh");
});
