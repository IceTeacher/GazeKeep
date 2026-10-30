import { expect, test } from "vitest";
import { PHONE, fitPhone, mapPlanePoint, projectPlane, unmapPlanePoint } from "../../../src/scripts/phone-geometry.ts";

test("系统装饰与应用共用逻辑画布和缩放比", () => {
  expect(PHONE.screenHeight).toBe(PHONE.statusHeight + PHONE.appHeight + PHONE.navHeight);
  expect(PHONE.screenHeight).toBe(848);
  expect(PHONE.statusHeight).toBe(40);
  expect(PHONE.appWidth).toBe(392);
  expect(PHONE.appHeight).toBe(784);
  expect(fitPhone(216).scale / fitPhone(432).scale).toBe(0.5);
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
      const out = mapPlanePoint(m, p[0], p[1]);
      expect(Math.hypot(out[0] - corners[i][0], out[1] - corners[i][1])).toBeLessThan(1e-7);
    }
  }
});

test("屏幕四边边框及圆角保持等宽", () => {
  const horizontal = (PHONE.width - PHONE.screenWidth) / 2;
  const vertical = (PHONE.height - PHONE.screenHeight) / 2;
  expect(horizontal).toBe(12);
  expect(vertical).toBe(horizontal);
  expect(PHONE.radius - PHONE.screenRadius).toBe(horizontal);
  expect(PHONE.bezel).toBe(horizontal);
});

test("投影后的点击位置可还原为屏幕逻辑坐标", () => {
  const m = projectPlane(PHONE.screenWidth, PHONE.screenHeight, [
    [40, 12],
    [250, 30],
    [228, 470],
    [18, 440],
  ]);
  for (const [x, y] of [
    [0, 0],
    [196, 424],
    [300, 790],
    [391, 847],
  ]) {
    const [u, v] = mapPlanePoint(m, x, y);
    const [rx, ry] = unmapPlanePoint(m, u, v);
    expect(rx).toBeCloseTo(x, 6);
    expect(ry).toBeCloseTo(y, 6);
  }
});
