import { afterEach, expect, test } from "vitest";
import { page } from "vitest/browser";
import { mountWebsite, nextFrame } from "../_support/page.ts";

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
  // 悬停首项暂停 1.5 秒一次的自动轮换，截图期间选中项不变。
  list.querySelector('[data-backend="lsposed"]')!.dispatchEvent(new MouseEvent("mouseenter"));
  await nextFrame();
  await expect(page.elementLocator(list)).toMatchScreenshot("desktop-backends-en");
});

test("移动端首屏文案", async () => {
  await settle("zh", 390, 844);
  await expect(page.elementLocator(document.querySelector(".hero-copy")!)).toMatchScreenshot("mobile-hero-zh");
});

/** 滚到目标并等待其滚动显现完成；截图冻结动画后呈现最终状态。 */
async function reveal(selector: string) {
  const target = document.querySelector<HTMLElement>(selector)!;
  target.scrollIntoView({ behavior: "instant", block: "center" });
  await expect.poll(() => target.classList.contains("is-revealed"), { timeout: 5_000 }).toBe(true);
  await nextFrame();
  return target;
}

test("桌面隐私三项（深色）", async () => {
  await settle("zh", 1440, 900);
  const lines = await reveal(".privacy-lines");
  await expect(page.elementLocator(lines)).toMatchScreenshot("desktop-privacy-zh");
});

test("移动端隐私三项（浅色）", async () => {
  await page.viewport(390, 844);
  site = await mountWebsite("zh", { theme: "light" });
  freeze = document.head.appendChild(document.createElement("style"));
  freeze.textContent =
    "*,*::before,*::after{animation:none!important;transition:none!important;caret-color:transparent!important}#phone-stage{visibility:hidden!important}";
  await document.fonts.ready;
  const lines = await reveal(".privacy-lines");
  await expect(page.elementLocator(lines)).toMatchScreenshot("mobile-privacy-zh-light");
});

test("桌面设置卡片", async () => {
  await settle("zh", 1440, 900);
  await reveal('.setting-card[data-setting="hide_indicator"]');
  const grid = document.querySelector<HTMLElement>(".setting-grid")!;
  await expect(page.elementLocator(grid)).toMatchScreenshot("desktop-settings-zh");
});
