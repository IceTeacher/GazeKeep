const clamp = (value: number) => Math.max(0, Math.min(1, value));
const smooth = (value: number) => {
  const t = clamp(value);
  return t * t * (3 - 2 * t);
};
/** 横移缓动：两端速度与加速度为零。 */
const glide = (value: number) => {
  const t = clamp(value);
  return t * t * t * (t * (t * 6 - 15) + 10);
};
/** 横移倾斜包络：两端为零且无折点，中段为 1。 */
const bump = (value: number) => {
  const t = clamp(value);
  return (4 * t * (1 - t)) ** 2;
};
/** 横移时朝运动方向侧转与侧倾，中段轻微后退。 */
export const TILT = Object.freeze({ turn: 18, roll: 4, scale: 0.04 });
/** @param travel -1 向左、1 向右 */
const tilt = (travel: number) => ({
  turn: TILT.turn * travel,
  rotateZ: TILT.roll * travel,
  scale: 1 - TILT.scale * Math.abs(travel),
});
/** 位置是唯一输入，正反滚动和恢复页面共享同一姿态。 */
/** 工作流标题停靠在进度约 0.07 处：此前手机保持首屏回正后的正面姿态、不显示手部，之后才进入第一幕。 */
const ENTER_START = 0.2;
export function storyPose(progress: number, mobile = false, viewportWidth = 1280) {
  // 第一幕的进入、横移与侧倾都在标题停靠区之后开始，0.2→1 映射为 0→1。
  const first = (progress - ENTER_START) / (1 - ENTER_START);
  const enter = smooth(first);
  const grip = enter * (1 - smooth((progress - 1.45) / 0.35));
  const held = enter * (1 - smooth((progress - 1.8) / 0.2));
  const start = mobile ? 0.12 : 0.52,
    left = mobile ? -0.06 : (-0.38 * viewportWidth) / Math.min(viewportWidth, 1200);
  // 移动端不做横移，只有桌面横移叠加倾斜。
  const travel = mobile ? 0 : bump((progress - 2.25) / 0.75) - bump(first) || 0;
  const lean = tilt(travel);
  return {
    x: start + (left - start) * glide(first) * (1 - glide((progress - 2.25) / 0.75)),
    turn: -(mobile ? 20 : 28) * held + lean.turn || 0,
    grip,
    rotateX: (mobile ? 2 : 3) * held || 0,
    rotateZ: -(mobile ? 4 : 6) * held + lean.rotateZ || 0,
    scale: (1 - (mobile ? 0.22 : 0.18) * held) * lean.scale,
    scene: enter,
    travel,
    dim: smooth((progress - 2.05) / 0.45) * (1 - smooth((progress - 3.55) / 0.45)),
  };
}
/**
 * 桌面功能区之间的横移：镜像第二幕到第三幕，运行方式到专注范围由右向左，专注范围到设置再回到右侧，
 * 过程中朝运动方向侧转、侧倾。
 * @param progress 0 位于出发区段，1 位于到达区段
 * @param side 到达侧：-1 左、1 右
 */
export function featurePose(progress: number, viewportWidth = 1280, side: -1 | 1 = -1) {
  const right = storyPose(4, false, viewportWidth).x,
    travel = side * bump(progress) || 0;
  return { x: -side * right + 2 * side * right * glide(progress), travel, rotateX: 0, ...tilt(travel) };
}
/**
 * 到达区段的文案按手机实际横向位置淡入：越过中线后才开始显示，对应三幕文案的交叉淡入，手机掠过时不压住清晰文字。
 * @param x 阻尼跟随后的横向位置
 * @param side 手机到达侧，与 featurePose 一致
 */
export function featureReveal(x: number, viewportWidth = 1280, side: -1 | 1 = -1) {
  return smooth((side * x) / storyPose(4, false, viewportWidth).x);
}
/**
 * 内屏演示场景：工作流标题处自动浏览主页与设置，第一幕检测往复，第二幕滚动隐私页，
 * 运行方式轮换后端，专注范围勾选与搜索，设置区逐项修改；其余阶段不演示。
 */
export function demoScene({ route, status, tour }: { route: string; status: string; tour: boolean }) {
  if (route === "home") return status === "sampling" ? "detect" : tour && status === "idle" ? "tour" : "none";
  if (route === "privacy") return status === "gaze_detected" ? "privacy" : "none";
  return ({ backend: "backends", whitelist: "apps", settings: "settings" } as Record<string, string>)[route] ?? "none";
}
/**
 * 专注范围横移的滚动区间：运行方式列表离开设备所在高度后起步，在范围文案停靠点结束，
 * 使横移落在吸附动画的减速段，与第二幕到第三幕一致。
 * @param layout 文档坐标
 */
export function featureSpan({
  listBottom,
  headingTop,
  stageTop,
  phoneHeight,
  snapTop,
  viewport,
}: {
  listBottom: number;
  headingTop: number;
  stageTop: number;
  phoneHeight: number;
  snapTop: number;
  viewport: number;
}) {
  const end = headingTop - snapTop;
  return { start: Math.min(listBottom - (stageTop + phoneHeight * 0.12), end - viewport * 0.25), end };
}
const FOLLOW_KEYS = ["x", "turn", "rotateX", "rotateZ", "scale"] as const;
export type FollowPose = Record<(typeof FOLLOW_KEYS)[number], number>;
/**
 * 横移与旋转的短阻尼跟随，只平滑滚轮台阶；停止后收敛到同一位置姿态。
 * @param dt 毫秒
 */
export function followPose<T extends FollowPose>(current: T | undefined, target: T, dt: number, tau = 120): T {
  if (!current || dt > 100) return { ...target };
  const k = 1 - Math.exp(-Math.max(0, dt) / tau);
  const next = { ...target };
  for (const key of FOLLOW_KEYS) {
    const value = current[key] + (target[key] - current[key]) * k;
    next[key] = Math.abs(target[key] - value) < 1e-3 ? target[key] : value;
  }
  return next;
}
export function selectSection(starts: readonly number[], position: number) {
  let index = 0;
  for (let i = 0; i < starts.length; i++) if (position >= starts[i]) index = i;
  return index;
}

/** 首屏初始姿态在滚出首屏后平滑归零。 */
export function heroPose(progress: number, mobile = false) {
  const t = 1 - smooth(progress);
  return {
    rotateX: (mobile ? 5 : 7) * t || 0,
    rotateY: -(mobile ? 10 : 12) * t || 0,
    rotateZ: (mobile ? 5 : 6) * t || 0,
  };
}

/**
 * 首屏电影式漂移（Ken Burns）：几个互质周期的缓慢正弦叠加成不重复的平移、倾斜与推近，
 * 均从 0 起步避免首帧跳变；随滚出首屏按 heroPose 同一包络衰减为 0。
 * @param seconds 自首屏出现起的秒数
 * @param progress 与 heroPose 相同的滚出进度
 */
export function heroDrift(seconds: number, progress: number, mobile = false) {
  const weight = (1 - smooth(progress)) * (mobile ? 0.6 : 1);
  const wave = (period: number) => Math.sin((2 * Math.PI * seconds) / period);
  return {
    x: 0.015 * wave(23) * weight || 0,
    rotateX: 2 * wave(19) * weight || 0,
    rotateY: 4.2 * wave(26) * weight || 0,
    rotateZ: 1.2 * wave(31) * weight || 0,
    scale: 0.015 * (1 - Math.cos((2 * Math.PI * seconds) / 34)) * weight || 0,
  };
}

export function readingOpacity(progress: number, center: number) {
  return 1 - smooth((Math.abs(progress - center) - 0.325) / 0.175);
}
export function sceneState(progress: number, mobile = false, width = 1280) {
  const pose = storyPose(progress, mobile, width);
  return {
    ...pose,
    route: progress >= 3.4 ? "backend" : progress >= 1.35 ? "privacy" : "home",
    status:
      progress >= 3.4
        ? "backend_lsposed"
        : progress >= 2.5
          ? "dim_fallback"
          : progress >= 1.35
            ? "gaze_detected"
            : progress >= 0.5
              ? "sampling"
              : "idle",
    effect:
      progress >= 2.15 && progress < 3.7
        ? "screen-dim"
        : progress >= 1.35 && progress < 2.15
          ? "screen-glow"
          : progress >= 0.5 && progress < 1.35
            ? "camera-pulse"
            : "none",
  };
}

/** 移动端三幕每幕占 0.95 屏滚动距离，进入三幕区时位于半幕。 */
const MOBILE_STEP = 0.95;
export function mobileStoryProgress(scrollY: number, storyTop: number, viewport: number) {
  return 0.5 + (scrollY - storyTop) / (viewport * MOBILE_STEP);
}
/**
 * 移动端三幕吸附点：相对三幕区顶部的滚动偏移，停靠时进度恰为整数幕。
 */
export function mobileStorySnaps(viewport: number, count: number) {
  return Array.from({ length: count }, (_, i) => (i + 0.5) * viewport * MOBILE_STEP);
}

/** 为系统导航、设备和最长正文保留互不相交的阅读区域。 */
export function mobileReadingLayout(height: number, copyHeight: number) {
  const available = height - 60;
  const phoneHeight = Math.min(available * 0.58, 460, available - copyHeight - 100);
  return {
    phoneHeight: Math.max(210, phoneHeight),
    copyTop: Math.max(210, phoneHeight) + 64,
    compact: height < 600 || phoneHeight < 210,
  };
}

/** 移动端后端与范围的设备槽位于文档流，不与正文共享一屏，可比三幕手机更大。 */
export function mobileFeaturePhoneHeight(height: number) {
  return Math.max(210, Math.min((height - 60) * 0.7, 560));
}

/**
 * 内屏遮罩：桌面进入后端区时仍全黑，随进度渐亮；移动端按设备槽进入视口的位置渐亮。
 */
export function screenDim({
  progress,
  mobile = false,
  compact = false,
  inBackend = false,
  inWhitelist = false,
  slotTop,
  viewport = 0,
  status,
}: {
  progress: number;
  mobile?: boolean;
  compact?: boolean;
  inBackend?: boolean;
  inWhitelist?: boolean;
  slotTop?: number;
  viewport?: number;
  status?: string;
}) {
  if (compact) return status === "dim_fallback" ? 1 : 0;
  if (inWhitelist) return 0;
  if (!mobile) return storyPose(progress).dim;
  if (inBackend && slotTop !== undefined) return 1 - smooth((viewport * 0.8 - slotTop) / (viewport * 0.35));
  return smooth((progress - 2.05) / 0.45);
}

/**
 * 移动端三幕：面板固定期间设备用视口坐标，释放后锚定在文档中，两种坐标都不随滚动改写。
 * @param releaseAt 固定面板开始随文档离开时的 scrollY
 */
export function mobileStoryFollow(
  scrollY: number,
  storyTop: number,
  releaseAt: number,
  header: number,
): { mode: "before" } | { mode: "fixed"; top: number } | { mode: "absolute"; y: number } {
  if (scrollY < storyTop - header) return { mode: "before" };
  if (scrollY <= releaseAt) return { mode: "fixed", top: header + 16 };
  return { mode: "absolute", y: releaseAt + header + 16 };
}
