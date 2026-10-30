import { afterEach, expect, test } from "vitest";
import { page } from "vitest/browser";
import { mountWebsite, nextFrame, SHOWCASE_TIMEOUT, type ShowcaseControl } from "../_support/page.ts";

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

const sceneCalls = () => site!.showcase.calls.filter(([name]) => name === "setDemoScene").map(([, scene]) => scene);
/** 桌面布局下停靠到元素的吸附位置，等待滚动帧同步内屏。 */
async function dock(selector: string, block: ScrollLogicalPosition = "start") {
  document.querySelector<HTMLElement>(selector)!.scrollIntoView({ behavior: "instant", block });
  await nextFrame();
  await nextFrame();
}
async function desktopReady() {
  await page.viewport(1440, 900);
  site = await mountWebsite("zh");
  await expect.poll(() => screen().dataset.ready, { timeout: SHOWCASE_TIMEOUT }).toBe("true");
}
const pressedBackend = () => document.querySelector<HTMLElement>("[data-backend][aria-pressed=true]")!.dataset.backend;

test("工作流标题处内屏自动浏览，手机下方固定显示注视不熄屏，首屏仍为操作提示", async () => {
  await desktopReady();
  expect(liveStatus()).toBe("点击可操作 · 按住鼠标上下拖动可滚动");
  expect(sceneCalls().at(-1)).toBe("none");
  await dock(".story-heading");
  await expect.poll(liveStatus).toBe("注视不熄屏");
  expect(sceneCalls().at(-1)).toBe("tour");
});

test("第一幕在检测中与检测成功之间往复切换，离开后停止", async () => {
  await desktopReady();
  await dock(".story-step:first-of-type .story-copy", "center");
  await expect.poll(liveStatus).toBe("临近熄屏 · 短时采样");
  expect(document.querySelector<HTMLElement>("#phone-chassis")!.dataset.effect).toBe("camera-pulse");
  await expect.poll(liveStatus, { timeout: 3_000 }).toBe("注视了屏幕 · 延后熄屏");
  expect(document.querySelector<HTMLElement>("#phone-chassis")!.dataset.effect).toBe("none");
  await expect.poll(liveStatus, { timeout: 3_000 }).toBe("临近熄屏 · 短时采样");
  await dock(".story-heading");
  await expect.poll(liveStatus).toBe("注视不熄屏");
  await new Promise((resolve) => setTimeout(resolve, 2_200));
  expect(liveStatus()).toBe("注视不熄屏");
});

test("运行方式 1.5 秒轮换一次后端，悬停选中时暂停", async () => {
  await desktopReady();
  await dock("#backends .feature-copy");
  expect(pressedBackend()).toBe("lsposed");
  await expect.poll(pressedBackend, { timeout: 3_000 }).toBe("shizuku");
  expect(liveStatus()).toBe("Shizuku · 演示已就绪");
  const accessibility = document.querySelector<HTMLElement>('[data-backend="accessibility"]')!;
  accessibility.dispatchEvent(new MouseEvent("mouseenter"));
  expect(pressedBackend()).toBe("accessibility");
  await new Promise((resolve) => setTimeout(resolve, 2_500));
  expect(pressedBackend()).toBe("accessibility");
  expect(sceneCalls().at(-1)).toBe("backends");
});

test("专注范围演示应用列表，设置区手机回到右侧且卡片随内屏回报同步", async () => {
  await desktopReady();
  await dock("#whitelist .feature-copy");
  await expect.poll(() => sceneCalls().at(-1)).toBe("apps");
  const shift = () =>
    parseFloat(document.querySelector<HTMLElement>("#phone-stage")!.style.getPropertyValue("--device-shift"));
  await expect.poll(shift).toBeLessThan(0);
  await dock("#settings .feature-copy");
  await expect.poll(() => sceneCalls().at(-1)).toBe("settings");
  expect(site!.showcase.calls).toContainEqual(["setScreen", "settings"]);
  await expect.poll(shift).toBeGreaterThan(0);
  const value = (id: string) => document.querySelector(`[data-setting="${id}"] .setting-value`)!.textContent;
  expect(["lead", "sensitivity", "max_hold", "hide_indicator"].map(value)).toEqual(["8 秒", "标准", "30 分钟", "关闭"]);
  const attempt = Number(
    new URL(document.querySelector<HTMLIFrameElement>("#kmp-frame")!.src).searchParams.get("attempt"),
  );
  const report = (detail: object) => dispatchEvent(new CustomEvent("gazekeep:demo", { detail }));
  // 过期加载的回报不改变卡片。
  report({ attempt: attempt - 1, focus: "lead", leadSeconds: 5, loose: true, holdMinutes: 60, hideIndicator: true });
  expect(value("lead")).toBe("8 秒");
  report({ attempt, focus: "max_hold", leadSeconds: 5, loose: true, holdMinutes: 60, hideIndicator: true });
  expect(["lead", "sensitivity", "max_hold", "hide_indicator"].map(value)).toEqual(["5 秒", "宽松", "60 分钟", "开启"]);
  const active = [...document.querySelectorAll<HTMLElement>("[data-setting]")].filter(
    (c) => c.dataset.active === "true",
  );
  expect(active.map((c) => c.dataset.setting)).toEqual(["max_hold"]);
});

const activeCalls = () => site!.showcase.calls.filter(([name]) => name === "setDemoActive").map(([, active]) => active);
/** 模拟标签页切到后台或回到前台。 */
function setHidden(hidden: boolean) {
  if (hidden) Object.defineProperty(document, "hidden", { configurable: true, get: () => true });
  else delete (document as { hidden?: boolean }).hidden;
  document.dispatchEvent(new Event("visibilitychange"));
}

test("滚过设置区、手机离开视口时暂停内屏演示，滚回后继续", async () => {
  await desktopReady();
  await dock("#settings .feature-copy");
  await expect.poll(() => sceneCalls().at(-1)).toBe("settings");
  expect(activeCalls().at(-1)).toBe(true);
  document.querySelector("footer")!.scrollIntoView({ behavior: "instant", block: "end" });
  await expect.poll(() => activeCalls().at(-1)).toBe(false);
  // 场景保持不变，只是暂停：回来后从暂停处继续，不重新开始。
  expect(sceneCalls().at(-1)).toBe("settings");
  await dock("#settings .feature-copy");
  await expect.poll(() => activeCalls().at(-1)).toBe(true);
  expect(sceneCalls().at(-1)).toBe("settings");
});

test("标签页隐藏时暂停后端轮换与内屏演示，回到前台后继续", async () => {
  await desktopReady();
  await dock("#backends .feature-copy");
  expect(pressedBackend()).toBe("lsposed");
  try {
    setHidden(true);
    await expect.poll(() => activeCalls().at(-1)).toBe(false);
    await new Promise((resolve) => setTimeout(resolve, 2_200));
    expect(pressedBackend()).toBe("lsposed");
  } finally {
    setHidden(false);
  }
  await expect.poll(() => activeCalls().at(-1)).toBe(true);
  await expect.poll(pressedBackend, { timeout: 3_000 }).toBe("shizuku");
});
