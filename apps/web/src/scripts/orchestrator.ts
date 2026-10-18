import { createShowcaseState, createShowcaseAttempts, chooseRuntime } from "./showcase-state.mjs";
import { initScrollSnap } from "./scroll-snap";
import type { DemoState, KmpRoute } from "../data/schema";
interface Bridge {
  setScreen(route: string): void;
  setDemoStatus(status: string): void;
  setLocaleAndTheme(locale: string, dark: boolean): void;
}
type DemoWindow = Window & { GazeKeepWebShowcaseBridge?: Bridge };
let initialized = false;
export function initWebsite() {
  if (initialized) return;
  initialized = true;
  const disposers: (() => void)[] = [];
  const transitions = new Set<Animation>();
  disposers.push(() => transitions.forEach((animation) => animation.cancel()));
  let disposed = false;
  const content = JSON.parse(document.querySelector("#showcase-content")!.textContent!) as {
    locale: string;
    status: Record<DemoState, string>;
    loading: string;
  };
  const state = createShowcaseState();
  const frame = document.querySelector<HTMLIFrameElement>("#kmp-frame")!;
  const screen = document.querySelector<HTMLElement>(".phone-screen")!;
  const phone = document.querySelector<HTMLElement>("#phone-chassis")!;
  const placeholder = document.querySelector<HTMLElement>("#phone-placeholder")!;
  const recovery = document.querySelector<HTMLElement>(".demo-recovery")!;
  const message = document.querySelector<HTMLElement>("#load-message")!;
  const statusLabel = document.querySelector<HTMLElement>("#demo-status")!;
  const reduce = matchMedia("(prefers-reduced-motion: reduce)");
  const systemTheme = matchMedia("(prefers-color-scheme: dark)");
  // 固定真实排版视口，再缩放显示；外壳变窄时不改变 Compose 的 dp 布局。
  const viewport = document.querySelector<HTMLElement>(".phone-viewport")!;
  const fitViewport = () => {
    const scale = Math.min(viewport.clientWidth / 392, viewport.clientHeight / 784);
    frame.style.setProperty("--kmp-scale", String(scale));
  };
  const viewportObserver = new ResizeObserver(fitViewport);
  viewportObserver.observe(viewport);
  fitViewport();
  disposers.push(() => viewportObserver.disconnect());
  disposers.push(initScrollSnap(() => reduce.matches));
  function listen(target: EventTarget, type: string, fn: EventListener, options?: AddEventListenerOptions) {
    target.addEventListener(type, fn, options);
    disposers.push(() => target.removeEventListener(type, fn, options));
  }
  const setAppearance = () => {
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
    ["mouseenter", "focus", "click"].forEach((event) => listen(card, event, activate));
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
    if (runtime === "wasm") {
      runtime = "js";
      load();
      return;
    }
    state.fail();
    recovery.hidden = false;
    message.textContent = content.locale === "zh" ? "界面暂时不可用" : "Interface unavailable";
    placeholder.querySelector(".loader")?.setAttribute("hidden", "");
  }
  function load() {
    if (disposed) return;
    clearTimeout(timeout);
    state.loading();
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
    state.attach(bridge);
    setAppearance();
    screen.dataset.ready = "true";
    recovery.hidden = true;
  }) as EventListener);
  listen(window, "gazekeep:error", ((event: CustomEvent<{ attempt: number }>) => {
    failed(event.detail?.attempt);
  }) as EventListener);
  listen(document.querySelector("#retry-showcase")!, "click", load);
  const fallbackTimeout = window.setTimeout.bind(window);
  const idle =
    "requestIdleCallback" in window ? window.requestIdleCallback(load, { timeout: 1500 }) : fallbackTimeout(load, 600);
  disposers.push(() => {
    clearTimeout(timeout);
    if ("cancelIdleCallback" in window) window.cancelIdleCallback(idle);
    else clearTimeout(idle);
  });
  // 以一个设备实例贯穿桌面叙事，移动端保持完整内容与自然滚动。
  const targets = document.querySelectorAll<HTMLElement>("#hero, .story-step, #backends, #whitelist");
  const observer = new IntersectionObserver(
    (entries) => {
      const visible = entries
        .filter((e) => e.isIntersecting)
        .sort((a, b) => b.intersectionRatio - a.intersectionRatio)[0];
      if (!visible) return;
      const el = visible.target as HTMLElement;
      document.querySelectorAll(".story-step").forEach((step) => step.classList.toggle("is-active", step === el));
      if (el.id === "backends") {
        const selected = el.querySelector<HTMLElement>("[aria-pressed=true]");
        sync("backend", `backend_${selected?.dataset.backend ?? "lsposed"}` as DemoState);
      } else sync(el.dataset.route as KmpRoute, el.dataset.status as DemoState, el.dataset.effect);
    },
    { rootMargin: "-25% 0px -35% 0px", threshold: [0, 0.2, 0.5, 1] },
  );
  targets.forEach((el) => observer.observe(el));
  disposers.push(() => observer.disconnect());
  async function motion() {
    if (reduce.matches) return;
    const [{ default: gsap }, { ScrollTrigger }] = await Promise.all([import("gsap"), import("gsap/ScrollTrigger")]);
    if (disposed) return;
    gsap.registerPlugin(ScrollTrigger);
    const media = gsap.matchMedia();
    media.add("(prefers-reduced-motion: no-preference)", () => {
      const context = gsap.context(() => {
        gsap.matchMedia().add("(min-width: 761px)", () => {
          gsap.to(phone, {
            rotateX: 0,
            rotateY: 0,
            rotateZ: 0,
            ease: "none",
            scrollTrigger: { trigger: "#hero", start: "top top", end: "bottom 30%", scrub: true },
          });
        });
      });
      return () => {
        context.revert();
      };
    });
    disposers.push(() => media.revert());
  }
  listen(window, "gazekeep:snapstart", ((event: CustomEvent<{ top: number }>) => {
    if (reduce.matches) return;
    const top = event.detail.top;
    document
      .querySelectorAll<HTMLElement>("h1, .section-heading, .story-step h3, .story-step p, .faq-section > h2")
      .forEach((el) => {
        const position = el.getBoundingClientRect().top + scrollY;
        if (position >= top - 20 && position < top + innerHeight) {
          const animation = el.animate(
            [
              { opacity: 0.65, transform: "translateY(16px)" },
              { opacity: 1, transform: "translateY(0)" },
            ],
            { duration: 700, easing: "cubic-bezier(.22,1,.36,1)" },
          );
          transitions.add(animation);
          animation.finished.then(
            () => transitions.delete(animation),
            () => transitions.delete(animation),
          );
        }
      });
  }) as EventListener);
  void motion().catch(() => {
    // 动效资源加载失败时继续使用原生滚动与步骤观察器。
  });
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
      instance?.update("gaze-gradient", { angle: Math.sin(elapsed * 0.4) * 55 });
      raf = requestAnimationFrame(tick);
    };
    const update = () => {
      const running = visible && !document.hidden && !reduce.matches;
      hero.dataset.motionPaused = String(!running);
      cancelAnimationFrame(raf);
      last = 0;
      if (instance) {
        running ? instance.resume() : instance.pause();
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
    if (reduce.matches || !("gpu" in navigator)) return;
    try {
      const { createShader } = await import("shaders/js");
      if (disposed) return;
      instance = await createShader(canvas, {
        components: [
          { id: "gaze-gradient", type: "LinearGradient", props: { colorA: "#080d18", colorB: "#293359" } },
          { type: "SimplexNoise", props: { scale: 1.7, speed: 0.65 } },
          { type: "CursorTrail", props: { radius: 0.22, softness: 0.85 } },
        ],
      });
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
