import { expect, test } from "vitest";
import { mountWebsite, scrollToY } from "./support/page.ts";

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
  document.querySelector<HTMLButtonElement>("#theme-toggle")!.click();
  expect(document.documentElement.dataset.theme).toBe("light");
  site.unmount();
});
