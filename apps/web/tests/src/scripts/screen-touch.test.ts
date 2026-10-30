import { expect, test } from "vitest";
import { createGestureTracker } from "../../../src/scripts/screen-touch.ts";

test("鼠标按下后位移在阈值内抬起为轻点", () => {
  const g = createGestureTracker(10);
  expect(g.start(1, 100, 200, true)).toBe(true);
  expect(g.move(1, 106, 207)).toBe("pending");
  expect(g.end(1)).toBe("tap");
});

test("鼠标拖动超过阈值即进入内屏拖动，回到起点也保持拖动", () => {
  const g = createGestureTracker(10);
  g.start(1, 100, 200, true);
  expect(g.move(1, 100, 211)).toBe("drag");
  expect(g.move(1, 100, 200)).toBe("drag");
  expect(g.end(1)).toBe("drag");
});

test("触摸未长按即滑动交给页面滚动，长按计时随后失效", () => {
  const g = createGestureTracker(10);
  g.start(1, 100, 200, false);
  expect(g.move(1, 100, 230)).toBe("native");
  expect(g.hold(1)).toBe(false);
  expect(g.move(1, 100, 260)).toBe("native");
  expect(g.end(1)).toBe("native");
});

test("触摸长按后滑动进入内屏拖动", () => {
  const g = createGestureTracker(10);
  g.start(1, 100, 200, false);
  expect(g.move(1, 102, 203)).toBe("pending");
  expect(g.hold(1)).toBe(true);
  expect(g.dragging()).toBe(true);
  expect(g.move(1, 100, 260)).toBe("drag");
  expect(g.end(1)).toBe("drag");
  expect(g.dragging()).toBe(false);
});

test("触摸轻点为轻点；长按不动后抬起按拖动结束，由内屏决定是否点击", () => {
  const tap = createGestureTracker(10);
  tap.start(1, 0, 0, false);
  expect(tap.end(1)).toBe("tap");
  const held = createGestureTracker(10);
  held.start(1, 0, 0, false);
  held.hold(1);
  expect(held.end(1)).toBe("drag");
});

test("鼠标不使用长按计时", () => {
  const g = createGestureTracker(10);
  g.start(1, 0, 0, true);
  expect(g.hold(1)).toBe(false);
  expect(g.end(1)).toBe("tap");
});

test("只跟踪首个指针；取消后返回取消前是否已转发按下", () => {
  const g = createGestureTracker(10);
  expect(g.end(1)).toBe("none");
  g.start(1, 0, 0, true);
  expect(g.start(2, 0, 0, true)).toBe(false);
  expect(g.move(2, 50, 50)).toBe("idle");
  expect(g.end(2)).toBe("none");
  expect(g.cancel(1)).toBe(true);
  expect(g.end(1)).toBe("none");
  g.start(3, 0, 0, false);
  expect(g.cancel(3)).toBe(false);
  g.start(4, 0, 0, false);
  g.hold(4);
  expect(g.cancel(4)).toBe(true);
});

test("重置会结束未收到抬起的手势，并返回是否需要取消内屏按压", () => {
  const g = createGestureTracker(10);
  expect(g.reset()).toBe(false);
  g.start(1, 0, 0, false);
  g.hold(1);
  expect(g.reset()).toBe(true);
  expect(g.dragging()).toBe(false);
  expect(g.start(2, 0, 0, false)).toBe(true);
});
