import { afterEach, expect, test } from "vitest";
import { page } from "vitest/browser";
import { mountWebsite, type ShowcaseControl } from "./support/page.ts";

let site: Awaited<ReturnType<typeof mountWebsite>> | undefined;
afterEach(() => {
  site?.unmount();
  site = undefined;
});

const screen = () => document.querySelector<HTMLElement>(".phone-screen")!;

test("内屏就绪后重放最新页面与状态，并隐藏加载提示", async () => {
  site = await mountWebsite("zh");
  await expect.poll(() => screen().dataset.ready, { timeout: 5000 }).toBe("true");
  expect(document.querySelector<HTMLButtonElement>("#retry-showcase")!.hidden).toBe(true);
  const names = site.showcase.calls.map(([name]) => name);
  expect(names).toEqual(expect.arrayContaining(["setLocaleAndTheme", "setScreen", "setDemoStatus"]));
});

test("Wasm 与 JS 都失败时显示重试，重试成功后恢复内屏", async () => {
  const showcase: ShowcaseControl = { mode: "ready", modes: ["error", "error"], calls: [] };
  site = await mountWebsite("zh", { showcase });
  const retry = page.getByRole("button", { name: "重新加载" });
  await expect.element(retry, { timeout: 5000 }).toBeVisible();
  expect(document.querySelector("#load-message")!.textContent).toBe("界面暂时不可用");
  expect(screen().dataset.ready).toBe("false");
  // 降级顺序：先 Wasm，失败后改用 JS。
  expect(document.querySelector<HTMLIFrameElement>("#kmp-frame")!.src).toContain("/kmp-showcase/js/");
  await retry.click();
  await expect.poll(() => screen().dataset.ready, { timeout: 5000 }).toBe("true");
  expect(document.querySelector<HTMLButtonElement>("#retry-showcase")!.hidden).toBe(true);
});
