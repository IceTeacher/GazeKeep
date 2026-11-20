import { afterEach, expect, inject, test } from "vitest";
import { page, userEvent } from "vitest/browser";
import { mountChangelog, mountWebsite } from "../_support/page.ts";

let mounted: { unmount: () => void } | undefined;
let originalFetch: typeof window.fetch | undefined;

afterEach(() => {
  mounted?.unmount();
  mounted = undefined;
  if (originalFetch) {
    window.fetch = originalFetch;
    originalFetch = undefined;
  }
});

test("平板与移动端导航提供抽屉菜单、键盘关闭、外部点击关闭与足够触控区域", async () => {
  await page.viewport(900, 800);
  mounted = await mountWebsite("zh");
  const toggle = document.querySelector<HTMLButtonElement>("#menu-toggle")!;
  const navLinks = document.querySelector<HTMLElement>(".nav-links")!;
  const mobileMenu = document.querySelector<HTMLElement>("#mobile-menu")!;

  expect(getComputedStyle(navLinks).display).toBe("none");
  expect(getComputedStyle(toggle).display).not.toBe("none");
  expect(mobileMenu.inert).toBe(true);

  await page.viewport(390, 844);
  toggle.click();
  expect(toggle.getAttribute("aria-expanded")).toBe("true");
  expect(mobileMenu.inert).toBe(false);

  const mobileLinks = [...document.querySelectorAll<HTMLElement>(".mobile-menu-links a")];
  expect(mobileLinks.length).toBeGreaterThanOrEqual(5);
  for (const link of mobileLinks) {
    expect(link.getBoundingClientRect().height).toBeGreaterThanOrEqual(44);
    expect(getComputedStyle(link).borderRadius).toBe("0px");
  }

  const menuRect = mobileMenu.getBoundingClientRect();
  const menuStyle = getComputedStyle(mobileMenu);
  expect(Math.abs(menuRect.width - innerWidth)).toBeLessThanOrEqual(1);
  expect(menuStyle.borderTopLeftRadius).toBe("0px");
  expect(menuStyle.borderTopWidth).toBe("0px");

  const mobileFoot = document.querySelector<HTMLElement>(".mobile-menu-foot")!;
  expect(getComputedStyle(mobileFoot).borderTopWidth).toBe("0px");
  const mobileCta = document.querySelector<HTMLElement>(".mobile-menu-cta")!;
  const ctaRect = mobileCta.getBoundingClientRect();
  expect(ctaRect.height).toBeGreaterThanOrEqual(44);
  expect(ctaRect.width).toBeLessThan(innerWidth * 0.6);

  await userEvent.keyboard("{Escape}");
  expect(toggle.getAttribute("aria-expanded")).toBe("false");
  expect(mobileMenu.inert).toBe(true);

  toggle.click();
  expect(toggle.getAttribute("aria-expanded")).toBe("true");
  document.querySelector("main")!.dispatchEvent(new MouseEvent("click", { bubbles: true }));
  expect(toggle.getAttribute("aria-expanded")).toBe("false");

  const footer = document.querySelector("footer")!;
  expect(document.documentElement.scrollWidth).toBeLessThanOrEqual(innerWidth + 1);
  for (const link of footer.querySelectorAll<HTMLElement>("a")) {
    expect(link.getBoundingClientRect().height).toBeGreaterThanOrEqual(44);
  }
});

test("更新日志页面渲染全部版本、表格与可折叠启用步骤，并支持原地语言切换", async () => {
  await page.viewport(1280, 900);
  mounted = await mountChangelog("zh");

  const versions = [...document.querySelectorAll<HTMLElement>(".changelog-card")].map((el) => el.dataset.version);
  expect(versions).toEqual(["v1.1.7", "v1.1.6", "v1.1.5", "v1.1.4", "v1.1.3", "v1.1.2"]);
  expect(document.documentElement.dataset.page).toBe("changelog");
  expect(getComputedStyle(document.documentElement).scrollSnapType).toBe("none");
  expect(document.querySelector<HTMLElement>(".nav-wrap")!.dataset.zone).toBe("content");
  expect(document.querySelectorAll(".changelog-card table").length).toBeGreaterThanOrEqual(2);

  const setup = document.querySelector<HTMLDetailsElement>(".changelog-card .changelog-setup")!;
  expect(setup.open).toBe(false);
  expect(getComputedStyle(setup).borderRadius).toBe("0px");
  expect(getComputedStyle(setup).backgroundColor).toBe("rgba(0, 0, 0, 0)");

  const hero = document.querySelector<HTMLElement>(".changelog-hero")!;
  expect(getComputedStyle(hero).borderBottomWidth).toBe("0px");
  const cardBody = document.querySelector<HTMLElement>(".changelog-body")!;
  const cardBodyStyle = getComputedStyle(cardBody);
  expect(cardBodyStyle.borderRadius).toBe("0px");
  expect(cardBodyStyle.borderTopWidth).toBe("0px");
  expect(cardBodyStyle.backgroundColor).toBe("rgba(0, 0, 0, 0)");
  expect(cardBody.getBoundingClientRect().width).toBeGreaterThan(800);

  const addedBadge = document.querySelector<HTMLElement>('.changelog-badge[data-kind="added"]')!;
  const improvedBadge = document.querySelector<HTMLElement>('.changelog-badge[data-kind="improved"]')!;
  expect(getComputedStyle(addedBadge).borderRadius).toBe("0px");
  expect(getComputedStyle(addedBadge).color).toBe(getComputedStyle(improvedBadge).color);

  setup.querySelector("summary")!.click();
  expect(setup.open).toBe(true);

  document.querySelector("#v1\\.1\\.4")!.scrollIntoView({ behavior: "instant", block: "start" });
  const scrollBefore = scrollY;
  expect(scrollBefore).toBeGreaterThan(100);

  const enChangelog = inject("changelogPages").en;
  originalFetch = window.fetch.bind(window);
  window.fetch = (async (input, init) => {
    if (String(input) === "/en/changelog/") {
      return new Response(
        `<html><head><title>Changelog - GazeKeep</title></head><body>${enChangelog.body}</body></html>`,
        {
          status: 200,
          headers: { "content-type": "text/html" },
        },
      );
    }
    return originalFetch!(input, init);
  }) as typeof window.fetch;

  await page.getByRole("link", { name: "Switch to English" }).click();
  await expect.poll(() => document.documentElement.lang, { timeout: 5_000 }).toBe("en");
  expect(location.pathname).toBe("/en/changelog/");
  expect(document.querySelector(".changelog-hero h1")!.textContent).toBe("Changelog");
  expect(scrollY).toBeGreaterThan(100);
  expect(Math.abs(scrollY - scrollBefore)).toBeLessThan(150);
});
