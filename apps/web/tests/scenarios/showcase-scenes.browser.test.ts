import { afterEach, expect, test } from "vitest";
import { page } from "vitest/browser";
import { mountWebsite, nextFrame, SHOWCASE_TIMEOUT } from "../_support/page.ts";

let site: Awaited<ReturnType<typeof mountWebsite>> | undefined;
afterEach(() => {
  site?.unmount();
  site = undefined;
});

const screen = () => document.querySelector<HTMLElement>(".phone-screen")!;
const liveStatus = () => document.querySelector("#demo-status")!.textContent;
const sceneCalls = () => site!.showcase.calls.filter(([name]) => name === "setDemoScene").map(([, scene]) => scene);
const pressedBackend = () => document.querySelector<HTMLElement>("[data-backend][aria-pressed=true]")!.dataset.backend;

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
