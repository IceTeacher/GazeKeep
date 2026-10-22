import { heroShaderLayers } from "./hero-shader.ts";

/** 首屏 Shader 底色与染色取自 tokens.css，随主题由 updateTheme 重读。 */
function palette() {
  const style = getComputedStyle(document.documentElement);
  return {
    colorA: style.getPropertyValue("--shader-base").trim(),
    colorB: style.getPropertyValue("--shader-tint").trim(),
  };
}

/** 首屏 WebGPU 背景：仅在首屏可见、页面前台且未要求减少动态时运行；不支持或失败时保留 CSS 降级。 */
export function initHeroBackdrop() {
  const canvas = document.querySelector<HTMLCanvasElement>("#hero-shader")!;
  const hero = document.getElementById("hero")!;
  const reduce = matchMedia("(prefers-reduced-motion: reduce)");
  let instance: Awaited<ReturnType<(typeof import("shaders/js"))["createShader"]>> | undefined;
  let visible = true,
    disposed = false,
    raf = 0,
    elapsed = 0,
    last = 0;
  const tick = (now: number) => {
    if (last) elapsed += (now - last) / 1000;
    last = now;
    instance?.update("gaze-gradient", { angle: 120 + Math.sin(elapsed * 0.16) * 65 });
    raf = requestAnimationFrame(tick);
  };
  const update = () => {
    const running = visible && !document.hidden && !reduce.matches;
    canvas.hidden = reduce.matches;
    hero.dataset.motionPaused = String(!running);
    cancelAnimationFrame(raf);
    last = 0;
    if (instance) {
      if (running) instance.resume();
      else instance.pause();
      canvas.dataset.running = String(running);
      if (running) raf = requestAnimationFrame(tick);
    }
  };
  const updateTheme = () => {
    const colors = palette();
    instance?.update("gaze-gradient", colors);
    instance?.update("gaze-noise", colors);
    instance?.update("gaze-prism", { beamColor: colors.colorB });
    instance?.update("gaze-cursor", colors);
  };
  const fallback = () => {
    canvas.dataset.fallback = "true";
    canvas.style.opacity = "0";
  };
  const io = new IntersectionObserver(
    ([entry]) => {
      visible = entry.isIntersecting && entry.intersectionRatio > 0.01;
      update();
    },
    { threshold: [0, 0.01] },
  );
  io.observe(hero);
  document.addEventListener("visibilitychange", update);
  reduce.addEventListener("change", update);
  update();

  async function create() {
    if (!("gpu" in navigator)) return;
    try {
      const { createShader } = await import("shaders/js");
      if (disposed) return;
      instance = await createShader(
        canvas,
        { components: heroShaderLayers(palette()) },
        {
          disableTelemetry: true,
          onError: (reason) => {
            canvas.dataset.failureReason = reason;
            fallback();
          },
        },
      );
      if (disposed) {
        instance.destroy();
        return;
      }
      if (instance.getFailureReason()) {
        instance.destroy();
        instance = undefined;
        fallback();
        return;
      }
      updateTheme();
      update();
    } catch {
      fallback();
    }
  }
  void create();
  return {
    updateTheme,
    dispose() {
      disposed = true;
      io.disconnect();
      cancelAnimationFrame(raf);
      document.removeEventListener("visibilitychange", update);
      reduce.removeEventListener("change", update);
      instance?.destroy();
    },
  };
}
