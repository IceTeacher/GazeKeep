import { afterEach, expect, test } from "vitest";
import { commands, page } from "vitest/browser";
import { mountWebsite, nextFrame, SHOWCASE_TIMEOUT } from "./support/page.ts";

let site: Awaited<ReturnType<typeof mountWebsite>> | undefined;
afterEach(async () => {
  site?.unmount();
  site = undefined;
  await commands.emulateMedia({ reducedMotion: null });
});

test("减少动态时使用紧凑静态布局：关闭吸附与首屏 shader，三幕文案全部可读", async () => {
  await page.viewport(1440, 900);
  await commands.emulateMedia({ reducedMotion: "reduce" });
  site = await mountWebsite("zh");
  await nextFrame();
  const root = document.documentElement;
  expect(root.dataset.compact).toBe("true");
  expect(getComputedStyle(root).scrollSnapType).not.toContain("mandatory");
  expect(document.querySelector<HTMLCanvasElement>("#hero-shader")!.hidden).toBe(true);
  for (const step of document.querySelectorAll<HTMLElement>(".story-step")) {
    expect(step.style.opacity).toBe("1");
    expect(step.inert).toBe(false);
  }
});

test("运行中切换减少动态偏好后立即改为紧凑布局，恢复后取消", async () => {
  await page.viewport(1440, 900);
  site = await mountWebsite("zh");
  await nextFrame();
  expect(document.documentElement.dataset.compact).toBe("false");
  await commands.emulateMedia({ reducedMotion: "reduce" });
  await expect.poll(() => document.documentElement.dataset.compact, { timeout: SHOWCASE_TIMEOUT }).toBe("true");
  await commands.emulateMedia({ reducedMotion: "no-preference" });
  await expect.poll(() => document.documentElement.dataset.compact, { timeout: SHOWCASE_TIMEOUT }).toBe("false");
});
