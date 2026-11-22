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
const activeCalls = () => site!.showcase.calls.filter(([name]) => name === "setDemoActive").map(([, active]) => active);
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

/** 模拟标签页切到后台或回到前台。 */
function setHidden(hidden: boolean) {
  if (hidden) Object.defineProperty(document, "hidden", { configurable: true, get: () => true });
  else delete (document as { hidden?: boolean }).hidden;
  document.dispatchEvent(new Event("visibilitychange"));
}

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

test("专注范围按钮与手机内屏双向同步，点击切换黑白名单", async () => {
  await desktopReady();
  await dock("#whitelist .feature-copy");
  await expect.poll(() => sceneCalls().at(-1)).toBe("apps");
  const whitelistBtn = document.querySelector<HTMLButtonElement>('[data-scope="whitelist"]')!;
  const blacklistBtn = document.querySelector<HTMLButtonElement>('[data-scope="blacklist"]')!;
  expect(whitelistBtn.getAttribute("aria-pressed")).toBe("true");
  expect(blacklistBtn.getAttribute("aria-pressed")).toBe("false");

  blacklistBtn.click();
  expect(blacklistBtn.getAttribute("aria-pressed")).toBe("true");
  expect(whitelistBtn.getAttribute("aria-pressed")).toBe("false");
  expect(liveStatus()).toBe("排除黑名单应用 · 演示中");
  expect(site!.showcase.calls).toContainEqual(["setDemoStatus", "scope_blacklist"]);

  const attempt = Number(
    new URL(document.querySelector<HTMLIFrameElement>("#kmp-frame")!.src).searchParams.get("attempt"),
  );
  dispatchEvent(
    new CustomEvent("gazekeep:demo", {
      detail: {
        attempt,
        focus: "",
        leadSeconds: 8,
        loose: false,
        holdMinutes: 30,
        hideIndicator: false,
        appScope: "whitelist",
      },
    }),
  );
  expect(whitelistBtn.getAttribute("aria-pressed")).toBe("true");
  expect(blacklistBtn.getAttribute("aria-pressed")).toBe("false");
  expect(liveStatus()).toBe("仅白名单应用 · 演示中");
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
