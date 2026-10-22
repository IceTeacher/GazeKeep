import type { Theme } from "./theme.ts";

type Point = { x: number; y: number };
type Size = { width: number; height: number };

/** 揭示圆需覆盖距起点最远的视口角。 */
export function revealRadius(origin: Point, viewport: Size) {
  return Math.hypot(Math.max(origin.x, viewport.width - origin.x), Math.max(origin.y, viewport.height - origin.y));
}

/** 只有实际明暗改变、未要求减少动态且支持视图过渡时才播放揭示动画。 */
export function shouldReveal(options: { from: Theme; to: Theme; reducedMotion: boolean; supported: boolean }) {
  return options.from !== options.to && !options.reducedMotion && options.supported;
}

const REVEAL_DURATION = 480;
const SETTLE_TIMEOUT = 160;
/** 视图过渡的回调要等下一次渲染机会；页面繁忙或在后台时可能长时间不执行，超时后直接应用，主题不随动画卡住。 */
const APPLY_TIMEOUT = 1000;

/** 等待内嵌展示页完成两帧绘制，使截取的新快照包含已换肤的手机内屏；超时后不再等待。 */
function settleFrame(frame: HTMLIFrameElement | null) {
  const view = frame?.contentWindow ?? window;
  return Promise.race([
    new Promise<void>((resolve) => view.requestAnimationFrame(() => view.requestAnimationFrame(() => resolve()))),
    new Promise<void>((resolve) => setTimeout(resolve, SETTLE_TIMEOUT)),
  ]);
}

/**
 * 以点击点为圆心扩散切换主题（参考 Ant Design 站点）：视图过渡截取新旧两帧，
 * 新主题帧的 clip-path 圆从 0 扩到覆盖全屏。条件不满足时直接切换。
 */
export async function transitionTheme(options: {
  origin: Point;
  from: Theme;
  to: Theme;
  reducedMotion: boolean;
  apply: () => void;
  frame?: HTMLIFrameElement | null;
}) {
  const supported = typeof document.startViewTransition === "function";
  if (!shouldReveal({ ...options, supported })) {
    options.apply();
    return;
  }
  const root = document.documentElement;
  root.dataset.themeTransition = "true";
  let applied = false;
  const apply = () => {
    if (applied) return;
    applied = true;
    options.apply();
  };
  const fallback = setTimeout(apply, APPLY_TIMEOUT);
  const transition = document.startViewTransition(async () => {
    clearTimeout(fallback);
    apply();
    await settleFrame(options.frame ?? null);
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
    /* 过渡被跳过时主题已在回调中应用。 */
  } finally {
    clearTimeout(fallback);
    apply();
    delete root.dataset.themeTransition;
  }
}
