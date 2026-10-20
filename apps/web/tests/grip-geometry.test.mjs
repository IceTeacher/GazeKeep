import test from "node:test";
import assert from "node:assert/strict";
import { PHONE } from "../src/scripts/phone-geometry.mjs";

test("镜像参考按真实屏幕等比定位，拇指不开放屏内覆盖", async () => {
  const { REFERENCE, referenceToScreen, thumbContact, PALM_CONTACT } = await import("../src/scripts/grip-geometry.mjs");
  assert.deepEqual(referenceToScreen(REFERENCE.screenRight, REFERENCE.screenTop), [0, 0]);
  assert.ok(Math.abs(referenceToScreen(REFERENCE.screenLeft, REFERENCE.screenTop)[0] - PHONE.screenWidth) < 1e-8);
  assert.equal(thumbContact.innerX, 0);
  assert.equal(PALM_CONTACT.inset, 24);
  assert.equal(PALM_CONTACT.top, PHONE.screenHeight * 0.45);
  assert.equal(PALM_CONTACT.bottom, PHONE.screenHeight * 0.9);
});
test("机身前后深度与指腹接触范围受真实边框约束", async () => {
  const { GRIP_SURFACE, FINGERS, thumbContact } = await import("../src/scripts/grip-geometry.mjs");
  assert.ok(GRIP_SURFACE.rear < -PHONE.depth / 2);
  assert.ok(GRIP_SURFACE.front > PHONE.depth / 2);
  assert.equal(GRIP_SURFACE.inset, 4);
  assert.equal(FINGERS.length, 4);
  for (let i = 0; i < FINGERS.length; i++) {
    const f = FINGERS[i];
    assert.equal(f.center, PHONE.screenHeight * [0.33, 0.46, 0.6, 0.73][i]);
    assert.ok(f.innerX >= PHONE.screenWidth - 4);
    assert.ok(f.outerX > PHONE.screenWidth + PHONE.bezel);
    assert.ok(f.rootX < PHONE.screenWidth);
    if (i) assert.ok(f.center - FINGERS[i - 1].center > (f.height + FINGERS[i - 1].height) / 2);
  }
  assert.ok(thumbContact.innerX <= 4);
});

test("手腕投影接点切线连续，单一拇指轮廓无外露闭合根部", async () => {
  const { gripPaths, forearmExtension } = await import("../src/scripts/grip-geometry.mjs");
  const paths = gripPaths((x, y, z) => [x + z * 0.4, y + z * 0.1], 300, 1, 800, 900);
  assert.ok(paths.silhouette);
  assert.equal(paths.thumb, undefined);
  for (const { before, join, after, previous, next } of paths.joints) {
    const a = [join[0] - before[0], join[1] - before[1]],
      b = [after[0] - join[0], after[1] - join[1]];
    assert.ok(Math.abs(a[0] * b[1] - a[1] * b[0]) < 1e-7);
    assert.ok(a[0] * b[0] + a[1] * b[1] > 0);
    for (let i = 0; i < 2; i++) {
      assert.ok(Math.abs(join[i] - 2 * before[i] + previous[i] - (next[i] - 2 * after[i] + join[i])) < 1e-7);
    }
  }
  assert.equal((paths.silhouette.match(/M /g) || []).length, 6);
  for (const hostLeft of [-100, 100, 500])
    assert.equal(forearmExtension([50, 400], [70, 600], hostLeft, 1).left + hostLeft, -24);
});
test("实体遮挡取凸轮廓，指身指腹合为四条无横向接边的轮廓", async () => {
  const { gripPaths, projectedHull } = await import("../src/scripts/grip-geometry.mjs");
  assert.deepEqual(
    projectedHull([
      [0, 0],
      [5, 0],
      [5, 5],
      [0, 5],
      [2, 2],
      [0, 0],
    ]),
    [
      [0, 0],
      [5, 0],
      [5, 5],
      [0, 5],
    ],
  );
  const samples = [];
  const project = (x, y, z) => {
    samples.push({ x, y, z });
    return [x + z / 2, y];
  };
  const a = gripPaths(project, 300, 1, 800, 900);
  assert.equal((a.contacts.match(/M /g) || []).length, 4);
  assert.ok(a.palmContact);
  assert.ok(a.palmWindow);
  assert.ok(samples.some((p) => p.z < 0 && p.x > 392));
  assert.ok(a.protection.includes("Q "));
  const b = gripPaths(project, 300, 1, 800, 900);
  assert.deepEqual(a, b);
  assert.ok(!a.silhouette.match(/NaN|Infinity/));
});

test("屏前掌根连续连接腕掌，屏内弧线只落在许可窗口", async () => {
  const { gripPaths, PALM_CONTACT } = await import("../src/scripts/grip-geometry.mjs");
  const a = gripPaths((x, y) => [x, y], 300, 1);
  const numbers = a.palmContact.match(/-?\d+(?:\.\d+)?(?:e[+-]?\d+)?/gi).map(Number);
  const points = [];
  for (let i = 0; i < numbers.length; i += 2) points.push(numbers.slice(i, i + 2));
  const xs = points.map((p) => p[0]),
    ys = points.map((p) => p[1]);
  const width = Math.max(...xs) - Math.min(...xs),
    height = Math.max(...ys) - Math.min(...ys);
  assert.ok(width > 100 && height > 200);
  assert.ok(Math.abs(Math.max(...xs) - 24) < 1e-8);
  assert.ok(Math.max(...ys) <= PHONE.screenHeight + 4);
  for (const [x, y] of points.filter((p) => p[0] > 0)) {
    assert.ok(x <= 24);
    assert.ok(y >= PALM_CONTACT.top && y <= PALM_CONTACT.bottom);
  }
  const window = a.palmWindow.match(/-?\d+(?:\.\d+)?/g).map(Number);
  assert.deepEqual(window, [
    -1000,
    PALM_CONTACT.top,
    24,
    PALM_CONTACT.top,
    24,
    PALM_CONTACT.bottom,
    -1000,
    PALM_CONTACT.bottom,
  ]);
});

test("进入过程前臂断面始终在视口之外，手掌不随延展改变", async () => {
  const { forearmExtension } = await import("../src/scripts/grip-geometry.mjs");
  for (const left of [-100, 100, 500])
    for (const progress of [0, 0.25, 0.5, 0.75, 1]) {
      const arm = forearmExtension([50, 400], [70, 600], left, progress);
      assert.ok(arm.left + left <= -24);
      assert.equal(arm.top, 400);
      assert.equal(arm.bottom, 600);
    }
});

test("实拍拇指尖位于机身顶部以下", async () => {
  const { thumbContact, gripPaths } = await import("../src/scripts/grip-geometry.mjs");
  assert.ok(thumbContact.tipY >= 50 && thumbContact.tipY <= 100);
  const values = gripPaths((x, y) => [x, y], 300, 1)
    .silhouette.split("Z")[0]
    .match(/-?\d+(?:\.\d+)?(?:e[+-]?\d+)?/gi)
    .map(Number);
  assert.equal(Math.min(...values.filter((_, i) => i % 2 === 1)), thumbContact.tipY);
});
