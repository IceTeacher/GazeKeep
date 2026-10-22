import { afterEach, expect, test } from "vitest";
import { commands, page } from "vitest/browser";
import { mountWebsite, nextFrame, SHOWCASE_TIMEOUT } from "../_support/page.ts";

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

const chassisTransform = () => document.querySelector<HTMLElement>("#phone-chassis")!.style.transform;

test("首屏停留时手机缓慢漂移，减少动态时保持静止", async () => {
  await page.viewport(1440, 900);
  site = await mountWebsite("zh");
  // 姿态只作用于 WebGL 实体机身；three.js 加载前的正面降级投影没有姿态。
  await expect
    .poll(() => document.querySelector<HTMLElement>("#phone-visual")!.dataset.renderer, { timeout: SHOWCASE_TIMEOUT })
    .toBe("webgl");
  // 首屏停留期间持续漂移：连续多个 1.2 秒窗口都有变化。
  for (let i = 0; i < 3; i++) {
    const before = chassisTransform();
    await new Promise((resolve) => setTimeout(resolve, 1200));
    expect(chassisTransform()).not.toBe(before);
  }
  site.unmount();

  await commands.emulateMedia({ reducedMotion: "reduce" });
  site = await mountWebsite("zh");
  await expect.poll(chassisTransform, { timeout: SHOWCASE_TIMEOUT }).not.toBe("");
  await document.fonts.ready;
  await expect.poll(() => site!.showcase.calls.length, { timeout: SHOWCASE_TIMEOUT }).toBeGreaterThan(0);
  // 漂移每 40ms 改变一次姿态，任何 1.2 秒窗口都不可能静止；减少动态时一次性重新测量结束后必然出现静止窗口。
  const staticFor = async (ms: number) => {
    const before = chassisTransform();
    await new Promise((resolve) => setTimeout(resolve, ms));
    return chassisTransform() === before;
  };
  await expect.poll(() => staticFor(1200), { timeout: SHOWCASE_TIMEOUT }).toBe(true);
});

test("滚动显现：未进入视口的内容先隐藏，进入后显现；减少动态时全部直接可见", async () => {
  await page.viewport(1440, 900);
  site = await mountWebsite("zh");
  const lines = document.querySelector<HTMLElement>(".privacy-lines")!;
  expect(document.documentElement.dataset.motion).toBe("on");
  expect(lines.classList.contains("is-revealed")).toBe(false);
  expect(getComputedStyle(lines.querySelector(".privacy-icon")!).opacity).toBe("0");
  lines.scrollIntoView({ behavior: "instant", block: "center" });
  await expect.poll(() => lines.classList.contains("is-revealed"), { timeout: 5_000 }).toBe(true);
  site.unmount();

  await commands.emulateMedia({ reducedMotion: "reduce" });
  site = await mountWebsite("zh");
  expect(document.documentElement.dataset.motion).toBe("off");
  for (const target of document.querySelectorAll<HTMLElement>("[data-reveal]")) {
    expect(target.classList.contains("is-revealed")).toBe(true);
    expect(getComputedStyle(target).opacity).toBe("1");
  }
});
