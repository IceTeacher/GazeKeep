import { expect, test } from "vitest";
import { createTapTracker } from "../../../src/scripts/screen-touch.ts";

test("按下与抬起位移在阈值内才算轻点", () => {
  const tap = createTapTracker(10);
  tap.start(1, 100, 200);
  expect(tap.move(1, 106, 207)).toBe(true);
  expect(tap.end(1)).toBe(true);
});

test("超出阈值即视为滑动，抬起后不再触发点击", () => {
  const tap = createTapTracker(10);
  tap.start(1, 100, 200);
  expect(tap.move(1, 100, 211)).toBe(false);
  // 回到起点也不能把已经开始的滑动当成轻点。
  expect(tap.move(1, 100, 200)).toBe(false);
  expect(tap.end(1)).toBe(false);
});

test("只跟踪首个指针，取消或其他指针不产生点击", () => {
  const tap = createTapTracker(10);
  expect(tap.end(1)).toBe(false);
  tap.start(1, 0, 0);
  expect(tap.start(2, 0, 0)).toBe(false);
  expect(tap.end(2)).toBe(false);
  tap.cancel(1);
  expect(tap.end(1)).toBe(false);
  expect(tap.start(3, 0, 0)).toBe(true);
  expect(tap.end(3)).toBe(true);
});
