/** @param {number} value */
const clamp = (value) => Math.max(0, Math.min(1, value));
/** @param {number} value */
const smooth = (value) => {
  const t = clamp(value);
  return t * t * (3 - 2 * t);
};
/** 横移缓动：两端速度与加速度为零。 @param {number} value */
const glide = (value) => {
  const t = clamp(value);
  return t * t * t * (t * (t * 6 - 15) + 10);
};
/** 横移倾斜包络：两端为零且无折点，中段为 1。 @param {number} value */
const bump = (value) => {
  const t = clamp(value);
  return (4 * t * (1 - t)) ** 2;
};
/** 横移时朝运动方向侧转与侧倾，中段轻微后退。 */
export const TILT = Object.freeze({ turn: 18, roll: 4, scale: 0.04 });
/** @param {number} travel -1 向左、1 向右 */
const tilt = (travel) => ({
  turn: TILT.turn * travel,
  rotateZ: TILT.roll * travel,
  scale: 1 - TILT.scale * Math.abs(travel),
});
/** 位置是唯一输入，正反滚动和恢复页面共享同一姿态。 @param {number} progress @param {boolean} mobile */
export function storyPose(progress, mobile = false, viewportWidth = 1280) {
  const enter = smooth(progress);
  const leave = smooth((progress - 2.25) / 0.75);
  const grip = enter * (1 - smooth((progress - 1.45) / 0.35));
  const held = enter * (1 - smooth((progress - 1.8) / 0.2));
  const start = mobile ? 0.12 : 0.52,
    left = mobile ? -0.06 : (-0.38 * viewportWidth) / Math.min(viewportWidth, 1200);
  // 移动端不做横移，只有桌面横移叠加倾斜。
  const travel = mobile ? 0 : bump((progress - 2.25) / 0.75) - bump(progress) || 0;
  const lean = tilt(travel);
  return {
    x: start + (left - start) * glide(progress) * (1 - glide((progress - 2.25) / 0.75)),
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
/** 桌面运行方式到专注范围：手机由右侧镜像到左侧。 @param {number} progress 0 位于运行方式，1 位于专注范围 */
export function featurePose(progress, viewportWidth = 1280) {
  const right = storyPose(4, false, viewportWidth).x,
    travel = -bump(progress) || 0;
  return { x: right - 2 * right * glide(progress), travel, rotateX: 0, ...tilt(travel) };
}
const FOLLOW_KEYS = ["x", "turn", "rotateX", "rotateZ", "scale"];
/**
 * 横移与旋转的短阻尼跟随，只平滑滚轮台阶；停止后收敛到同一位置姿态。
 * @param {Record<string, number> | undefined} current @param {Record<string, number>} target @param {number} dt 毫秒
 */
export function followPose(current, target, dt, tau = 120) {
  if (!current || dt > 100) return { ...target };
  const k = 1 - Math.exp(-Math.max(0, dt) / tau);
  const next = { ...target };
  for (const key of FOLLOW_KEYS) {
    const value = current[key] + (target[key] - current[key]) * k;
    next[key] = Math.abs(target[key] - value) < 1e-3 ? target[key] : value;
  }
  return next;
}
/** @param {number[]} starts @param {number} position */
export function selectSection(starts, position) {
  let index = 0;
  for (let i = 0; i < starts.length; i++) if (position >= starts[i]) index = i;
  return index;
}

/** 首屏初始姿态在滚出首屏后平滑归零。 @param {number} progress @param {boolean} mobile */
export function heroPose(progress, mobile = false) {
  const t = 1 - smooth(progress);
  return {
    rotateX: (mobile ? 5 : 7) * t || 0,
    rotateY: -(mobile ? 10 : 12) * t || 0,
    rotateZ: (mobile ? 5 : 6) * t || 0,
  };
}

export function readingOpacity(progress, center) {
  return 1 - smooth((Math.abs(progress - center) - 0.325) / 0.175);
}
export function sceneState(progress, mobile = false, width = 1280) {
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

/** 为系统导航、设备和最长正文保留互不相交的阅读区域。 */
export function mobileReadingLayout(height, copyHeight) {
  const available = height - 60;
  const phoneHeight = Math.min(available * 0.45, 340, available - copyHeight - 100);
  return {
    phoneHeight: Math.max(210, phoneHeight),
    copyTop: Math.max(210, phoneHeight) + 64,
    compact: height < 600 || phoneHeight < 210,
  };
}

/**
 * 内屏遮罩：桌面进入后端区时仍全黑，随进度渐亮；移动端按设备槽进入视口的位置渐亮。
 * @param {{ progress: number, mobile?: boolean, compact?: boolean, inBackend?: boolean, inWhitelist?: boolean, slotTop?: number, viewport?: number, status?: string }} state
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
}) {
  if (compact) return status === "dim_fallback" ? 1 : 0;
  if (inWhitelist) return 0;
  if (!mobile) return storyPose(progress).dim;
  if (inBackend && slotTop !== undefined) return 1 - smooth((viewport * 0.8 - slotTop) / (viewport * 0.35));
  return smooth((progress - 2.05) / 0.45);
}

/**
 * 移动端三幕：面板固定期间设备用视口坐标，释放后锚定在文档中，两种坐标都不随滚动改写。
 * @param {number} scrollY @param {number} storyTop @param {number} releaseAt 固定面板开始随文档离开时的 scrollY @param {number} header
 * @returns {{ mode: "before" } | { mode: "fixed", top: number } | { mode: "absolute", y: number }}
 */
export function mobileStoryFollow(scrollY, storyTop, releaseAt, header) {
  if (scrollY < storyTop - header) return { mode: "before" };
  if (scrollY <= releaseAt) return { mode: "fixed", top: header + 16 };
  return { mode: "absolute", y: releaseAt + header + 16 };
}
