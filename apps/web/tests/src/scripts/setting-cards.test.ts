import { expect, test } from "vitest";
import { DEFAULT_SETTINGS, settingArc, settingValue } from "../../../src/scripts/setting-cards.ts";

const values = { seconds: "{n} 秒", minutes: "{n} 分钟", standard: "标准", loose: "宽松", on: "开启", off: "关闭" };

test("卡片默认值与 App 默认设置一致：提前 8 秒、标准灵敏度、30 分钟、显示指示器", () => {
  expect(DEFAULT_SETTINGS).toEqual({ leadSeconds: 8, loose: false, holdMinutes: 30, hideIndicator: false });
  const text = (["lead", "sensitivity", "max_hold", "hide_indicator"] as const).map((id) =>
    settingValue(id, DEFAULT_SETTINGS, values),
  );
  expect(text).toEqual(["8 秒", "标准", "30 分钟", "关闭"]);
});

test("演示修改后的取值按模板格式化", () => {
  const tuned = { leadSeconds: 5, loose: true, holdMinutes: 60, hideIndicator: true };
  expect(settingValue("lead", tuned, values)).toBe("5 秒");
  expect(settingValue("sensitivity", tuned, values)).toBe("宽松");
  expect(settingValue("max_hold", tuned, values)).toBe("60 分钟");
  expect(settingValue("hide_indicator", tuned, values)).toBe("开启");
  // 自定义数值原样代入模板。
  expect(settingValue("max_hold", { ...tuned, holdMinutes: 1440 }, values)).toBe("1440 分钟");
});

test("刻度环按取值比例描绘：30 分钟为半圈，较长、较宽松与开启描得更满，超出量程封顶", () => {
  expect(settingArc("max_hold", DEFAULT_SETTINGS)).toBe(50);
  expect(settingArc("max_hold", { ...DEFAULT_SETTINGS, holdMinutes: 60 })).toBe(100);
  expect(settingArc("max_hold", { ...DEFAULT_SETTINGS, holdMinutes: 600 })).toBe(100);
  expect(settingArc("lead", { ...DEFAULT_SETTINGS, leadSeconds: 5 })).toBeLessThan(
    settingArc("lead", DEFAULT_SETTINGS),
  );
  expect(settingArc("sensitivity", { ...DEFAULT_SETTINGS, loose: true })).toBeGreaterThan(
    settingArc("sensitivity", DEFAULT_SETTINGS),
  );
  expect(settingArc("hide_indicator", DEFAULT_SETTINGS)).toBe(0);
  expect(settingArc("hide_indicator", { ...DEFAULT_SETTINGS, hideIndicator: true })).toBe(100);
});
