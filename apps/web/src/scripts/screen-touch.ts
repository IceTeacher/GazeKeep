import { PHONE, projectPlane, unmapPlanePoint } from "./phone-geometry.ts";

/** 只跟踪首个指针；位移超过阈值后视为滑动，交给页面原生滚动。 */
export function createTapTracker(slop = 10) {
  let id: number | undefined,
    x = 0,
    y = 0,
    moved = false;
  return {
    start(pointer: number, px: number, py: number) {
      if (id !== undefined) return false;
      id = pointer;
      x = px;
      y = py;
      moved = false;
      return true;
    },
    move(pointer: number, px: number, py: number) {
      if (pointer !== id) return false;
      if (Math.hypot(px - x, py - y) > slop) moved = true;
      return !moved;
    },
    end(pointer: number) {
      if (pointer !== id) return false;
      id = undefined;
      return !moved;
    },
    cancel(pointer: number) {
      if (pointer === id) id = undefined;
    },
  };
}

type Listen = (target: EventTarget, type: string, fn: EventListener, options?: AddEventListenerOptions) => void;
type FrameWindow = Window & typeof globalThis;

/** 展示入口把画布放在 ComposeViewport 的开放 shadow root 中。 */
function findCanvas(doc: Document | null | undefined) {
  if (!doc) return undefined;
  const direct = doc.querySelector("canvas");
  if (direct) return direct;
  for (const element of doc.querySelectorAll("*")) {
    const canvas = element.shadowRoot?.querySelector("canvas");
    if (canvas) return canvas;
  }
  return undefined;
}

/**
 * 内屏命中层位于页面文档中：滚轮和竖向触摸直接滚动页面，叙事与吸附不受影响；
 * 只有轻点按投影逆变换换算为应用坐标后转发给 Compose 画布。iframe 本身保持 inert，不接收焦点。
 */
export function initScreenTouch({
  layer,
  host,
  frame,
  active,
  onTap,
  listen,
}: {
  layer: HTMLElement;
  host: HTMLElement;
  frame: HTMLIFrameElement;
  active: () => boolean;
  onTap: () => void;
  listen: Listen;
}) {
  const tap = createTapTracker();
  let pressed = false;
  const toApp = (event: PointerEvent): [number, number] | undefined => {
    const corners = host.dataset.projection;
    if (!corners) return undefined;
    const box = host.getBoundingClientRect();
    const matrix = projectPlane(PHONE.screenWidth, PHONE.screenHeight, JSON.parse(corners));
    const [x, y] = unmapPlanePoint(matrix, event.clientX - box.left, event.clientY - box.top);
    return [x, y - PHONE.statusHeight];
  };
  const forward = (type: string, [x, y]: [number, number], buttons: number) => {
    const win = frame.contentWindow as FrameWindow | null;
    const canvas = findCanvas(frame.contentDocument);
    if (!win || !canvas) return false;
    // 统一按鼠标左键转发：触摸滑动已由页面接管，转发的只剩轻点。
    const event = new win.PointerEvent(type, {
      bubbles: true,
      cancelable: true,
      composed: true,
      clientX: x,
      clientY: y,
      pointerId: 1,
      pointerType: "mouse",
      isPrimary: true,
      button: 0,
      buttons,
    });
    // Compose 按 offsetX/Y 命中；Android Chrome 会把 iframe 内合成事件的 offset 除以设备像素比，这里直接写入画布坐标。
    const box = canvas.getBoundingClientRect();
    Object.defineProperties(event, {
      offsetX: { value: x - box.left },
      offsetY: { value: y - box.top },
    });
    canvas.dispatchEvent(event);
    return true;
  };
  // 移出到画布之外再抬起，Compose 不会把这次按压当作点击。
  const release = () => {
    if (!pressed) return;
    pressed = false;
    forward("pointermove", [-1e4, -1e4], 1);
    forward("pointerup", [-1e4, -1e4], 0);
  };
  listen(layer, "pointerdown", ((event: PointerEvent) => {
    if (event.button !== 0 || !active()) return;
    const point = toApp(event);
    if (!point || !tap.start(event.pointerId, event.clientX, event.clientY)) return;
    pressed = forward("pointerdown", point, 1);
    // 鼠标拖出命中层后抬起也要收尾，否则跟踪器会停在按下状态。
    try {
      layer.setPointerCapture(event.pointerId);
    } catch {
      /* 指针已结束时无法捕获，由后续 pointerup/cancel 收尾。 */
    }
  }) as EventListener);
  listen(layer, "pointermove", ((event: PointerEvent) => {
    if (!tap.move(event.pointerId, event.clientX, event.clientY)) release();
  }) as EventListener);
  listen(layer, "pointerup", ((event: PointerEvent) => {
    const tapped = tap.end(event.pointerId);
    const point = tapped && pressed ? toApp(event) : undefined;
    if (!point) return release();
    pressed = false;
    forward("pointerup", point, 0);
    onTap();
  }) as EventListener);
  const cancel = ((event: PointerEvent) => {
    tap.cancel(event.pointerId);
    release();
  }) as EventListener;
  listen(layer, "pointercancel", cancel);
  listen(layer, "lostpointercapture", cancel);
  listen(layer, "contextmenu", (event) => event.preventDefault());
}
