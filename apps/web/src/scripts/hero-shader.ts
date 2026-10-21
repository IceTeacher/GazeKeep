/** 首屏 shader 图层的唯一来源：页面按此创建，构建按其组件类型裁剪 shader 注册表。 */
export type ShaderColors = { colorA: string; colorB: string };

export function heroShaderLayers(colors: ShaderColors) {
  return [
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
    {
      id: "gaze-cursor",
      type: "CursorTrail",
      props: { ...colors, radius: 0.5, length: 0.4, softness: 0.9, opacity: 0.2 },
    },
  ];
}

export const HERO_SHADER_COMPONENTS = heroShaderLayers({ colorA: "", colorB: "" }).map((layer) => layer.type);
