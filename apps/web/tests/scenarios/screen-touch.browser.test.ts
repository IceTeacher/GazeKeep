import { afterEach, expect, test } from "vitest";
import { userEvent } from "vitest/browser";
import { mapPlanePoint, PHONE, projectPlane, unmapPlanePoint } from "../../src/scripts/phone-geometry.ts";
import { mountWebsite, nextFrame, scrollToY, SHOWCASE_TIMEOUT } from "../_support/page.ts";

let site: Awaited<ReturnType<typeof mountWebsite>> | undefined;
afterEach(() => {
  site?.unmount();
  site = undefined;
});

const layer = () => document.querySelector<HTMLElement>("#screen-touch")!;
const host = () => document.querySelector<HTMLElement>("#phone-visual")!;
const pointerCalls = () => site!.showcase.calls.filter(([name]) => name === "pointer");
const projection = () => projectPlane(PHONE.screenWidth, PHONE.screenHeight, JSON.parse(host().dataset.projection!));

async function mountReady() {
  site = await mountWebsite("zh");
  await expect
    .poll(() => document.querySelector<HTMLElement>(".phone-screen")!.dataset.ready, { timeout: SHOWCASE_TIMEOUT })
    .toBe("true");
  await expect.poll(() => host().dataset.projection).toBeTruthy();
  await nextFrame();
}

/** 应用坐标经当前投影换算为视口坐标。 */
function clientPoint(x: number, y: number) {
  const box = host().getBoundingClientRect();
  const [u, v] = mapPlanePoint(projection(), x, y + PHONE.statusHeight);
  return { clientX: box.left + u, clientY: box.top + v };
}

function press(type: string, point: { clientX: number; clientY: number }) {
  layer().dispatchEvent(
    new PointerEvent(type, { ...point, bubbles: true, cancelable: true, pointerId: 7, button: 0, isPrimary: true }),
  );
}

test("真实点击倾斜内屏时，按投影逆变换以应用坐标转发给画布", async () => {
  await mountReady();
  let down: PointerEvent | undefined;
  layer().addEventListener("pointerdown", (e) => (down = e), { once: true });
  // 首屏手机持续漂移，Playwright 的稳定性等待不会结束；坐标以实际收到的事件为准。
  await userEvent.click(layer(), { force: true });
  expect(down).toBeDefined();
  const box = host().getBoundingClientRect();
  const [x, y] = unmapPlanePoint(projection(), down!.clientX - box.left, down!.clientY - box.top);
  const calls = pointerCalls();
  expect(calls.map(([, type]) => type)).toEqual(["pointerdown", "pointerup"]);
  const [, , fx, fy] = calls[0] as [string, string, number, number];
  expect(fx).toBeCloseTo(x, 0);
  expect(fy).toBeCloseTo(y - PHONE.statusHeight, 0);
  // 点击位置落在应用区内，而不是状态栏或手势条。
  expect(fx).toBeGreaterThan(0);
  expect(fy).toBeGreaterThan(0);
  expect(fy).toBeLessThan(PHONE.appHeight);
});

test("浏览器把合成事件的 offsetX 按设备像素比缩小时，画布仍收到应用坐标", async () => {
  await mountReady();
  // 复现 Android Chrome（DPR 3.5）：iframe 内合成指针事件的 offsetX/Y 被除以设备像素比，Compose 按此坐标命中。
  const frame = document.querySelector<HTMLIFrameElement>("#kmp-frame")!.contentWindow as Window & typeof globalThis;
  for (const [axis, client] of [
    ["offsetX", "clientX"],
    ["offsetY", "clientY"],
  ] as const)
    Object.defineProperty(frame.MouseEvent.prototype, axis, {
      configurable: true,
      get(this: MouseEvent) {
        return this[client] / 3.5;
      },
    });
  const point = clientPoint(294, 750);
  press("pointerdown", point);
  press("pointerup", point);
  const offsets = site!.showcase.calls.filter(([name]) => name === "offset");
  expect(offsets).toHaveLength(2);
  for (const [, , x, y] of offsets as [string, string, number, number][]) {
    expect(x).toBeCloseTo(294, 0);
    expect(y).toBeCloseTo(750, 0);
  }
});

test("命中层覆盖投影后的应用区，屏幕外仍穿透到页面", async () => {
  await mountReady();
  const inside = clientPoint(PHONE.appWidth / 2, PHONE.appHeight - 40);
  expect(document.elementFromPoint(inside.clientX, inside.clientY)).toBe(layer());
  const outside = clientPoint(PHONE.appWidth / 2, -PHONE.statusHeight - 120);
  expect(document.elementFromPoint(outside.clientX, outside.clientY)).not.toBe(layer());
});

test("按下后滑动超过阈值，只在画布外抬起，不产生点击", async () => {
  await mountReady();
  const start = clientPoint(200, 400);
  press("pointerdown", start);
  press("pointermove", { clientX: start.clientX, clientY: start.clientY + 30 });
  press("pointerup", { clientX: start.clientX, clientY: start.clientY + 30 });
  const calls = pointerCalls();
  expect(calls[0].slice(0, 2)).toEqual(["pointer", "pointerdown"]);
  expect(calls.slice(1)).toEqual([
    ["pointer", "pointermove", -1e4, -1e4],
    ["pointer", "pointerup", -1e4, -1e4],
  ]);
});

test("内屏黑屏时不响应点按", async () => {
  await mountReady();
  document.querySelector<HTMLElement>("#phone-chassis")!.style.setProperty("--screen-dim", "1");
  const point = clientPoint(200, 400);
  press("pointerdown", point);
  press("pointerup", point);
  expect(pointerCalls()).toEqual([]);
});

test("轻点内屏后滚动页面，以转场复位一次到当前段落页面", async () => {
  await mountReady();
  const point = clientPoint(200, 400);
  press("pointerdown", point);
  press("pointerup", point);
  const restores = () => site!.showcase.calls.filter(([name, , restore]) => name === "setScreen" && restore === true);
  expect(restores()).toEqual([]);
  // 桌面端强制吸附，按停靠点滚动；阈值内的细微滚动由单元测试覆盖。
  const workflow = document.querySelector<HTMLElement>("#workflow")!;
  await scrollToY(workflow.getBoundingClientRect().top + scrollY);
  await expect.poll(() => restores()).toHaveLength(1);
  expect(restores()[0][1]).toBe("home");
  await scrollToY(scrollY + innerHeight);
  expect(restores()).toHaveLength(1);
});

test("未操作内屏时滚动不触发复位", async () => {
  await mountReady();
  await scrollToY(200);
  expect(site!.showcase.calls.some(([name, , restore]) => name === "setScreen" && restore === true)).toBe(false);
});
