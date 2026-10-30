import { PHONE, projectPlane, unmapPlanePoint } from "./phone-geometry.ts";

/**
 * 触摸按住不动超过此时长后才滚动内屏；更快的滑动交给页面原生滚动。
 * Android Firefox 在按住约 500ms 时触发长按并中断后续触摸事件，阈值需留出在此之前开始滑动的窗口。
 */
export const HOLD_MS = 250;
/** 长按菜单事件之后若这么久没有任何后续指针事件，视为浏览器已中断触摸流。 */
const STALLED_MS = 600;

type GestureState = "pending" | "drag" | "native";

/**
 * 只跟踪首个指针。鼠标按下即转发，拖动超过阈值后为内屏拖动；
 * 触摸先等待长按，未长按就滑动视为页面滚动，长按后的滑动才是内屏拖动。
 */
export function createGestureTracker(slop = 10) {
  let id: number | undefined,
    x = 0,
    y = 0,
    mouse = false,
    held = false,
    state: GestureState = "pending";
  const finish = () => {
    id = undefined;
    held = false;
  };
  return {
    start(pointer: number, px: number, py: number, isMouse: boolean) {
      if (id !== undefined) return false;
      id = pointer;
      x = px;
      y = py;
      mouse = isMouse;
      held = false;
      state = "pending";
      return true;
    },
    move(pointer: number, px: number, py: number): GestureState | "idle" {
      if (pointer !== id) return "idle";
      if (state === "pending" && Math.hypot(px - x, py - y) > slop) state = mouse || held ? "drag" : "native";
      return state;
    },
    /** 长按计时到期；只有仍停在原处的触摸才转为内屏拖动。 */
    hold(pointer: number) {
      if (pointer !== id || mouse || state !== "pending") return false;
      held = true;
      return true;
    },
    /** 内屏正在接收这次触摸，页面不应滚动。 */
    dragging() {
      return id !== undefined && (state === "drag" || held);
    },
    end(pointer: number): "tap" | "drag" | "native" | "none" {
      if (pointer !== id) return "none";
      const result = state === "native" ? "native" : state === "drag" || held ? "drag" : "tap";
      finish();
      return result;
    },
    /** 返回取消前是否已向内屏转发按下，需要随之取消。 */
    cancel(pointer: number) {
      if (pointer !== id) return false;
      return this.reset();
    },
    /** 结束当前手势（不论指针），用于浏览器不再派发结束事件时恢复。 */
    reset() {
      if (id === undefined) return false;
      const forwarded = mouse || held;
      finish();
      return forwarded;
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
 * 内屏命中层位于页面文档中：滚轮与快速触摸滑动直接滚动页面，叙事与吸附不受影响；
 * 轻点、鼠标拖动与长按后的触摸滑动按投影逆变换换算为应用坐标，以触摸指针转发给 Compose 画布，
 * 由 Compose 处理点击、拖动、惯性与回弹。iframe 本身保持 inert，不接收焦点。
 */
export function initScreenTouch({
  layer,
  host,
  frame,
  active,
  onInteract,
  listen,
}: {
  layer: HTMLElement;
  host: HTMLElement;
  frame: HTMLIFrameElement;
  active: () => boolean;
  onInteract: () => void;
  listen: Listen;
}) {
  const gesture = createGestureTracker();
  let start: [number, number] = [0, 0],
    last: [number, number] = [0, 0],
    holdTimer: ReturnType<typeof setTimeout> | undefined,
    stallTimer: ReturnType<typeof setTimeout> | undefined;
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
    if (!win || !canvas) return;
    // 鼠标也按触摸转发：Compose 只对触摸指针启用拖动滚动与惯性。
    const event = new win.PointerEvent(type, {
      bubbles: true,
      cancelable: true,
      composed: true,
      clientX: x,
      clientY: y,
      pointerId: 1,
      pointerType: "touch",
      isPrimary: true,
      button: type === "pointermove" ? -1 : 0,
      buttons,
      pressure: buttons ? 0.5 : 0,
    });
    // Compose 按 offsetX/Y 命中；Android Chrome 会把 iframe 内合成事件的 offset 除以设备像素比，这里直接写入画布坐标。
    const box = canvas.getBoundingClientRect();
    Object.defineProperties(event, {
      offsetX: { value: x - box.left },
      offsetY: { value: y - box.top },
    });
    canvas.dispatchEvent(event);
  };
  const stopHold = () => {
    clearTimeout(holdTimer);
    holdTimer = undefined;
    clearTimeout(stallTimer);
    stallTimer = undefined;
  };
  // 结束未收到抬起或取消的手势，并让内屏释放已转发的按压。
  const abandon = () => {
    stopHold();
    delete layer.dataset.dragging;
    if (gesture.reset()) forward("pointercancel", last, 0);
  };
  listen(layer, "pointerdown", ((event: PointerEvent) => {
    if (event.button !== 0 || !active()) return;
    if (event.isPrimary) abandon();
    const point = toApp(event);
    const mouse = event.pointerType !== "touch";
    if (!point || !gesture.start(event.pointerId, event.clientX, event.clientY, mouse)) return;
    start = last = point;
    if (mouse) {
      forward("pointerdown", point, 1);
      // 鼠标拖出命中层后抬起也要收尾，否则跟踪器会停在按下状态。
      try {
        layer.setPointerCapture(event.pointerId);
      } catch {
        /* 指针已结束时无法捕获，由后续 pointerup/cancel 收尾。 */
      }
      return;
    }
    // 触摸按住不动才把这次按压交给内屏，此前不转发，快速滑动不会在内屏留下按压。
    const pointer = event.pointerId;
    holdTimer = setTimeout(() => {
      holdTimer = undefined;
      if (gesture.hold(pointer)) forward("pointerdown", start, 1);
    }, HOLD_MS);
  }) as EventListener);
  listen(layer, "pointermove", ((event: PointerEvent) => {
    clearTimeout(stallTimer);
    const state = gesture.move(event.pointerId, event.clientX, event.clientY);
    if (state === "idle") return;
    if (state === "native") return stopHold();
    if (event.pointerType !== "touch" || gesture.dragging()) {
      const point = toApp(event);
      if (!point) return;
      last = point;
      forward("pointermove", point, 1);
      if (state === "drag" && event.pointerType !== "touch") layer.dataset.dragging = "true";
    }
  }) as EventListener);
  // 长按后的首个 touchmove 可取消，阻止页面滚动；快速滑动不拦截，保持原生滚动与吸附。
  listen(
    layer,
    "touchmove",
    (event) => {
      if (gesture.dragging() && event.cancelable) event.preventDefault();
    },
    { passive: false },
  );
  listen(layer, "pointerup", ((event: PointerEvent) => {
    stopHold();
    delete layer.dataset.dragging;
    const mouse = event.pointerType !== "touch";
    const result = gesture.end(event.pointerId);
    if (result === "none" || result === "native") return;
    const point = toApp(event) ?? last;
    if (result === "tap" && !mouse) forward("pointerdown", point, 1);
    forward("pointerup", point, 0);
    onInteract();
  }) as EventListener);
  const cancel = ((event: PointerEvent) => {
    stopHold();
    delete layer.dataset.dragging;
    if (gesture.cancel(event.pointerId)) forward("pointercancel", last, 0);
  }) as EventListener;
  listen(layer, "pointercancel", cancel);
  listen(layer, "lostpointercapture", cancel);
  // Chrome 在长按菜单后继续派发触摸事件；Firefox 则不再派发，连抬起也没有，只能按超时收尾。
  listen(layer, "contextmenu", (event) => {
    event.preventDefault();
    if (!gesture.dragging()) return;
    clearTimeout(stallTimer);
    stallTimer = setTimeout(abandon, STALLED_MS);
  });
}
