import { expect, test } from "vitest";
import { demoVisible } from "../../../src/scripts/demo-visibility.ts";

test("手机可见部分不足 25% 或标签页隐藏时视为离开", () => {
  expect(demoVisible(1, false)).toBe(true);
  expect(demoVisible(0.25, false)).toBe(true);
  expect(demoVisible(0.24, false)).toBe(false);
  expect(demoVisible(0, false)).toBe(false);
  expect(demoVisible(1, true)).toBe(false);
});
