import {
  type Bridge,
  createShowcaseState,
  createShowcaseAttempts,
  createRenderGate,
  chooseRuntime,
} from "./showcase-state.ts";
import { initStoryScroll } from "./story-scroll";
import { initCameraHalo } from "./camera-halo";
import { initPhoneDisplay } from "./phone-display";
import { heroShaderLayers } from "./hero-shader.ts";
import type { DemoState, KmpRoute } from "../data/schema";
type DemoWindow = Window & { GazeKeepWebShowcaseBridge?: Bridge };
let initialized = false;
export function initWebsite() {
  if (initialized) return;
  initialized = true;
  const disposers: (() => void)[] = [];
  let disposed = false;
  const content = JSON.parse(document.querySelector("#showcase-content")!.textContent!) as {
    locale: string;
    status: Record<DemoState, string>;
    loading: string;
  };
  const state = createShowcaseState();
  const renderGate = createRenderGate();
  let requestedRoute = "",
    renderTimeout: ReturnType<typeof setTimeout> | undefined;
  const frame = document.querySelector<HTMLIFrameElement>("#kmp-frame")!;
  const screen = document.querySelector<HTMLElement>(".phone-screen")!;
  const phone = document.querySelector<HTMLElement>("#phone-chassis")!;
  const placeholder = document.querySelector<HTMLElement>("#phone-placeholder")!;
  const recovery = document.querySelector<HTMLButtonElement>("#retry-showcase")!;
  const message = document.querySelector<HTMLElement>("#load-message")!;
  const statusLabel = document.querySelector<HTMLElement>("#demo-status")!;
  const reduce = matchMedia("(prefers-reduced-motion: reduce)");
  const systemTheme = matchMedia("(prefers-color-scheme: dark)");
  const phoneDisplay = initPhoneDisplay();
  disposers.push(() => phoneDisplay.dispose());
  disposers.push(initCameraHalo());
  function listen(target: EventTarget, type: string, fn: EventListener, options?: AddEventListenerOptions) {
    target.addEventListener(type, fn, options);
    disposers.push(() => target.removeEventListener(type, fn, options));
  }
  listen(document.querySelector(".explore")!, "click", ((event: MouseEvent) => {
    if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
    event.preventDefault();
    if (location.hash !== "#workflow") history.pushState(null, "", "#workflow");
    document
      .querySelector("#workflow")!
      .scrollIntoView({ behavior: reduce.matches ? "instant" : "smooth", block: "start" });
  }) as EventListener);
  let updateShaderTheme = () => {};
  const setAppearance = () => {
    updateShaderTheme();
    state.update({ locale: content.locale, dark: document.documentElement.dataset.theme === "dark" });
    if (frame.contentDocument?.documentElement) frame.contentDocument.documentElement.lang = content.locale;
  };
  setAppearance();
  listen(document.querySelector("#theme-toggle")!, "click", () => {
    const theme = document.documentElement.dataset.theme === "dark" ? "light" : "dark";
    document.documentElement.dataset.theme = theme;
    try {
      localStorage.setItem("gazekeep-theme", theme);
    } catch {
      /* 无存储权限时仍可切换本页主题。 */
    }
    setAppearance();
  });
  listen(systemTheme, "change", () => {
    let saved: string | null = null;
    try {
      saved = localStorage.getItem("gazekeep-theme");
    } catch {
      /* 使用系统主题。 */
    }
    if (!saved) {
      document.documentElement.dataset.theme = systemTheme.matches ? "dark" : "light";
      setAppearance();
    }
  });
  document.querySelectorAll<HTMLButtonElement>(".faq-trigger").forEach((button) =>
    listen(button, "click", () => {
      const open = button.getAttribute("aria-expanded") !== "true";
      button.setAttribute("aria-expanded", String(open));
      document.getElementById(button.getAttribute("aria-controls")!)!.inert = !open;
    }),
  );
  function sync(route: KmpRoute, status: DemoState, effect = "none") {
    state.update({ route, status });
    phone.dataset.effect = effect;
    statusLabel.textContent = content.status[status];
  }
  document.querySelectorAll<HTMLButtonElement>("[data-backend]").forEach((card) => {
    const activate = () => {
      document.querySelectorAll("[data-backend]").forEach((c) => c.setAttribute("aria-pressed", String(c === card)));
      sync("backend", `backend_${card.dataset.backend}` as DemoState);
    };
    listen(card, "click", activate);
    listen(card, "focus", activate);
    listen(card, "mouseenter", () => {
      if (matchMedia("(hover:hover)").matches) activate();
    });
    listen(card, "pointermove", ((event: PointerEvent) => {
      const box = card.getBoundingClientRect();
      card.style.setProperty("--mouse-x", `${event.clientX - box.left}px`);
      card.style.setProperty("--mouse-y", `${event.clientY - box.top}px`);
    }) as EventListener);
  });
  listen(document.querySelector("#show-limit")!, "click", () => sync("settings", "idle"));
  let timeout: ReturnType<typeof setTimeout> | undefined;
  const attempts = createShowcaseAttempts();
  let runtime: "wasm" | "js";
  try {
    runtime = chooseRuntime(
      typeof WebAssembly !== "undefined" &&
        WebAssembly.validate(new Uint8Array([0, 97, 115, 109, 1, 0, 0, 0, 1, 5, 1, 95, 1, 127, 0])),
    );
  } catch {
    runtime = "js";
  }
  function failed(attempt: number) {
    if (!attempts.fail(attempt)) return;
    clearTimeout(timeout);
    clearTimeout(renderTimeout);
    screen.dataset.ready = "false";
    if (runtime === "wasm") {
      runtime = "js";
      load();
      return;
    }
    state.fail();
    recovery.hidden = false;
    message.textContent = content.locale === "zh" ? "界面暂时不可用" : "Interface unavailable";
    placeholder.querySelector(".loader")?.setAttribute("hidden", "");
    window.dispatchEvent(new Event("gazekeep:scene-ready"));
  }
  function load() {
    if (disposed) return;
    clearTimeout(timeout);
    state.loading();
    renderGate.reset();
    requestedRoute = "";
    clearTimeout(renderTimeout);
    recovery.hidden = true;
    screen.dataset.ready = "false";
    message.textContent = content.loading;
    placeholder.querySelector(".loader")?.removeAttribute("hidden");
    const attempt = attempts.begin();
    frame.src = `/kmp-showcase/${runtime}/index.html?attempt=${attempt}`;
    timeout = setTimeout(() => failed(attempt), 60000);
  }
  listen(window, "gazekeep:ready", ((event: CustomEvent<{ attempt: number }>) => {
    if (!attempts.ready(event.detail?.attempt)) return;
    const bridge = (frame.contentWindow as DemoWindow | null)?.GazeKeepWebShowcaseBridge;
    if (!bridge) return;
    clearTimeout(timeout);
    (window as DemoWindow).GazeKeepWebShowcaseBridge = bridge;
    state.attach({
      ...bridge,
      setScreen(route: string) {
        if (requestedRoute === route) return;
        requestedRoute = route;
        const revision = renderGate.request(route);
        bridge.setScreen(route, revision);
        clearTimeout(renderTimeout);
        renderTimeout = setTimeout(() => failed(event.detail.attempt), 8000);
      },
    });
    setAppearance();
    screen.dataset.ready = "true";
    recovery.hidden = true;
  }) as EventListener);
  listen(window, "gazekeep:rendered", ((event: CustomEvent<{ attempt: number; route: string; revision: number }>) => {
    const d = event.detail;
    if (!attempts.ready(d?.attempt) || !renderGate.accept(d.route, d.revision)) return;
    clearTimeout(renderTimeout);
    window.dispatchEvent(new Event("gazekeep:scene-ready"));
  }) as EventListener);
  listen(window, "gazekeep:error", ((event: CustomEvent<{ attempt: number }>) => {
    failed(event.detail?.attempt);
  }) as EventListener);
  listen(document.querySelector("#retry-showcase")!, "click", load);
  const retryHit = document.querySelector<HTMLButtonElement>("#retry-hit")!;
  listen(retryHit, "click", load);
  const menu = document.querySelector<HTMLButtonElement>("#menu-toggle")!;
  const nav = document.querySelector<HTMLElement>(".nav-wrap")!;
  const closeMenu = () => {
    menu.setAttribute("aria-expanded", "false");
    nav.dataset.menu = "false";
  };
  listen(menu, "click", () => {
    const open = menu.getAttribute("aria-expanded") !== "true";
    menu.setAttribute("aria-expanded", String(open));
    nav.dataset.menu = String(open);
  });
  document.querySelectorAll(".mobile-menu a").forEach((a) => listen(a, "click", closeMenu));
  listen(window, "keydown", ((e: KeyboardEvent) => {
    if (e.key === "Escape" && menu.getAttribute("aria-expanded") === "true") {
      closeMenu();
      menu.focus();
    }
  }) as EventListener);
  let hitRaf = 0;
  const heroSection = document.getElementById("hero")!;
  const updateChrome = () => {
    cancelAnimationFrame(hitRaf);
    nav.dataset.scrolled = String(scrollY > 20);
    // 首屏完全离开导航后才切换为正文区导航底，着色区与底部渐变区都使用渐隐层。
    nav.dataset.zone = heroSection.getBoundingClientRect().bottom > nav.offsetHeight ? "hero" : "content";
    const button = document.querySelector<HTMLElement>("#retry-showcase")!;
    const box = button.getBoundingClientRect();
    retryHit.hidden =
      button.hidden ||
      box.bottom < 72 ||
      box.top > innerHeight ||
      getComputedStyle(document.querySelector(".screen-dim")!).opacity === "1";
    retryHit.style.left = `${box.left + box.width / 2}px`;
    retryHit.style.top = `${box.top + box.height / 2}px`;
    if (!button.hidden && !document.hidden) hitRaf = requestAnimationFrame(updateChrome);
  };
  listen(window, "scroll", updateChrome, { passive: true });
  listen(window, "resize", updateChrome);
  listen(window, "gazekeep:scene-ready", updateChrome);
  listen(document, "visibilitychange", updateChrome);
  updateChrome();
  disposers.push(() => cancelAnimationFrame(hitRaf));

  const fallbackTimeout = window.setTimeout.bind(window);
  const idle =
    "requestIdleCallback" in window ? window.requestIdleCallback(load, { timeout: 1500 }) : fallbackTimeout(load, 600);
  disposers.push(() => {
    clearTimeout(timeout);
    clearTimeout(renderTimeout);
    if ("cancelIdleCallback" in window) window.cancelIdleCallback(idle);
    else clearTimeout(idle);
  });
  disposers.push(initStoryScroll(sync, phoneDisplay.update, (route) => renderGate.ready(route)));
  async function shader() {
    const canvas = document.querySelector<HTMLCanvasElement>("#hero-shader")!;
    const hero = document.getElementById("hero")!;
    let visible = true;
    let instance: Awaited<ReturnType<(typeof import("shaders/js"))["createShader"]>> | undefined;
    let raf = 0,
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
    const io = new IntersectionObserver(
      ([entry]) => {
        visible = entry.isIntersecting && entry.intersectionRatio > 0.01;
        update();
      },
      { threshold: [0, 0.01] },
    );
    io.observe(hero);
    listen(document, "visibilitychange", update);
    listen(reduce, "change", update);
    update();
    disposers.push(() => {
      io.disconnect();
      cancelAnimationFrame(raf);
      instance?.destroy();
    });
    const palette = () =>
      document.documentElement.dataset.theme === "dark"
        ? { colorA: "#080d18", colorB: "#377e68" }
        : { colorA: "#f4f7fa", colorB: "#b4d5c7" };
    updateShaderTheme = () => {
      const colors = palette();
      instance?.update("gaze-gradient", colors);
      instance?.update("gaze-noise", colors);
      instance?.update("gaze-prism", { beamColor: colors.colorB });
      instance?.update("gaze-cursor", colors);
    };
    if (!("gpu" in navigator)) return;
    try {
      const { createShader } = await import("shaders/js");
      if (disposed) return;
      instance = await createShader(
        canvas,
        {
          components: heroShaderLayers(palette()),
        },
        {
          disableTelemetry: true,
          onError: (reason) => {
            canvas.dataset.failureReason = reason;
            canvas.dataset.fallback = "true";
            canvas.style.opacity = "0";
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
        canvas.dataset.fallback = "true";
        canvas.style.opacity = "0";
        return;
      }
      updateShaderTheme();
      update();
    } catch {
      canvas.dataset.fallback = "true";
      canvas.style.opacity = "0";
    }
  }
  void shader();
  listen(window, "pagehide", ((event: PageTransitionEvent) => {
    if (event.persisted) return;
    disposed = true;
    disposers.splice(0).forEach((dispose) => dispose());
    initialized = false;
  }) as EventListener);
}
