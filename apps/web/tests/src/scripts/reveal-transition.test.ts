import { expect, test } from "vitest";
import { clickOrigin, revealRadius } from "../../../src/scripts/reveal-transition.ts";

test("揭示半径覆盖最远视口角", () => {
  expect(revealRadius({ x: 0, y: 0 }, { width: 300, height: 400 })).toBe(500);
});

test("点击坐标取自指针事件，键盘触发时取控件中心", () => {
  const el = {
    getBoundingClientRect: () => ({ left: 100, top: 20, width: 40, height: 20 }),
  } as unknown as Element;
  expect(clickOrigin(el, { detail: 1, clientX: 112, clientY: 28 })).toEqual({ x: 112, y: 28 });
  expect(clickOrigin(el, { detail: 0, clientX: 0, clientY: 0 })).toEqual({ x: 120, y: 30 });
});
