import { afterEach, expect, test } from "vitest";
import { page, userEvent } from "vitest/browser";
import { mountWebsite } from "../_support/page.ts";

let site: Awaited<ReturnType<typeof mountWebsite>> | undefined;
afterEach(async () => {
  await userEvent.unhover(document.body);
  site?.unmount();
  site = undefined;
});

const transformOf = (el: Element) => getComputedStyle(el).transform;

test("下载按钮悬停时上浮，下载箭头循环下落而托盘不动", async () => {
  await page.viewport(1440, 900);
  site = await mountWebsite("zh");
  const button = document.querySelector<HTMLElement>(".hero-actions .button")!;
  const [arrow, tray, head] = button.querySelectorAll(".lucide-download path");
  expect(getComputedStyle(arrow!).animationName).toBe("none");
  await userEvent.hover(button);
  await expect.poll(() => getComputedStyle(arrow!).animationName, { timeout: 5_000 }).toBe("arrow-drop");
  expect(getComputedStyle(head!).animationName).toBe("arrow-drop");
  expect(getComputedStyle(tray!).animationName).toBe("none");
  await expect.poll(() => transformOf(button), { timeout: 5_000 }).not.toBe("none");
  expect(getComputedStyle(button).boxShadow).not.toBe("none");
});

test("外链箭头悬停时朝右上探出，导航图标按钮出现圆形底色", async () => {
  await page.viewport(1440, 900);
  site = await mountWebsite("zh");
  const link = document.querySelector<HTMLElement>(".hero-actions .text-link")!;
  const arrow = link.querySelector(".lucide-arrow-up-right")!;
  expect(transformOf(arrow)).toBe("none");
  await userEvent.hover(link);
  await expect.poll(() => new DOMMatrix(transformOf(arrow)).m41, { timeout: 5_000 }).toBeCloseTo(3);
  expect(new DOMMatrix(transformOf(arrow)).m42).toBeCloseTo(-3);

  const toggle = document.querySelector<HTMLElement>("#theme-toggle")!;
  const before = getComputedStyle(toggle).backgroundColor;
  await userEvent.hover(toggle);
  await expect.poll(() => getComputedStyle(toggle).backgroundColor, { timeout: 5_000 }).not.toBe(before);
});
