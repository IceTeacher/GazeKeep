export type Point = { x: number; y: number };
export type Size = { width: number; height: number };

const REVEAL_DURATION = 480;
const SETTLE_TIMEOUT = 160;
/** 视图过渡的回调要等下一次渲染机会；页面繁忙或在后台时可能长时间不执行，超时后直接应用，界面不随动画卡住。 */
const APPLY_TIMEOUT = 1000;

/** 揭示圆需覆盖距起点最远的视口角。 */
export function revealRadius(origin: Point, viewport: Size) {
  return Math.hypot(Math.max(origin.x, viewport.width - origin.x), Math.max(origin.y, viewport.height - origin.y));
}

/** 取点击发生时的指针位置；键盘触发时退回目标控件中心。 */
export function clickOrigin(target: Element, event?: Pick<MouseEvent, "detail" | "clientX" | "clientY">): Point {
  if (event && event.detail > 0) {
    return { x: event.clientX, y: event.clientY };
  }
  const box = target.getBoundingClientRect();
  return { x: box.left + box.width / 2, y: box.top + box.height / 2 };
}

/** 等待内嵌展示页完成两帧绘制，使截取的新快照包含已更新的手机内屏；超时后不再等待。 */
export function settleFrame(frame?: HTMLIFrameElement | null, timeoutMs = SETTLE_TIMEOUT) {
  const view = frame?.contentWindow ?? window;
  return Promise.race([
    new Promise<void>((resolve) => view.requestAnimationFrame(() => view.requestAnimationFrame(() => resolve()))),
    new Promise<void>((resolve) => setTimeout(resolve, timeoutMs)),
  ]);
}

/**
 * 以起点为中心播放圆形揭示视图过渡（参考主题切换动画）：
 * 新视图帧的 clip-path 圆从 0 扩到覆盖全屏；条件不满足时直接应用状态变化。
 */
export async function runCircularReveal(options: {
  origin: Point;
  animate: boolean;
  apply: () => Promise<void> | void;
  frame?: HTMLIFrameElement | null;
  flag?: "themeTransition" | "localeTransition";
}) {
  const supported = typeof document.startViewTransition === "function";
  if (!options.animate || !supported) {
    await options.apply();
    return;
  }
  const root = document.documentElement;
  const flag = options.flag ?? "themeTransition";
  root.dataset[flag] = "true";
  let applied = false;
  let applyPromise: Promise<void> | undefined;
  const applyOnce = () => {
    if (!applied) {
      applied = true;
      applyPromise = Promise.resolve(options.apply());
    }
    return applyPromise!;
  };
  const fallback = setTimeout(() => {
    void applyOnce();
  }, APPLY_TIMEOUT);
  const transition = document.startViewTransition(async () => {
    clearTimeout(fallback);
    await applyOnce();
    await settleFrame(options.frame);
  });
  try {
    await transition.ready;
    const { x, y } = options.origin;
    const radius = revealRadius(options.origin, { width: innerWidth, height: innerHeight });
    root.animate(
      { clipPath: [`circle(0px at ${x}px ${y}px)`, `circle(${radius}px at ${x}px ${y}px)`] },
      {
        duration: REVEAL_DURATION,
        easing: "cubic-bezier(0.4, 0, 0.2, 1)",
        pseudoElement: "::view-transition-new(root)",
      },
    );
    await transition.finished;
  } catch {
    /* 过渡被跳过时变更已在回调中应用。 */
  } finally {
    clearTimeout(fallback);
    await applyOnce();
    delete root.dataset[flag];
  }
}
