import { expect, test } from "vitest";
import { PHONE } from "../../../src/scripts/phone-geometry.ts";

test("镜像参考按真实屏幕等比定位，拇指不开放屏内覆盖", async () => {
  const { REFERENCE, referenceToScreen, thumbContact, PALM_CONTACT } =
    await import("../../../src/scripts/grip-geometry.ts");
  expect(referenceToScreen(REFERENCE.screenRight, REFERENCE.screenTop)).toEqual([0, 0]);
  expect(Math.abs(referenceToScreen(REFERENCE.screenLeft, REFERENCE.screenTop)[0] - PHONE.screenWidth)).toBeLessThan(
    1e-8,
  );
  expect(thumbContact.innerX).toBe(0);
  expect(PALM_CONTACT.inset).toBe(24);
  expect(PALM_CONTACT.top).toBe(PHONE.screenHeight * 0.45);
  expect(PALM_CONTACT.bottom).toBe(PHONE.screenHeight * 0.9);
});
test("机身前后深度与指腹接触范围受真实边框约束", async () => {
  const { GRIP_SURFACE, FINGERS, thumbContact } = await import("../../../src/scripts/grip-geometry.ts");
  expect(GRIP_SURFACE.rear).toBeLessThan(-PHONE.depth / 2);
  expect(GRIP_SURFACE.front).toBeGreaterThan(PHONE.depth / 2);
  expect(GRIP_SURFACE.inset).toBe(4);
  expect(FINGERS.length).toBe(4);
  for (let i = 0; i < FINGERS.length; i++) {
    const f = FINGERS[i];
    expect(f.center).toBe(PHONE.screenHeight * [0.33, 0.46, 0.6, 0.73][i]);
    expect(f.innerX).toBeGreaterThanOrEqual(PHONE.screenWidth - 4);
    expect(f.outerX).toBeGreaterThan(PHONE.screenWidth + PHONE.bezel);
    expect(f.rootX).toBeLessThan(PHONE.screenWidth);
    if (i) expect(f.center - FINGERS[i - 1].center).toBeGreaterThan((f.height + FINGERS[i - 1].height) / 2);
  }
  expect(thumbContact.innerX).toBeLessThanOrEqual(4);
});

test("手腕投影接点切线连续，单一拇指轮廓无外露闭合根部", async () => {
  const { gripPaths, forearmExtension } = await import("../../../src/scripts/grip-geometry.ts");
  const paths = gripPaths((x, y, z) => [x + z * 0.4, y + z * 0.1], 300, 1);
  expect(paths.silhouette).toBeTruthy();
  expect(!("thumb" in paths)).toBe(true);
  for (const { before, join, after, previous, next } of paths.joints) {
    const a = [join[0] - before[0], join[1] - before[1]],
      b = [after[0] - join[0], after[1] - join[1]];
    expect(Math.abs(a[0] * b[1] - a[1] * b[0])).toBeLessThan(1e-7);
    expect(a[0] * b[0] + a[1] * b[1]).toBeGreaterThan(0);
    for (let i = 0; i < 2; i++) {
      expect(Math.abs(join[i] - 2 * before[i] + previous[i] - (next[i] - 2 * after[i] + join[i]))).toBeLessThan(1e-7);
    }
  }
  expect((paths.silhouette.match(/M /g) || []).length).toBe(6);
  for (const hostLeft of [-100, 100, 500])
    expect(forearmExtension([50, 400], [70, 600], hostLeft, 1).left + hostLeft).toBe(-24);
});
test("实体遮挡取凸轮廓，指身指腹合为四条无横向接边的轮廓", async () => {
  const { gripPaths, projectedHull } = await import("../../../src/scripts/grip-geometry.ts");
  expect(
    projectedHull([
      [0, 0],
      [5, 0],
      [5, 5],
      [0, 5],
      [2, 2],
      [0, 0],
    ]),
  ).toEqual([
    [0, 0],
    [5, 0],
    [5, 5],
    [0, 5],
  ]);
  const samples: { x: number; y: number; z: number }[] = [];
  const project = (x: number, y: number, z: number) => {
    samples.push({ x, y, z });
    return [x + z / 2, y];
  };
  const a = gripPaths(project, 300, 1);
  expect((a.contacts.match(/M /g) || []).length).toBe(4);
  expect(a.palmContact).toBeTruthy();
  expect(a.palmWindow).toBeTruthy();
  expect(samples.some((p) => p.z < 0 && p.x > 392)).toBe(true);
  expect(a.protection.includes("Q ")).toBe(true);
  const b = gripPaths(project, 300, 1);
  expect(a).toEqual(b);
  expect(!a.silhouette.match(/NaN|Infinity/)).toBe(true);
});

test("屏前掌根连续连接腕掌，屏内弧线只落在许可窗口", async () => {
  const { gripPaths, PALM_CONTACT } = await import("../../../src/scripts/grip-geometry.ts");
  const a = gripPaths((x, y) => [x, y], 300, 1);
  const numbers = a.palmContact.match(/-?\d+(?:\.\d+)?(?:e[+-]?\d+)?/gi)!.map(Number);
  const points = [];
  for (let i = 0; i < numbers.length; i += 2) points.push(numbers.slice(i, i + 2));
  const xs = points.map((p) => p[0]),
    ys = points.map((p) => p[1]);
  const width = Math.max(...xs) - Math.min(...xs),
    height = Math.max(...ys) - Math.min(...ys);
  expect(width > 100 && height > 200).toBe(true);
  expect(Math.abs(Math.max(...xs) - 24)).toBeLessThan(1e-8);
  expect(Math.max(...ys)).toBeLessThanOrEqual(PHONE.screenHeight + 4);
  for (const [x, y] of points.filter((p) => p[0] > 0)) {
    expect(x).toBeLessThanOrEqual(24);
    expect(y >= PALM_CONTACT.top && y <= PALM_CONTACT.bottom).toBe(true);
  }
  const window = a.palmWindow.match(/-?\d+(?:\.\d+)?/g)!.map(Number);
  expect(window).toEqual([
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
  const { forearmExtension } = await import("../../../src/scripts/grip-geometry.ts");
  for (const left of [-100, 100, 500])
    for (const progress of [0, 0.25, 0.5, 0.75, 1]) {
      const arm = forearmExtension([50, 400], [70, 600], left, progress);
      expect(arm.left + left).toBeLessThanOrEqual(-24);
      expect(arm.top).toBe(400);
      expect(arm.bottom).toBe(600);
    }
});

test("实拍拇指尖位于机身顶部以下", async () => {
  const { thumbContact, gripPaths } = await import("../../../src/scripts/grip-geometry.ts");
  expect(thumbContact.tipY >= 50 && thumbContact.tipY <= 100).toBe(true);
  const values = gripPaths((x, y) => [x, y], 300, 1)
    .silhouette.split("Z")[0]
    .match(/-?\d+(?:\.\d+)?(?:e[+-]?\d+)?/gi)!
    .map(Number);
  expect(Math.min(...values.filter((_, i) => i % 2 === 1))).toBe(thumbContact.tipY);
});
