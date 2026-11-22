import { afterEach, expect, test } from "vitest";
import { page } from "vitest/browser";
import { mountWebsite, nextFrame, SHOWCASE_TIMEOUT, type ShowcaseControl } from "../_support/page.ts";

let site: Awaited<ReturnType<typeof mountWebsite>> | undefined;
afterEach(() => {
  site?.unmount();
  site = undefined;
});

const screen = () => document.querySelector<HTMLElement>(".phone-screen")!;
const liveStatus = () => document.querySelector("#demo-status")!.textContent;

async function dock(selector: string, block: ScrollLogicalPosition = "start") {
  document.querySelector<HTMLElement>(selector)!.scrollIntoView({ behavior: "instant", block });
  await nextFrame();
  await nextFrame();
}

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
  const placeholder = document.querySelector<HTMLElement>("#phone-placeholder")!;
  const preview = document.querySelector<HTMLElement>(".app-preview")!;
  const placeholderStatus = document.querySelector<HTMLElement>(".placeholder-status")!;
  const retry = page.getByRole("button", { name: "重新加载" });
  await expect.element(retry, { timeout: SHOWCASE_TIMEOUT }).toBeVisible();
  expect(document.querySelector("#load-message")!.textContent).toBe("界面暂时不可用");
  expect(screen().dataset.ready).toBe("false");
  expect(placeholder.dataset.state).toBe("error");
  expect(getComputedStyle(preview).display).toBe("none");
  expect(getComputedStyle(placeholderStatus).display).toBe("flex");
  // 降级顺序：先 Wasm，失败后改用 JS。
  expect(document.querySelector<HTMLIFrameElement>("#kmp-frame")!.src).toContain("/kmp-showcase/js/");
  await retry.click();
  await expect.poll(() => screen().dataset.ready, { timeout: SHOWCASE_TIMEOUT }).toBe("true");
  expect(document.querySelector<HTMLButtonElement>("#retry-showcase")!.hidden).toBe(true);
  expect(placeholder.dataset.state).toBeUndefined();
  expect(getComputedStyle(placeholderStatus).display).toBe("none");
});

test("未收到 gazekeep:rendered 前保留同构 DOM 预览并隐藏机内加载胶囊", async () => {
  site = await mountWebsite("zh", { showcase: { mode: "silent", calls: [] } });
  const frame = document.querySelector<HTMLIFrameElement>("#kmp-frame")!;
  const placeholder = document.querySelector<HTMLElement>("#phone-placeholder")!;
  const placeholderStatus = document.querySelector<HTMLElement>(".placeholder-status")!;

  expect(getComputedStyle(placeholderStatus).display).toBe("none");
  expect(getComputedStyle(document.querySelector<HTMLElement>(".app-preview")!).display).toBe("flex");

  type TestBridge = { setScreen: (route: string, revision?: number, restore?: boolean) => void };
  await expect
    .poll(
      () =>
        Boolean((frame.contentWindow as { GazeKeepWebShowcaseBridge?: TestBridge } | null)?.GazeKeepWebShowcaseBridge),
      {
        timeout: SHOWCASE_TIMEOUT,
      },
    )
    .toBe(true);

  const attempt = Number(new URL(frame.src).searchParams.get("attempt"));
  const bridge = (frame.contentWindow as unknown as { GazeKeepWebShowcaseBridge: TestBridge })
    .GazeKeepWebShowcaseBridge;
  let lastScreen: { route: string; revision?: number } | undefined;
  bridge.setScreen = (route, revision, restore) => {
    lastScreen = { route, revision };
    site!.showcase.calls.push(restore ? ["setScreen", route, true] : ["setScreen", route]);
  };

  dispatchEvent(new CustomEvent("gazekeep:ready", { detail: { attempt } }));
  await expect.poll(() => lastScreen?.route, { timeout: SHOWCASE_TIMEOUT }).toBe("home");
  // 等手机外壳首帧就绪后（避免继承 :root[data-phone="pending"] 的 visibility: hidden）再核对占位仍可见。
  await expect.poll(() => document.documentElement.dataset.phone, { timeout: SHOWCASE_TIMEOUT }).toBe("ready");
  await nextFrame();
  await nextFrame();

  // 仅 bridge 就绪不足以切换：首帧未绘出前继续由同构 DOM 承接且状态仍为准备就绪。
  expect(screen().dataset.ready).toBe("false");
  expect(liveStatus()).toBe("准备就绪");
  expect(getComputedStyle(placeholder).opacity).toBe("1");
  expect(getComputedStyle(placeholder).visibility).toBe("visible");

  dispatchEvent(
    new CustomEvent("gazekeep:rendered", {
      detail: { route: lastScreen!.route, revision: lastScreen!.revision, attempt },
    }),
  );
  await expect.poll(() => screen().dataset.ready, { timeout: 5_000 }).toBe("true");
  expect(liveStatus()).toBe("点击可操作 · 按住鼠标上下拖动可滚动");
  await expect.poll(() => getComputedStyle(placeholder).visibility, { timeout: 5_000 }).toBe("hidden");
  expect(getComputedStyle(placeholder).opacity).toBe("0");
});

test("主页同构预览对齐子集字体、色板、SVG 图标与几何高度", async () => {
  site = await mountWebsite("zh", { theme: "dark", showcase: { mode: "silent", calls: [] } });
  const placeholder = document.querySelector<HTMLElement>("#phone-placeholder")!;
  const preview = document.querySelector<HTMLElement>(".app-preview")!;
  const topbar = document.querySelector<HTMLElement>(".app-preview-topbar")!;
  const hero = document.querySelector<HTMLElement>(".app-preview-hero")!;
  const card = document.querySelector<HTMLElement>(".app-preview-card")!;
  const nav = document.querySelector<HTMLElement>(".app-preview-nav")!;
  const ring = document.querySelector<SVGSVGElement>("svg.app-preview-ring")!;
  const tabIcons = document.querySelectorAll<SVGSVGElement>(".app-preview-tab svg");

  expect(getComputedStyle(preview).fontFamily).toContain("GazeKeep Showcase Sans");
  expect(getComputedStyle(placeholder).transitionDuration).toContain("0.15s");
  expect(getComputedStyle(topbar).height).toBe("102px");
  expect(getComputedStyle(topbar).paddingTop).toBe("56px");
  expect(getComputedStyle(hero).minHeight).toBe("130px");
  expect(getComputedStyle(hero).backgroundColor).toBe("rgb(25, 54, 35)");
  expect(getComputedStyle(card).backgroundColor).toBe("rgb(36, 36, 36)");
  expect(getComputedStyle(nav).height).toBe("65px");
  expect(getComputedStyle(nav).paddingTop).toBe("9px");
  expect(ring.getAttribute("viewBox")).toBe("0 0 92 92");
  expect(tabIcons).toHaveLength(2);
  for (const icon of tabIcons) expect(icon.getAttribute("viewBox")).toBe("0 0 24 24");

  site.unmount();
  site = await mountWebsite("en", { theme: "light", showcase: { mode: "silent", calls: [] } });
  expect(getComputedStyle(document.querySelector<HTMLElement>(".app-preview-hero")!).backgroundColor).toBe(
    "rgb(223, 246, 228)",
  );
  expect(getComputedStyle(document.querySelector<HTMLElement>(".app-preview-card")!).backgroundColor).toBe(
    "rgb(255, 255, 255)",
  );
  expect(document.querySelector(".app-preview-title")!.textContent).toBe("GazeKeep");
});

test("内屏加载失败时不提示操作，保留准备就绪", async () => {
  const showcase: ShowcaseControl = { mode: "ready", modes: ["error", "error"], calls: [] };
  site = await mountWebsite("zh", { showcase });
  await expect.element(page.getByRole("button", { name: "重新加载" }), { timeout: SHOWCASE_TIMEOUT }).toBeVisible();
  expect(liveStatus()).toBe("准备就绪");
});

test("Save-Data 或慢速网络在首屏空闲时不主动加载 KMP，点击手机或向下滚动时立即触发挂载", async () => {
  const original = Object.getOwnPropertyDescriptor(Navigator.prototype, "connection");
  let mockConn: { saveData?: boolean; effectiveType?: string } = { saveData: true, effectiveType: "4g" };
  Object.defineProperty(navigator, "connection", { configurable: true, get: () => mockConn });
  try {
    await page.viewport(1440, 900);
    site = await mountWebsite("zh");
    await nextFrame();
    await nextFrame();
    await new Promise((resolve) => setTimeout(resolve, 150));
    const frame = document.querySelector<HTMLIFrameElement>("#kmp-frame")!;
    expect(frame.getAttribute("src")).toBeNull();
    expect(screen().dataset.ready).toBe("false");

    // 轻触或点击首屏手机内屏立即触发挂载。
    screen().dispatchEvent(new PointerEvent("pointerdown", { bubbles: true }));
    await expect.poll(() => frame.getAttribute("src"), { timeout: SHOWCASE_TIMEOUT }).toContain("/kmp-showcase/");
    await expect.poll(() => screen().dataset.ready, { timeout: SHOWCASE_TIMEOUT }).toBe("true");

    site.unmount();
    mockConn = { saveData: false, effectiveType: "3g" };
    site = await mountWebsite("zh");
    await nextFrame();
    await nextFrame();
    const frame2 = document.querySelector<HTMLIFrameElement>("#kmp-frame")!;
    expect(frame2.getAttribute("src")).toBeNull();

    await dock(".story-heading");
    await expect.poll(() => frame2.getAttribute("src"), { timeout: SHOWCASE_TIMEOUT }).toContain("/kmp-showcase/");
    await expect.poll(() => screen().dataset.ready, { timeout: SHOWCASE_TIMEOUT }).toBe("true");
  } finally {
    if (original) Object.defineProperty(Navigator.prototype, "connection", original);
    delete (navigator as { connection?: unknown }).connection;
  }
});
