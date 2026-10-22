import { expect, test } from "vitest";
import { revealRadius, shouldReveal } from "../../../src/scripts/theme-transition.ts";

test("揭示圆半径覆盖距点击点最远的视口角", () => {
  expect(revealRadius({ x: 0, y: 0 }, { width: 300, height: 400 })).toBe(500);
  expect(revealRadius({ x: 300, y: 400 }, { width: 300, height: 400 })).toBe(500);
  expect(revealRadius({ x: 150, y: 200 }, { width: 300, height: 400 })).toBe(250);
});

test("实际明暗不变、减少动态或浏览器不支持时跳过揭示动画", () => {
  const base = { from: "dark", to: "light", reducedMotion: false, supported: true } as const;
  expect(shouldReveal(base)).toBe(true);
  expect(shouldReveal({ ...base, to: "dark" })).toBe(false);
  expect(shouldReveal({ ...base, reducedMotion: true })).toBe(false);
  expect(shouldReveal({ ...base, supported: false })).toBe(false);
});
