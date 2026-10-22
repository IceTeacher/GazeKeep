import { afterEach, describe, expect, test } from "vitest";
import { page } from "vitest/browser";
import { mountWebsite, nextFrame, scrollToY } from "./support/page.ts";

let site: Awaited<ReturnType<typeof mountWebsite>> | undefined;
afterEach(() => {
  site?.unmount();
  site = undefined;
});

const steps = () => [...document.querySelectorAll<HTMLElement>(".story-step")];
const activeIndex = () => steps().findIndex((step) => step.classList.contains("is-active"));
const scene = () => Number(document.querySelector<HTMLElement>("#phone-stage")!.dataset.scene);
/** 按元素自身的吸附对齐方式停靠，与浏览器吸附落点一致（含 scroll-padding 与 scroll-margin）。 */
async function snapTo(el: HTMLElement, block: ScrollLogicalPosition) {
  el.scrollIntoView({ behavior: "instant", block });
  await nextFrame();
  await nextFrame();
}

describe("桌面三幕叙事", () => {
  test("原生强制吸附开启，三幕文案的停靠位置对应整数幕并激活对应文案", async () => {
    await page.viewport(1440, 900);
    site = await mountWebsite("zh");
    expect(document.documentElement.dataset.compact).toBe("false");
    expect(getComputedStyle(document.documentElement).scrollSnapType).toContain("mandatory");
    const copies = steps().map((step) => step.querySelector<HTMLElement>(".story-copy")!);
    expect(copies).toHaveLength(3);
    for (const [i, copy] of copies.entries()) {
      await snapTo(copy, "center");
      expect(scene()).toBeCloseTo(i + 1, 1);
      expect(activeIndex()).toBe(i);
      expect(steps()[i].inert).toBe(false);
    }
  });

  test("正向滚动与反向回到同一位置得到相同场景，不依赖经过的路径", async () => {
    await page.viewport(1440, 900);
    site = await mountWebsite("zh");
    const second = steps()[1].querySelector<HTMLElement>(".story-copy")!;
    await snapTo(second, "center");
    const forward = { scene: scene(), active: activeIndex() };
    await scrollToY(document.documentElement.scrollHeight);
    await snapTo(second, "center");
    expect({ scene: scene(), active: activeIndex() }).toEqual(forward);
  });
});

test("移动端三幕由零高标记逐幕强制吸附，每个停靠点落在整数幕", async () => {
  await page.viewport(390, 844);
  site = await mountWebsite("zh");
  expect(document.documentElement.dataset.compact).toBe("false");
  expect(getComputedStyle(document.documentElement).scrollSnapType).toContain("mandatory");
  const marks = [...document.querySelectorAll<HTMLElement>(".story-snap")];
  expect(marks).toHaveLength(3);
  for (const [i, mark] of marks.entries()) {
    expect(getComputedStyle(mark).scrollSnapStop).toBe("always");
    await snapTo(mark, "start");
    expect(scene()).toBeCloseTo(i + 1, 1);
    expect(activeIndex()).toBe(i);
  }
});
