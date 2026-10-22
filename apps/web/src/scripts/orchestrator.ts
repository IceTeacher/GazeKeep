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
import { initHeroBackdrop } from "./hero-backdrop.ts";
import { initScrollReveal } from "./scroll-reveal.ts";
import { initSiteChrome } from "./site-chrome.ts";
import { initThemeSwitch } from "./theme-switch.ts";
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
  const phoneDisplay = initPhoneDisplay();
  disposers.push(() => phoneDisplay.dispose());
  disposers.push(initCameraHalo());
  function listen(target: EventTarget, type: string, fn: EventListener, options?: AddEventListenerOptions) {
    target.addEventListener(type, fn, options);
    disposers.push(() => target.removeEventListener(type, fn, options));
  }
  const backdrop = initHeroBackdrop();
  disposers.push(() => backdrop.dispose());
  const setAppearance = () => {
    backdrop.updateTheme();
    state.update({ locale: content.locale, dark: document.documentElement.dataset.theme === "dark" });
    if (frame.contentDocument?.documentElement) frame.contentDocument.documentElement.lang = content.locale;
  };
  disposers.push(initThemeSwitch({ onChange: setAppearance, frame }));
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
  disposers.push(initSiteChrome(listen, load));
  disposers.push(initScrollReveal());

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
  listen(window, "pagehide", ((event: PageTransitionEvent) => {
    if (event.persisted) return;
    disposed = true;
    disposers.splice(0).forEach((dispose) => dispose());
    initialized = false;
  }) as EventListener);
}
