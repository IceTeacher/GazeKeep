import { expect, test } from "vitest";
import { HERO_SHADER_COMPONENTS, heroShaderLayers, shouldUseHeroWebGPU } from "../../../src/scripts/hero-shader.ts";

test("iOS 与 iPadOS Safari（含桌面模式 UA）禁用首屏重度 WebGPU Shader", () => {
  const macDesktopSafari =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/27.2 Safari/605.1.15";
  const iphoneSafari =
    "Mozilla/5.0 (iPhone; CPU iPhone OS 18_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.4 Mobile/15E148 Safari/604.1";
  const ipadLegacy =
    "Mozilla/5.0 (iPad; CPU OS 18_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.4 Mobile/15E148 Safari/604.1";
  const desktopChrome =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36";

  expect(shouldUseHeroWebGPU({ userAgent: iphoneSafari, maxTouchPoints: 5, hasWebGPU: true })).toBe(false);
  expect(shouldUseHeroWebGPU({ userAgent: ipadLegacy, maxTouchPoints: 5, hasWebGPU: true })).toBe(false);
  // iPadOS 请求桌面网站时 UA 伪装为 Macintosh，但仍具备多点触控与 AppleWebKit 特征
  expect(shouldUseHeroWebGPU({ userAgent: macDesktopSafari, maxTouchPoints: 5, hasWebGPU: true })).toBe(false);

  // 桌面 Safari 与桌面 Chrome 正常启用
  expect(shouldUseHeroWebGPU({ userAgent: macDesktopSafari, maxTouchPoints: 0, hasWebGPU: true })).toBe(true);
  expect(shouldUseHeroWebGPU({ userAgent: desktopChrome, maxTouchPoints: 0, hasWebGPU: true })).toBe(true);
  expect(shouldUseHeroWebGPU({ userAgent: desktopChrome, maxTouchPoints: 0, hasWebGPU: false })).toBe(false);
});

test("非精细指针环境跳过 CursorTrail 逐像素全屏轨迹采样，同时保留完整注册表清单", () => {
  const colors = { colorA: "#080d18", colorB: "#377e68" };
  const touchLayers = heroShaderLayers(colors, { interactiveCursor: false });
  expect(touchLayers.some((layer) => layer.type === "CursorTrail")).toBe(false);
  expect(touchLayers.map((layer) => layer.type)).toEqual(["LinearGradient", "SimplexNoise", "Prism", "WaveDistortion"]);

  const desktopLayers = heroShaderLayers(colors, { interactiveCursor: true });
  expect(desktopLayers.some((layer) => layer.type === "CursorTrail")).toBe(true);
  expect(HERO_SHADER_COMPONENTS).toContain("CursorTrail");
});
