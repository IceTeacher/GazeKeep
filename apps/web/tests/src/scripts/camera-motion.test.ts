import { expect, test } from "vitest";
import { haloFrame, initialHalo } from "../../../src/scripts/camera-motion.ts";

test("速度滤波保持弧长范围，正反滚动得到相同弧长", () => {
  const a = haloFrame(initialHalo(), { dt: 16, delta: 100, active: true });
  const b = haloFrame(initialHalo(), { dt: 16, delta: -100, active: true });
  expect(a.arc).toBe(b.arc);
  expect(a.arc > 70 && a.arc <= 260).toBe(true);
  let settled = a;
  for (let i = 0; i < 100; i++) settled = haloFrame(settled, { dt: 16, delta: 0, active: true });
  expect(Math.abs(settled.arc - 70)).toBeLessThan(0.01);
});
test("旋转线性跨圈，退出淡出期间继续，重新进入不重置相位", () => {
  let s = initialHalo();
  for (let i = 0; i < 100; i++) s = haloFrame(s, { dt: 16, delta: 0, active: true });
  expect(Math.abs(s.angle - 384)).toBeLessThan(1e-8);
  const leaving = haloFrame(s, { dt: 16, delta: 0, active: false });
  expect(leaving.angle > s.angle && leaving.opacity > 0 && leaving.opacity < 1).toBe(true);
  const reenter = haloFrame(leaving, { dt: 16, delta: 0, active: true });
  expect(Math.abs(reenter.angle - leaving.angle - 3.84)).toBeLessThan(1e-8);
  for (let i = 0; i < 20; i++) s = haloFrame(s, { dt: 16, delta: 0, active: false });
  expect(s.opacity).toBe(0);
  expect(haloFrame(s, { dt: 16, delta: 0, active: false }).angle).toBe(s.angle);
});
test("减少动态使用静态提示，长帧不会造成相位跳跃", () => {
  const still = haloFrame(initialHalo(), { dt: 16, delta: 500, active: true, reduced: true });
  expect(still.angle).toBe(0);
  expect(still.arc).toBe(120);
  expect(still.opacity).toBe(1);
  const delayed = haloFrame(initialHalo(), { dt: 5000, delta: 0, active: true });
  expect(delayed.angle).toBeLessThanOrEqual(240 * 0.064);
});
