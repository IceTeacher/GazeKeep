import { expect, test } from "vitest";
import { mountWebsite, scrollToY } from "../_support/page.ts";

test("页面卸载后停止响应滚动与交互，之后可以重新初始化", async () => {
  const site = await mountWebsite("zh");
  const stage = document.querySelector<HTMLElement>("#phone-stage")!;
  await scrollToY(0);
  dispatchEvent(new PageTransitionEvent("pagehide", { persisted: false }));
  const frozen = stage.dataset.scene;
  await scrollToY(innerHeight * 2);
  expect(stage.dataset.scene).toBe(frozen);
  document.querySelector<HTMLButtonElement>("#theme-toggle")!.click();
  expect(document.documentElement.dataset.theme).toBe("dark");
  site.unmount();

  const again = await mountWebsite("zh");
  const next = document.querySelector<HTMLElement>("#phone-stage")!;
  await scrollToY(innerHeight * 2);
  expect(Number(next.dataset.scene)).toBeGreaterThan(0);
  again.unmount();
});

test("往返缓存的 pagehide 不释放页面", async () => {
  const site = await mountWebsite("zh");
  dispatchEvent(new PageTransitionEvent("pagehide", { persisted: true }));
  // 页面仍响应交互：主题按钮从深色切到下一档（跟随系统）。
  document.querySelector<HTMLButtonElement>("#theme-toggle")!.click();
  await expect.poll(() => document.documentElement.dataset.themePreference, { timeout: 5_000 }).toBe("system");
  site.unmount();
});
