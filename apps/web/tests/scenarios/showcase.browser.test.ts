import { afterEach, expect, test } from "vitest";
import { page } from "vitest/browser";
import { mountWebsite, SHOWCASE_TIMEOUT, type ShowcaseControl } from "../_support/page.ts";

let site: Awaited<ReturnType<typeof mountWebsite>> | undefined;
afterEach(() => {
  site?.unmount();
  site = undefined;
});

const screen = () => document.querySelector<HTMLElement>(".phone-screen")!;

test("内屏就绪后重放最新页面与状态，并隐藏加载提示", async () => {
  site = await mountWebsite("zh");
  await expect.poll(() => screen().dataset.ready, { timeout: SHOWCASE_TIMEOUT }).toBe("true");
  expect(document.querySelector<HTMLButtonElement>("#retry-showcase")!.hidden).toBe(true);
  const names = site.showcase.calls.map(([name]) => name);
  expect(names).toEqual(expect.arrayContaining(["setLocaleAndTheme", "setScreen", "setDemoStatus"]));
});

test("Wasm 与 JS 都失败时显示重试，重试成功后恢复内屏", async () => {
  const showcase: ShowcaseControl = { mode: "ready", modes: ["error", "error"], calls: [] };
  site = await mountWebsite("zh", { showcase });
  const retry = page.getByRole("button", { name: "重新加载" });
  await expect.element(retry, { timeout: SHOWCASE_TIMEOUT }).toBeVisible();
  expect(document.querySelector("#load-message")!.textContent).toBe("界面暂时不可用");
  expect(screen().dataset.ready).toBe("false");
  // 降级顺序：先 Wasm，失败后改用 JS。
  expect(document.querySelector<HTMLIFrameElement>("#kmp-frame")!.src).toContain("/kmp-showcase/js/");
  await retry.click();
  await expect.poll(() => screen().dataset.ready, { timeout: SHOWCASE_TIMEOUT }).toBe("true");
  expect(document.querySelector<HTMLButtonElement>("#retry-showcase")!.hidden).toBe(true);
});

const liveStatus = () => document.querySelector("#demo-status")!.textContent;

test("首屏内屏可操作后，手机下方按鼠标输入提示操作方式；其他阶段恢复演示状态", async () => {
  site = await mountWebsite("zh");
  expect(liveStatus()).toBe("准备就绪");
  await expect.poll(liveStatus, { timeout: SHOWCASE_TIMEOUT }).toBe("点击可操作 · 按住鼠标上下拖动可滚动");
  document.querySelector<HTMLButtonElement>('[data-backend="shizuku"]')!.click();
  expect(liveStatus()).toBe("Shizuku · 演示已就绪");
});

test("英文页使用英文操作提示", async () => {
  site = await mountWebsite("en");
  await expect.poll(liveStatus, { timeout: SHOWCASE_TIMEOUT }).toBe("Click to use · drag to scroll");
});

test("内屏加载失败时不提示操作，保留准备就绪", async () => {
  const showcase: ShowcaseControl = { mode: "ready", modes: ["error", "error"], calls: [] };
  site = await mountWebsite("zh", { showcase });
  await expect.element(page.getByRole("button", { name: "重新加载" }), { timeout: SHOWCASE_TIMEOUT }).toBeVisible();
  expect(liveStatus()).toBe("准备就绪");
});
