import { expect, test } from "vitest";
import { assertPrivacyInPolicy, extractPrivacy } from "../../scripts/privacy.ts";

test("隐私资源解析 XML 实体并拒绝缺失或重复", () => {
  expect(
    extractPrivacy(
      '<resources><string name="privacy_line_1">A &amp; B</string><string name="privacy_line_2">C</string><string name="privacy_line_3">D</string></resources>',
    ),
  ).toEqual(["A & B", "C", "D"]);
  expect(() => extractPrivacy("<resources/>")).toThrow(/privacy_line_1/);
  expect(() =>
    extractPrivacy('<string name="privacy_line_1">A</string><string name="privacy_line_1">B</string>'),
  ).toThrow(/privacy_line_1/);
});

test("隐私文档同时约束中英文资源", () => {
  const policy = "中文一\n中文二\n中文三\nEnglish one\nEnglish two\nEnglish three";
  expect(() =>
    assertPrivacyInPolicy(policy, ["中文一", "中文二", "中文三"], ["English one", "English two", "English three"]),
  ).not.toThrow();
  expect(() =>
    assertPrivacyInPolicy(policy, ["中文一", "中文二", "中文三"], ["English one", "Changed", "English three"]),
  ).toThrow(/Changed/);
});
