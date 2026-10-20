import test from "node:test";
import assert from "node:assert/strict";
import { storyPose, selectSection } from "../src/scripts/story-motion.mjs";

test("三幕位置在边界连续，侧面仍保留正面投影", () => {
  assert.equal(storyPose(-1).turn, 0);
  assert.equal(storyPose(1).turn, -28);
  assert.ok(storyPose(1).x < 0);
  assert.equal(storyPose(2).grip, 0);
  assert.equal(storyPose(2).scale, 1);
  assert.equal(storyPose(3).x, storyPose(0).x);
  assert.equal(storyPose(2).turn, 0);
  assert.equal(storyPose(3).turn, 0);
  assert.equal(storyPose(3).dim, 1);
  for (const boundary of [1, 2, 2.25, 3]) {
    const before = storyPose(boundary - 0.00001);
    const after = storyPose(boundary + 0.00001);
    for (const key of Object.keys(before)) assert.ok(Math.abs(before[key] - after[key]) < 0.002, key);
  }
  assert.ok(Math.cos((storyPose(1).turn * Math.PI) / 180) > 0.6);
});
test("正反滚动和直接跳转使用相同姿态，小屏缩短侧转", () => {
  const positions = [0, 0.5, 1, 1.8, 2.5, 3];
  const forward = positions.map((p) => storyPose(p));
  assert.deepEqual(
    positions
      .toReversed()
      .map((p) => storyPose(p))
      .toReversed(),
    forward,
  );
  assert.equal(storyPose(1, true).turn, -20);
  assert.equal(storyPose(3, true).turn, 0);
});
test("阶段由位置确定，快速反向与重叠区不依赖观察器回调顺序", () => {
  const starts = [0, 100, 200, 300];
  assert.equal(selectSection(starts, 250), 2);
  assert.equal(selectSection(starts, 100), 1);
  assert.equal(selectSection(starts, -20), 0);
  assert.equal(selectSection(starts, 999), 3);
});

test("持机三轴与缩放统一插值，结束后恢复正面", () => {
  const held = storyPose(1),
    mobile = storyPose(1, true);
  assert.equal(held.rotateX, 3);
  assert.equal(held.rotateZ, -6);
  assert.ok(Math.abs(held.scale - 0.82) < 1e-10);
  assert.ok(Math.abs(held.x + (0.38 * 1280) / 1200) < 1e-10);
  assert.equal(mobile.rotateX, 2);
  assert.equal(mobile.rotateZ, -4);
  assert.ok(Math.abs(mobile.scale - 0.78) < 1e-10);
  assert.ok(Math.abs(mobile.x + 0.06) < 1e-10);
  for (const p of [0, 3]) {
    const pose = storyPose(p);
    assert.equal(pose.rotateX, 0);
    assert.equal(pose.rotateZ, 0);
    assert.equal(pose.scale, 1);
  }
  assert.equal(storyPose(2.25).grip, 0);
  assert.equal(storyPose(1.8).turn, -28);
});

test("持机投影中心按视口比例定位，首尾保留原位置", () => {
  for (const width of [320, 390, 768, 1280, 1920]) {
    const mobile = width <= 760,
      held = storyPose(1, mobile, width);
    const unit = (mobile ? width : Math.min(width, 1200)) / 2;
    assert.ok(Math.abs(width / 2 + held.x * unit - width * (mobile ? 0.47 : 0.31)) < 1e-8);
    assert.equal(storyPose(0, mobile, width).x, mobile ? 0.12 : 0.52);
    assert.equal(storyPose(3, mobile, width).x, storyPose(0, mobile, width).x);
  }
});

test("黑屏连续进入并在后端区域前恢复", () => {
  assert.equal(storyPose(2).dim, 0);
  assert.ok(storyPose(2.25).dim > 0 && storyPose(2.25).dim < 1);
  assert.equal(storyPose(2.5).dim, 1);
  assert.equal(storyPose(4).dim, 0);
});
