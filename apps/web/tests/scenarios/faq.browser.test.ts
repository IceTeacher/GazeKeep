import { afterEach, expect, test } from "vitest";
import { page } from "vitest/browser";
import { en } from "../../src/data/en.ts";
import { zh } from "../../src/data/zh.ts";
import { mountWebsite } from "../_support/page.ts";

let site: Awaited<ReturnType<typeof mountWebsite>> | undefined;
afterEach(() => {
  site?.unmount();
  site = undefined;
});

test("常见问题支持展开收起、分段换行并在原地切换语言时同步更新全部条目", async () => {
  await page.viewport(1440, 900);
  site = await mountWebsite("zh");

  const items = [...document.querySelectorAll<HTMLElement>("#faq .faq-item")];
  expect(items).toHaveLength(zh.faq.items.length);

  const firstTrigger = items[0].querySelector<HTMLButtonElement>(".faq-trigger")!;
  const firstAnswer = items[0].querySelector<HTMLElement>(".faq-answer")!;
  const firstParagraph = firstAnswer.querySelector<HTMLElement>("p")!;

  expect(firstTrigger.getAttribute("aria-expanded")).toBe("false");
  expect(firstAnswer.inert).toBe(true);
  expect(getComputedStyle(firstParagraph).whiteSpace).toBe("pre-line");

  firstTrigger.click();
  expect(firstTrigger.getAttribute("aria-expanded")).toBe("true");
  expect(firstAnswer.inert).toBe(false);

  await page.getByRole("link", { name: "Switch to English" }).click();
  await expect.poll(() => document.documentElement.lang, { timeout: 5_000 }).toBe("en");

  items.forEach((item, index) => {
    expect(item.querySelector(".faq-question")!.textContent).toBe(en.faq.items[index].question);
    expect(item.querySelector(".faq-answer p")!.textContent).toBe(en.faq.items[index].answer);
  });
});
