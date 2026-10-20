import test from "node:test";
import assert from "node:assert/strict";
import { sceneState, readingOpacity, mobileReadingLayout } from "../src/scripts/story-motion.mjs";
import { createRenderGate } from "../src/scripts/showcase-state.mjs";
test("隐私先换页，手部后淡出；后端只在全黑期间切换", () => {
  assert.equal(sceneState(1.4).route, "privacy");
  assert.equal(sceneState(1.4).grip, 1);
  assert.equal(sceneState(2).grip, 0);
  assert.equal(sceneState(3.39).route, "privacy");
  assert.equal(sceneState(3.41).route, "backend");
  for (const p of [3.39, 3.41]) assert.equal(sceneState(p).dim, 1);
  assert.equal(sceneState(4).dim, 0);
});
test("绘制回执忽略过期编号和错误路由", () => {
  const g = createRenderGate();
  const a = g.request("privacy");
  const b = g.request("backend");
  assert.equal(g.accept("privacy", a), false);
  assert.equal(g.accept("privacy", b), false);
  assert.equal(g.accept("backend", b), true);
  assert.equal(g.ready("backend"), true);
  g.reset();
  assert.equal(g.ready("backend"), false);
});
test("文案拥有完整阅读区间，正反滚动结果一致", () => {
  for (const p of [0.7, 1, 1.3]) assert.equal(readingOpacity(p, 1), 1);
  const p = [1, 1.4, 1.6, 2, 2.5, 3.4, 4];
  assert.deepEqual(
    p.map((v) => sceneState(v)),
    p
      .toReversed()
      .map((v) => sceneState(v))
      .toReversed(),
  );
});

test("移动端设备与最长正文分区，短屏与大字号改为自然阅读", () => {
  for (const height of [740, 844, 932]) {
    const layout = mobileReadingLayout(height, 220);
    assert.equal(layout.compact, false);
    assert.ok(layout.copyTop > layout.phoneHeight + 16);
    assert.ok(layout.copyTop + 220 < height - 60);
  }
  assert.equal(mobileReadingLayout(390, 220).compact, true);
  assert.equal(mobileReadingLayout(740, 500).compact, true);
});
