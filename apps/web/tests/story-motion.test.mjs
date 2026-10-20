import test from "node:test";
import assert from "node:assert/strict";
import {
  storyPose,
  selectSection,
  featurePose,
  featureSpan,
  featureReveal,
  followPose,
} from "../src/scripts/story-motion.mjs";

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

test("横移以倾斜包络过渡，静止段回正且方向随运动", () => {
  for (const p of [-1, 0, 1, 1.8, 2, 2.25, 3, 4]) assert.equal(storyPose(p).travel, 0, String(p));
  assert.equal(storyPose(0.5).travel, -1);
  assert.equal(storyPose(2.625).travel, 1);
  assert.ok(storyPose(0.5).turn < -28 * 0.5 && storyPose(0.5).rotateZ < 0);
  assert.ok(storyPose(2.625).turn > 0 && storyPose(2.625).rotateZ > 0);
  assert.ok(storyPose(2.625).scale < 1);
  assert.equal(storyPose(0.5, true).travel, 0);
  // 缓动两端速度为零：起步与收尾都比线性慢。
  const start = storyPose(0).x,
    end = storyPose(1).x;
  assert.ok(Math.abs(storyPose(0.05).x - start) < Math.abs(end - start) * 0.05);
  assert.ok(Math.abs(storyPose(0.95).x - end) < Math.abs(end - start) * 0.05);
  let last = start;
  for (let p = 0; p <= 1; p += 0.05) {
    const x = storyPose(p).x;
    assert.ok(x <= last + 1e-12);
    last = x;
  }
});

test("专注范围区手机移到左侧，过渡中倾斜并在两端回正", () => {
  const right = featurePose(0, 1280),
    left = featurePose(1, 1280);
  assert.equal(right.x, storyPose(4).x);
  assert.equal(left.x, -right.x);
  for (const pose of [right, left]) {
    assert.equal(pose.turn, 0);
    assert.equal(pose.rotateZ, 0);
    assert.equal(pose.scale, 1);
  }
  const mid = featurePose(0.5, 1280);
  assert.equal(mid.travel, -1);
  assert.ok(mid.turn < 0 && mid.rotateZ < 0 && mid.scale < 1);
  assert.deepEqual(featurePose(-1, 1280), right);
  assert.deepEqual(featurePose(2, 1280), left);
  for (const width of [800, 1280, 1920]) {
    const unit = Math.min(width, 1200) / 2;
    assert.ok(width / 2 + featurePose(1, width).x * unit < width / 2);
  }
});

test("专注范围横移在吸附减速段完成：终点为范围停靠点，起点在运行方式列表离开设备之后", () => {
  const base = { listBottom: 5700, headingTop: 6547, stageTop: 100, phoneHeight: 621, snapTop: 100, viewport: 900 };
  const span = featureSpan(base);
  // 与第二幕到第三幕一致：吸附停靠时横移刚好结束，而不是在吸附中途一闪而过。
  assert.equal(span.end, base.headingTop - base.snapTop);
  assert.equal(span.start, base.listBottom - (base.stageTop + base.phoneHeight * 0.12));
  // 范围文案随横移后半程淡入，手机掠过时不压住清晰文字，停靠时完全显示。
  const reveal = (p) => featureReveal(featurePose(p, 1280).x, 1280);
  assert.equal(reveal(0), 0);
  assert.equal(reveal(0.5), 0);
  assert.ok(reveal(0.75) > 0 && reveal(0.75) < 1);
  assert.equal(reveal(1), 1);
  // 区间过短时向前延伸，保证横移可见。
  const tight = featureSpan({ ...base, listBottom: 6400 });
  assert.equal(tight.end - tight.start, base.viewport * 0.25);
  assert.equal(tight.end, base.headingTop - base.snapTop);
});

test("阻尼跟随收敛到位置决定的姿态，长间隔直接对齐", () => {
  const target = { x: 1, turn: -20, rotateX: 0, rotateZ: 4, scale: 0.9 };
  let pose = { x: 0, turn: 0, rotateX: 0, rotateZ: 0, scale: 1 };
  const first = followPose(pose, target, 16);
  assert.ok(first.x > 0 && first.x < 1);
  for (let i = 0; i < 120; i++) pose = followPose(pose, target, 16);
  assert.deepEqual(pose, target);
  assert.deepEqual(followPose({ ...target, x: -5 }, target, 500), target);
  assert.deepEqual(followPose(undefined, target, 16), target);
});
