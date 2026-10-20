import test from "node:test";
import assert from "node:assert/strict";
import { screenDim, mobileStoryFollow } from "../src/scripts/story-motion.mjs";

test("第三幕至后端区：进入后端区时仍全黑，随滚动连续渐亮，正反一致", () => {
  const at = (progress) => screenDim({ progress, inBackend: progress >= 3.5 });
  assert.equal(at(3.4), 1);
  assert.equal(at(3.5), 1);
  assert.ok(at(3.75) > 0.1 && at(3.75) < 0.9);
  assert.equal(at(4.05), 0);
  let last = 1;
  for (let p = 3.5; p <= 4.05; p += 0.01) {
    const d = at(p);
    assert.ok(d <= last + 1e-9);
    last = d;
  }
  assert.equal(screenDim({ progress: 3.6, inWhitelist: true }), 0);
});

test("移动端后端设备槽从视口下方进入时由黑渐亮", () => {
  const at = (slotTop) => screenDim({ progress: 3.6, mobile: true, inBackend: true, slotTop, viewport: 844 });
  assert.equal(at(844), 1);
  assert.ok(at(844 * 0.62) > 0 && at(844 * 0.62) < 1);
  assert.equal(at(844 * 0.4), 0);
  // 三幕面板释放后、后端设备槽出现前保持黑屏，不在离场的设备上提前亮屏
  for (const progress of [3.3, 3.6, 3.9, 4.2]) assert.equal(screenDim({ progress, mobile: true }), 1);
});

test("紧凑模式只按阶段状态显示黑屏", () => {
  assert.equal(screenDim({ progress: 3, compact: true, status: "dim_fallback" }), 1);
  assert.equal(screenDim({ progress: 3, compact: true, status: "backend_lsposed" }), 0);
});

test("移动端视口固定段位置与滚动无关，前后边界与文档锚定连续", () => {
  const storyTop = 2000,
    releaseAt = 4200,
    header = 60;
  assert.equal(mobileStoryFollow(storyTop - header - 1, storyTop, releaseAt, header).mode, "before");
  const fixed = [storyTop - header, 2600, 3333, releaseAt].map((y) =>
    mobileStoryFollow(y, storyTop, releaseAt, header),
  );
  for (const f of fixed) assert.deepEqual(f, { mode: "fixed", top: header + 16 });
  const after = [releaseAt + 1, releaseAt + 500].map((y) => mobileStoryFollow(y, storyTop, releaseAt, header));
  assert.deepEqual(after[0], after[1]);
  assert.equal(after[0].mode, "absolute");
  // 释放瞬间文档坐标对应的视口位置与固定段相同
  assert.equal(after[0].y - releaseAt, header + 16);
});
