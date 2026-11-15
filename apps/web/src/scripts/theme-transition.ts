import type { Theme } from "./theme.ts";
import { revealRadius, runCircularReveal, type Point } from "./reveal-transition.ts";

export { revealRadius };

/** 只有实际明暗改变、未要求减少动态且支持视图过渡时才播放揭示动画。 */
export function shouldReveal(options: { from: Theme; to: Theme; reducedMotion: boolean; supported: boolean }) {
  return options.from !== options.to && !options.reducedMotion && options.supported;
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
  await runCircularReveal({
    origin: options.origin,
    animate: !options.reducedMotion && options.from !== options.to,
    apply: options.apply,
    frame: options.frame,
    flag: "themeTransition",
  });
}
