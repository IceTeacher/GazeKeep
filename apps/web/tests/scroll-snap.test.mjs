import test from "node:test";
import assert from "node:assert/strict";
import { buildSnapStops, nextSnapStop, createWheelGesture, shouldSnap } from "../src/scripts/snap-policy.mjs";

test("长内容补齐逐屏停靠点，保留最后一屏且不越界", () => {
  assert.deepEqual(buildSnapStops([0, 900, 3300], 900, 3600), [0, 900, 1800, 2700, 3300, 3600]);
  assert.deepEqual(buildSnapStops([0, 800], 900, 300), [0, 300]);
});
test("从非整屏位置前进、后退和页首尾均选择相邻停靠点", () => {
  const stops = [0, 900, 1800, 2100];
  assert.equal(nextSnapStop(stops, 450, 1), 900);
  assert.equal(nextSnapStop(stops, 450, -1), 0);
  assert.equal(nextSnapStop(stops, 900, 1), 1800);
  assert.equal(nextSnapStop(stops, 0, -1), 0);
  assert.equal(nextSnapStop(stops, 2100, 1), 2100);
});
test("一次触控板手势含连续惯性事件时只翻一屏，新手势才再次翻页", () => {
  const gesture = createWheelGesture();
  assert.equal(gesture.push(12, 0, false), 0);
  assert.equal(gesture.push(30, 20, false), 1);
  assert.equal(gesture.push(120, 100, true), 0);
  assert.equal(gesture.push(80, 500, false), 0);
  assert.equal(gesture.push(70, 550, false), 0);
  assert.equal(gesture.push(80, 900, false), 0);
  assert.equal(gesture.push(-60, 1400, false), -1);
});

test("隐私入口向下及后续正文自由滚动，入口向上恢复功能区吸附", () => {
  assert.equal(shouldSnap(1800, 1, 2700), true);
  assert.equal(shouldSnap(2700, 1, 2700), false);
  assert.equal(shouldSnap(2700, -1, 2700), true);
  assert.equal(shouldSnap(2850, -1, 2700), false);
  assert.equal(shouldSnap(2850, 1, 2700), false);
});
