import { inject } from "vitest";
import "../../src/styles/global.css";

export type ShowcaseControl = {
  mode: "ready" | "error" | "silent";
  modes?: ShowcaseControl["mode"][];
  calls: unknown[][];
};
type FixtureWindow = Window & { __showcaseFixture?: ShowcaseControl };

/** 挂载 Astro 渲染的真实页面并初始化脚本；返回的 unmount 走页面自身的 pagehide 清理路径。 */
export async function mountWebsite(
  lang: "zh" | "en",
  { theme = "dark" as "dark" | "light" | "system", showcase = { mode: "ready", calls: [] } as ShowcaseControl } = {},
) {
  const page = inject("pages")[lang];
  const root = document.documentElement;
  root.lang = page.lang;
  // 与页面头部脚本一致：显式明暗写入偏好存储，system 时由系统配色决定。
  try {
    if (theme === "system") localStorage.removeItem("gazekeep-theme");
    else localStorage.setItem("gazekeep-theme", theme);
  } catch {
    /* 无存储权限的环境按跟随系统处理。 */
  }
  root.dataset.phone = "pending";
  (window as FixtureWindow).__showcaseFixture = showcase;
  document.body.innerHTML = page.body;
  scrollTo(0, 0);
  const { initWebsite } = await import("../../src/scripts/orchestrator.ts");
  initWebsite();
  return {
    showcase,
    unmount() {
      dispatchEvent(new PageTransitionEvent("pagehide", { persisted: false }));
      document.body.innerHTML = "";
      delete (window as FixtureWindow).__showcaseFixture;
      for (const key of ["phone", "theme", "themePreference", "scroll"]) delete root.dataset[key];
      try {
        localStorage.clear();
      } catch {
        /* 无存储权限的环境无需清理。 */
      }
    },
  };
}

/** 内屏加载在 requestIdleCallback（最长 1.5 秒）后才开始，繁忙机器上还会更慢；等待内屏事件统一使用此上限。 */
export const SHOWCASE_TIMEOUT = 15_000;

export const nextFrame = () => new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));

/** 等待浏览器原生滚动与其后至少两帧的渲染更新。 */
export async function scrollToY(y: number) {
  scrollTo({ top: y, behavior: "instant" });
  await nextFrame();
  await nextFrame();
}
