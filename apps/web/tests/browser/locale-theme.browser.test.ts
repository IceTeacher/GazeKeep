import { afterEach, expect, test } from "vitest";
import { page } from "vitest/browser";
import { mountWebsite } from "./support/page.ts";

let site: Awaited<ReturnType<typeof mountWebsite>> | undefined;
afterEach(() => {
  site?.unmount();
  site = undefined;
});

test.each([
  { lang: "zh", other: "en", label: "English" },
  { lang: "en", other: "zh", label: "中文" },
] as const)("$lang 页面提供指向 $other 的语言切换，并把语言传给展示内屏", async ({ lang, other, label }) => {
  site = await mountWebsite(lang);
  const links = page.getByRole("link", { name: label, exact: true }).elements();
  expect(links.length).toBeGreaterThan(0);
  for (const link of links) expect(link.getAttribute("href")).toBe(`/${other}/`);
  await expect
    .poll(() => site!.showcase.calls.find(([name]) => name === "setLocaleAndTheme"), { timeout: 5000 })
    .toEqual(["setLocaleAndTheme", lang, true]);
});

test("主题切换写入偏好并同步展示内屏", async () => {
  site = await mountWebsite("zh", { theme: "dark" });
  await expect.poll(() => site!.showcase.calls.length, { timeout: 5000 }).toBeGreaterThan(0);
  await page.getByRole("button", { name: "切换明暗主题" }).click();
  expect(document.documentElement.dataset.theme).toBe("light");
  expect(localStorage.getItem("gazekeep-theme")).toBe("light");
  expect(site.showcase.calls.findLast(([name]) => name === "setLocaleAndTheme")).toEqual([
    "setLocaleAndTheme",
    "zh",
    false,
  ]);
});
