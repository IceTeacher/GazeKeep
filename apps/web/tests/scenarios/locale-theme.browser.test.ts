import { afterEach, expect, test } from "vitest";
import { commands, page } from "vitest/browser";
import { mountWebsite, SHOWCASE_TIMEOUT } from "../_support/page.ts";

let site: Awaited<ReturnType<typeof mountWebsite>> | undefined;
afterEach(async () => {
  site?.unmount();
  site = undefined;
  await commands.emulateMedia({ colorScheme: null, reducedMotion: null });
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
    .poll(() => site!.showcase.calls.find(([name]) => name === "setLocaleAndTheme"), { timeout: SHOWCASE_TIMEOUT })
    .toEqual(["setLocaleAndTheme", lang, true]);
});

const toggle = (lang: "zh" | "en" = "zh") =>
  page.getByRole("button", { name: lang === "zh" ? /切换主题/ : /Change theme/ });
const lastAppearance = () => site!.showcase.calls.findLast(([name]) => name === "setLocaleAndTheme");

test("未保存偏好时默认跟随系统，系统明暗变化同步页面与展示内屏", async () => {
  await commands.emulateMedia({ colorScheme: "dark" });
  site = await mountWebsite("zh", { theme: "system" });
  const root = document.documentElement;
  expect(root.dataset.themePreference).toBe("system");
  expect(root.dataset.theme).toBe("dark");
  await expect.poll(lastAppearance, { timeout: SHOWCASE_TIMEOUT }).toEqual(["setLocaleAndTheme", "zh", true]);
  await expect.element(toggle()).toHaveAccessibleName("切换主题（跟随系统）");
  await commands.emulateMedia({ colorScheme: "light" });
  await expect.poll(() => root.dataset.theme).toBe("light");
  expect(lastAppearance()).toEqual(["setLocaleAndTheme", "zh", false]);
  expect(localStorage.getItem("gazekeep-theme")).toBeNull();
});

test("主题按钮按 跟随系统 → 浅色 → 深色 → 跟随系统 循环，只保存显式选择", async () => {
  await commands.emulateMedia({ colorScheme: "light" });
  site = await mountWebsite("zh", { theme: "system" });
  const root = document.documentElement;
  await expect.poll(() => site!.showcase.calls.length, { timeout: SHOWCASE_TIMEOUT }).toBeGreaterThan(0);
  const steps = [
    { preference: "light", theme: "light", stored: "light", label: "浅色" },
    { preference: "dark", theme: "dark", stored: "dark", label: "深色" },
    { preference: "system", theme: "light", stored: null, label: "跟随系统" },
  ] as const;
  for (const step of steps) {
    await toggle().click();
    // 偏好与存储在点击时立即更新，明暗与内屏换肤随视图过渡应用。
    expect(root.dataset.themePreference).toBe(step.preference);
    expect(localStorage.getItem("gazekeep-theme")).toBe(step.stored);
    await expect.poll(() => root.dataset.theme, { timeout: 5_000 }).toBe(step.theme);
    await expect.element(toggle()).toHaveAccessibleName(`切换主题（${step.label}）`);
    await expect.poll(lastAppearance).toEqual(["setLocaleAndTheme", "zh", step.theme === "dark"]);
    await expect.poll(() => root.dataset.themeTransition, { timeout: 5_000 }).toBeUndefined();
  }
});

test("深浅主题共用同一主题绿与手部肤色，只切换表面色", async () => {
  site = await mountWebsite("zh", { theme: "light" });
  const root = document.documentElement;
  const hand = document.querySelector('[data-grip-path="silhouette"]')!;
  const sample = () => ({
    accent: getComputedStyle(root).getPropertyValue("--accent").trim(),
    hand: getComputedStyle(hand).fill,
    bg: getComputedStyle(root).getPropertyValue("--bg").trim(),
  });
  const light = sample();
  await toggle().click();
  await expect.poll(() => root.dataset.theme).toBe("dark");
  const dark = sample();
  expect(dark.accent).toBe(light.accent);
  expect(dark.hand).toBe(light.hand);
  expect(dark.bg).not.toBe(light.bg);
});

test("切换实际明暗时以视图过渡揭示，减少动态或明暗不变时直接切换", async () => {
  await commands.emulateMedia({ colorScheme: "dark" });
  site = await mountWebsite("zh", { theme: "dark" });
  const original = document.startViewTransition.bind(document);
  let transitions = 0;
  document.startViewTransition = ((update: ViewTransitionUpdateCallback) => {
    transitions++;
    return original(update);
  }) as typeof document.startViewTransition;
  try {
    // 深色 → 跟随系统（系统为深色）：实际明暗不变。
    await toggle().click();
    await expect.poll(() => document.documentElement.dataset.themePreference).toBe("system");
    expect(transitions).toBe(0);
    // 跟随系统（深色）→ 浅色：播放揭示。
    await toggle().click();
    await expect.poll(() => document.documentElement.dataset.theme).toBe("light");
    expect(transitions).toBe(1);
    await expect.poll(() => document.documentElement.dataset.themeTransition, { timeout: 5_000 }).toBeUndefined();
    // 减少动态：浅色 → 深色直接切换。
    await commands.emulateMedia({ reducedMotion: "reduce" });
    await toggle().click();
    await expect.poll(() => document.documentElement.dataset.theme).toBe("dark");
    expect(transitions).toBe(1);
  } finally {
    document.startViewTransition = original;
  }
});
