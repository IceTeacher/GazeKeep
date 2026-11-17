import { heroShaderLayers, shouldUseHeroWebGPU } from "./hero-shader.ts";

/** 首屏 Shader 底色与染色取自 tokens.css，随主题由 updateTheme 重读。 */
function palette() {
  const style = getComputedStyle(document.documentElement);
  return {
    colorA: style.getPropertyValue("--shader-base").trim(),
    colorB: style.getPropertyValue("--shader-tint").trim(),
  };
}

/** 首屏 WebGPU 背景：仅在首屏可见、页面前台、未要求减少动态且非 iOS/iPadOS WebKit 时运行；其余保留 CSS 降级。 */
export function initHeroBackdrop() {
  const canvas = document.querySelector<HTMLCanvasElement>("#hero-shader")!;
  const hero = document.getElementById("hero")!;
  const reduce = matchMedia("(prefers-reduced-motion: reduce)");
  const finePointer = matchMedia("(pointer: fine)");
  let instance: Awaited<ReturnType<(typeof import("shaders/js"))["createShader"]>> | undefined;
  let visible = true,
    disposed = false;

  const update = () => {
    const running = visible && !document.hidden && !reduce.matches;
    canvas.hidden = reduce.matches;
    hero.dataset.motionPaused = String(!running);
    if (instance) {
      if (running) instance.resume();
      else instance.pause();
      canvas.dataset.running = String(running);
    }
  };
  const updateTheme = () => {
    const colors = palette();
    instance?.update("gaze-gradient", colors);
    instance?.update("gaze-noise", colors);
    instance?.update("gaze-prism", { beamColor: colors.colorB });
    if (finePointer.matches) instance?.update("gaze-cursor", colors);
  };
  const fallback = () => {
    delete hero.dataset.shaderActive;
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
    if (
      !shouldUseHeroWebGPU({
        userAgent: navigator.userAgent,
        maxTouchPoints: navigator.maxTouchPoints ?? 0,
        hasWebGPU: "gpu" in navigator && Boolean((navigator as Navigator & { gpu?: unknown }).gpu),
      })
    ) {
      canvas.hidden = true;
      return;
    }
    try {
      // 先探测 WebGPU 适配器并在首帧机身与内屏发起加载之后再延迟拉取 1.3 MB 的 shaders 运行时，避免无适配器环境白耗流量及抢占首帧资源。
      await new Promise<void>((resolve) => {
        const start = () => setTimeout(resolve, 120);
        if ("requestAnimationFrame" in window) requestAnimationFrame(start);
        else setTimeout(resolve, 120);
      });
      if (disposed || reduce.matches) return;
      const gpu = (navigator as Navigator & { gpu?: { requestAdapter?: () => Promise<unknown> } }).gpu;
      const adapter = await gpu?.requestAdapter?.();
      if (!adapter || disposed || reduce.matches) return;
      const { createShader } = await import("shaders/js");
      if (disposed) return;
      instance = await createShader(
        canvas,
        { components: heroShaderLayers(palette(), { interactiveCursor: finePointer.matches }) },
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
      hero.dataset.shaderActive = "true";
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
      delete hero.dataset.shaderActive;
      io.disconnect();
      document.removeEventListener("visibilitychange", update);
      reduce.removeEventListener("change", update);
      instance?.destroy();
    },
  };
}
