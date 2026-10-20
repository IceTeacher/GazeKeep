import test from "node:test";
import assert from "node:assert/strict";
import { haloFrame, initialHalo } from "../src/scripts/camera-motion.ts";

test("速度滤波保持弧长范围，正反滚动得到相同弧长", () => {
  const a = haloFrame(initialHalo(), { dt: 16, delta: 100, active: true });
  const b = haloFrame(initialHalo(), { dt: 16, delta: -100, active: true });
  assert.equal(a.arc, b.arc);
  assert.ok(a.arc > 70 && a.arc <= 260);
  let settled = a;
  for (let i = 0; i < 100; i++) settled = haloFrame(settled, { dt: 16, delta: 0, active: true });
  assert.ok(Math.abs(settled.arc - 70) < 0.01);
});
test("旋转线性跨圈，退出淡出期间继续，重新进入不重置相位", () => {
  let s = initialHalo();
  for (let i = 0; i < 100; i++) s = haloFrame(s, { dt: 16, delta: 0, active: true });
  assert.ok(Math.abs(s.angle - 384) < 1e-8);
  const leaving = haloFrame(s, { dt: 16, delta: 0, active: false });
  assert.ok(leaving.angle > s.angle && leaving.opacity > 0 && leaving.opacity < 1);
  const reenter = haloFrame(leaving, { dt: 16, delta: 0, active: true });
  assert.ok(Math.abs(reenter.angle - leaving.angle - 3.84) < 1e-8);
  for (let i = 0; i < 20; i++) s = haloFrame(s, { dt: 16, delta: 0, active: false });
  assert.equal(s.opacity, 0);
  assert.equal(haloFrame(s, { dt: 16, delta: 0, active: false }).angle, s.angle);
});
test("减少动态使用静态提示，长帧不会造成相位跳跃", () => {
  const still = haloFrame(initialHalo(), { dt: 16, delta: 500, active: true, reduced: true });
  assert.equal(still.angle, 0);
  assert.equal(still.arc, 120);
  assert.equal(still.opacity, 1);
  const delayed = haloFrame(initialHalo(), { dt: 5000, delta: 0, active: true });
  assert.ok(delayed.angle <= 240 * 0.064);
});
