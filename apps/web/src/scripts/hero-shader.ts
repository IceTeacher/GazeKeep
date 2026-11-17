/** 首屏 shader 图层的唯一来源：页面按此创建，构建按其组件类型裁剪 shader 注册表。 */
export type ShaderColors = { colorA: string; colorB: string };

export interface HeroWebGPUEnv {
  userAgent: string;
  maxTouchPoints: number;
  hasWebGPU: boolean;
}

/**
 * 判断当前运行环境是否适合启用首屏 WebGPU Shader。
 * iOS 与 iPadOS（含请求桌面站点的 Macintosh 多点触控 UA）在 WebKit GPU Process 下运行多 Pass rgba16float
 * WebGPU 会严重掉帧，并与同页 iframe 内的 Skiko WebGL2 发生合成冲突导致内屏纯白，故直接回退 CSS 光学背景。
 */
export function shouldUseHeroWebGPU({ userAgent, maxTouchPoints, hasWebGPU }: HeroWebGPUEnv): boolean {
  if (!hasWebGPU) return false;
  if (/iPhone|iPad|iPod/i.test(userAgent)) return false;
  if (/Macintosh/i.test(userAgent) && maxTouchPoints > 1 && /AppleWebKit/i.test(userAgent)) return false;
  return true;
}

export function heroShaderLayers(colors: ShaderColors, options?: { interactiveCursor?: boolean }) {
  const interactiveCursor = options?.interactiveCursor ?? true;
  const base = [
    { id: "gaze-gradient", type: "LinearGradient", props: { ...colors, angle: 120 } },
    { id: "gaze-noise", type: "SimplexNoise", props: { ...colors, scale: 2.4, speed: 0.22, opacity: 0.16 } },
    {
      id: "gaze-prism",
      type: "Prism",
      props: {
        position: { x: 0.1, y: 0.8 },
        splitPosition: { x: 0.56, y: 0.35 },
        beamColor: colors.colorB,
        beamWidth: 0.06,
        saturation: 0.08,
        intensity: 1.3,
        spread: 0.45,
        speed: 0.08,
        opacity: 0.4,
      },
    },
    { type: "WaveDistortion", props: { strength: 0.12, frequency: 2.2, speed: 0.18, angle: 35 } },
  ];
  return interactiveCursor
    ? [
        ...base,
        {
          id: "gaze-cursor",
          type: "CursorTrail",
          props: { ...colors, radius: 0.5, length: 0.4, softness: 0.9, opacity: 0.2 },
        },
      ]
    : base;
}

export const HERO_SHADER_COMPONENTS = heroShaderLayers({ colorA: "", colorB: "" }, { interactiveCursor: true }).map(
  (layer) => layer.type,
);
