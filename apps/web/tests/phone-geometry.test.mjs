import test from "node:test";
import assert from "node:assert/strict";
import { PHONE, projectPlane, mapPlanePoint, fitPhone } from "../src/scripts/phone-geometry.mjs";
import { heroPose, storyPose } from "../src/scripts/story-motion.mjs";

test("首屏保留旧版倾斜，滚出后逐轴回正", () => {
  assert.deepEqual(heroPose(0), { rotateX: 7, rotateY: -12, rotateZ: 6 });
  assert.deepEqual(heroPose(0, true), { rotateX: 5, rotateY: -10, rotateZ: 5 });
  assert.deepEqual(heroPose(1), { rotateX: 0, rotateY: 0, rotateZ: 0 });
  const mid = heroPose(0.5);
  assert.ok(mid.rotateX > 0 && mid.rotateX < 7);
  assert.ok(mid.rotateY < 0 && mid.rotateY > -12);
});
test("手部先淡出，手机随后移动和回正", () => {
  assert.equal(storyPose(1.8).grip, 0);
  assert.equal(storyPose(1.8).turn, -28);
  assert.equal(storyPose(2.25).x, storyPose(2).x);
  assert.equal(storyPose(3).turn, 0);
});
test("系统装饰与应用共用逻辑画布和缩放比", () => {
  assert.equal(PHONE.screenHeight, PHONE.statusHeight + PHONE.appHeight + PHONE.navHeight);
  assert.equal(PHONE.screenHeight, 848);
  assert.equal(PHONE.statusHeight, 40);
  assert.equal(PHONE.appWidth, 392);
  assert.equal(PHONE.appHeight, 784);
  assert.equal(fitPhone(216).scale / fitPhone(432).scale, 0.5);
});
test("透视映射将屏幕四角准确映射到不同姿态的投影", () => {
  const w = PHONE.screenWidth,
    h = PHONE.screenHeight;
  for (const corners of [
    [
      [12, 8],
      [212, 20],
      [190, 430],
      [20, 400],
    ],
    [
      [0, 0],
      [100, 0],
      [100, 210],
      [0, 210],
    ],
    [
      [100, 10],
      [280, 0],
      [300, 420],
      [90, 440],
    ],
  ]) {
    const m = projectPlane(w, h, corners);
    for (const [i, p] of [
      [0, 0],
      [w, 0],
      [w, h],
      [0, h],
    ].entries()) {
      const out = mapPlanePoint(m, ...p);
      assert.ok(Math.hypot(out[0] - corners[i][0], out[1] - corners[i][1]) < 1e-7);
    }
  }
});

test("屏幕四边边框及圆角保持等宽", () => {
  const horizontal = (PHONE.width - PHONE.screenWidth) / 2;
  const vertical = (PHONE.height - PHONE.screenHeight) / 2;
  assert.equal(horizontal, 12);
  assert.equal(vertical, horizontal);
  assert.equal(PHONE.radius - PHONE.screenRadius, horizontal);
  assert.equal(PHONE.bezel, horizontal);
});
