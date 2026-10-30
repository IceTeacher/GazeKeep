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

function press(type: string, point: { clientX: number; clientY: number }, pointerType = "mouse") {
  layer().dispatchEvent(
    new PointerEvent(type, {
      ...point,
      bubbles: true,
      cancelable: true,
      pointerId: 7,
      pointerType,
      button: type === "pointermove" ? -1 : 0,
      isPrimary: true,
    }),
  );
}

/** 返回 touchmove 是否被拦截；被拦截时浏览器不会滚动页面。 */
function touchMove(point: { clientX: number; clientY: number }) {
  const touch = new Touch({ identifier: 7, target: layer(), ...point });
  const event = new TouchEvent("touchmove", { bubbles: true, cancelable: true, touches: [touch] });
  layer().dispatchEvent(event);
  return event.defaultPrevented;
}

const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

test("真实点击倾斜内屏时，按投影逆变换以应用坐标转发给画布", async () => {
  await mountReady();
  let expected: [number, number] | undefined;
  // 首屏手机持续漂移，Playwright 的稳定性等待不会结束；坐标以实际收到的事件为准。
  // 投影约每 40ms 随漂移更新：在按下的同一次派发中取投影，与转发时所用的一致，不受点击返回前的帧数影响。
  layer().addEventListener(
    "pointerdown",
    (e) => {
      const box = host().getBoundingClientRect();
      expected = unmapPlanePoint(projection(), e.clientX - box.left, e.clientY - box.top);
    },
    { once: true },
  );
  await userEvent.click(layer(), { force: true });
  expect(expected).toBeDefined();
  const [x, y] = expected!;
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

test("鼠标按住上下拖动，以触摸指针把完整拖动转发给内屏，方向与鼠标一致", async () => {
  await mountReady();
  const from = clientPoint(200, 600);
  press("pointerdown", from);
  for (let i = 1; i <= 5; i++) press("pointermove", clientPoint(200, 600 - i * 40));
  expect(layer().dataset.dragging).toBe("true");
  press("pointerup", clientPoint(200, 400));
  expect(layer().dataset.dragging).toBeUndefined();
  const calls = pointerCalls() as [string, string, number, number][];
  expect(calls.map(([, type]) => type)).toEqual(["pointerdown", ...Array(5).fill("pointermove"), "pointerup"]);
  // 自然滚动：转发的纵坐标随鼠标上移而减小，内容跟随指针。
  const ys = calls.map(([, , , y]) => Math.round(y));
  expect(ys).toEqual([600, 560, 520, 480, 440, 400, 400]);
  const types = site!.showcase.calls.filter(([name]) => name === "pointerType").map(([, , type]) => type);
  expect(new Set(types)).toEqual(new Set(["touch"]));
});

test("触摸快速滑动交给页面滚动，不转发也不拦截", async () => {
  await mountReady();
  const from = clientPoint(200, 500);
  press("pointerdown", from, "touch");
  const to = clientPoint(200, 440);
  press("pointermove", to, "touch");
  expect(touchMove(to)).toBe(false);
  press("pointerup", to, "touch");
  await wait(400);
  expect(pointerCalls()).toEqual([]);
});

test("触摸长按后滑动转发给内屏，并阻止页面滚动", async () => {
  await mountReady();
  const from = clientPoint(200, 500);
  press("pointerdown", from, "touch");
  // 长按前不向内屏留下按压。
  expect(pointerCalls()).toEqual([]);
  await wait(400);
  expect(pointerCalls().map(([, type]) => type)).toEqual(["pointerdown"]);
  const to = clientPoint(200, 420);
  expect(touchMove(to)).toBe(true);
  press("pointermove", to, "touch");
  press("pointerup", to, "touch");
  const calls = pointerCalls() as [string, string, number, number][];
  expect(calls.map(([, type]) => type)).toEqual(["pointerdown", "pointermove", "pointerup"]);
  expect(Math.round(calls[1][3])).toBe(420);
  // 结束后不再拦截下一次快速滑动。
  expect(touchMove(to)).toBe(false);
});

test("长按后未收到抬起（Firefox 长按菜单会中断触摸流），新的按下先取消内屏旧按压", async () => {
  await mountReady();
  press("pointerdown", clientPoint(200, 500), "touch");
  await wait(300);
  const next = clientPoint(100, 300);
  layer().dispatchEvent(
    new PointerEvent("pointerdown", {
      ...next,
      bubbles: true,
      pointerId: 8,
      pointerType: "touch",
      button: 0,
      isPrimary: true,
    }),
  );
  layer().dispatchEvent(
    new PointerEvent("pointerup", {
      ...next,
      bubbles: true,
      pointerId: 8,
      pointerType: "touch",
      button: 0,
      isPrimary: true,
    }),
  );
  expect(pointerCalls().map(([, type]) => type)).toEqual(["pointerdown", "pointercancel", "pointerdown", "pointerup"]);
});

test("长按菜单弹出后触摸流不再继续时，取消内屏按压并恢复页面滚动", async () => {
  await mountReady();
  press("pointerdown", clientPoint(200, 500), "touch");
  await wait(300);
  layer().dispatchEvent(new MouseEvent("contextmenu", { bubbles: true, cancelable: true }));
  await wait(800);
  expect(pointerCalls().map(([, type]) => type)).toEqual(["pointerdown", "pointercancel"]);
  expect(touchMove(clientPoint(200, 420))).toBe(false);
});

test("长按菜单之后仍有移动（Chrome）时继续拖动", async () => {
  await mountReady();
  press("pointerdown", clientPoint(200, 500), "touch");
  await wait(300);
  layer().dispatchEvent(new MouseEvent("contextmenu", { bubbles: true, cancelable: true }));
  await wait(200);
  press("pointermove", clientPoint(200, 460), "touch");
  await wait(700);
  press("pointerup", clientPoint(200, 460), "touch");
  expect(pointerCalls().map(([, type]) => type)).toEqual(["pointerdown", "pointermove", "pointerup"]);
});

test("触摸轻点在抬起时转发按下与抬起", async () => {
  await mountReady();
  const point = clientPoint(200, 500);
  press("pointerdown", point, "touch");
  press("pointerup", point, "touch");
  expect(pointerCalls().map(([, type]) => type)).toEqual(["pointerdown", "pointerup"]);
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
