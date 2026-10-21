import { inject } from "vitest";
import "../../../src/styles/global.css";

export type ShowcaseControl = {
  mode: "ready" | "error" | "silent";
  modes?: ShowcaseControl["mode"][];
  calls: unknown[][];
};
type FixtureWindow = Window & { __showcaseFixture?: ShowcaseControl };

/** 挂载 Astro 渲染的真实页面并初始化脚本；返回的 unmount 走页面自身的 pagehide 清理路径。 */
export async function mountWebsite(
  lang: "zh" | "en",
  { theme = "dark", showcase = { mode: "ready", calls: [] } as ShowcaseControl } = {},
) {
  const page = inject("pages")[lang];
  const root = document.documentElement;
  root.lang = page.lang;
  root.dataset.theme = theme;
  root.dataset.phone = "pending";
  (window as FixtureWindow).__showcaseFixture = showcase;
  document.body.innerHTML = page.body;
  scrollTo(0, 0);
  const { initWebsite } = await import("../../../src/scripts/orchestrator.ts");
  initWebsite();
  return {
    showcase,
    unmount() {
      dispatchEvent(new PageTransitionEvent("pagehide", { persisted: false }));
      document.body.innerHTML = "";
      delete (window as FixtureWindow).__showcaseFixture;
      for (const key of ["phone", "theme", "scroll"]) delete root.dataset[key];
      try {
        localStorage.clear();
      } catch {
        /* 无存储权限的环境无需清理。 */
      }
    },
  };
}

export const nextFrame = () => new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));

/** 等待浏览器原生滚动与其后至少两帧的渲染更新。 */
export async function scrollToY(y: number) {
  scrollTo({ top: y, behavior: "instant" });
  await nextFrame();
  await nextFrame();
}
