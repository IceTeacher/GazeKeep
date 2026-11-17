import { expect, test } from "vitest";
import { en } from "../../../src/data/en.ts";
import { zh } from "../../../src/data/zh.ts";

test("中英文常见问题条目数量一致且无空文案", () => {
  expect(zh.faq.items.length).toBeGreaterThanOrEqual(7);
  expect(zh.faq.items).toHaveLength(en.faq.items.length);

  for (const [index, zhItem] of zh.faq.items.entries()) {
    const enItem = en.faq.items[index];
    expect(zhItem.question.trim().length).toBeGreaterThan(0);
    expect(zhItem.answer.trim().length).toBeGreaterThan(0);
    expect(enItem.question.trim().length).toBeGreaterThan(0);
    expect(enItem.answer.trim().length).toBeGreaterThan(0);
  }
});

test("常见问题覆盖 v1.1.6 核心排查与设置路径", () => {
  const zhText = zh.faq.items.map((item) => `${item.question}\n${item.answer}`).join("\n");
  const enText = en.faq.items.map((item) => `${item.question}\n${item.answer}`).join("\n");

  for (const keyword of [
    "隐藏相机隐私指示器",
    "宽松：有正脸即可",
    "受限制的设置",
    "无限制",
    "省电模式暂停",
    "显示系统应用",
    "详细日志",
    "arm64-v8a",
  ]) {
    expect(zhText).toContain(keyword);
  }

  for (const keyword of [
    "Hide camera privacy indicator",
    "Relaxed: facing forward",
    "Allow restricted settings",
    "No restrictions",
    "Pause on Battery Saver",
    "Show system apps",
    "Detailed logs",
    "arm64-v8a",
  ]) {
    expect(enText).toContain(keyword);
  }
});
