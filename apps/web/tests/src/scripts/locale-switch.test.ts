import { expect, test } from "vitest";
import { alternateLocale, localeFromPath, resolveLocalePath } from "../../../src/scripts/locale-switch.ts";

test("目标语言与路由路径计算正确", () => {
  expect(alternateLocale("zh")).toBe("en");
  expect(alternateLocale("en")).toBe("zh");
  expect(resolveLocalePath("en", "home")).toBe("/en/");
  expect(resolveLocalePath("zh", "home", "#whitelist")).toBe("/zh/#whitelist");
  expect(resolveLocalePath("en", "changelog")).toBe("/en/changelog/");
  expect(localeFromPath("/en/changelog/")).toBe("en");
  expect(localeFromPath("/zh/")).toBe("zh");
});
