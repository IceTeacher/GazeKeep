import {
  type Bridge,
  createShowcaseState,
  createShowcaseAttempts,
  createRenderGate,
  createDriftTracker,
  chooseRuntime,
  liveStatusText,
} from "./showcase-state.ts";
import { initScreenTouch } from "./screen-touch.ts";
import { createAutoCycle } from "./demo-cycle.ts";
import { DEFAULT_SETTINGS, renderSettingCards, type DemoSettings } from "./setting-cards.ts";
import { initStoryScroll } from "./story-scroll";
import { initCameraHalo } from "./camera-halo";
import { initPhoneDisplay } from "./phone-display";
import { initHeroBackdrop } from "./hero-backdrop.ts";
import { initScrollReveal } from "./scroll-reveal.ts";
import { initSiteChrome } from "./site-chrome.ts";
import { initThemeSwitch } from "./theme-switch.ts";
import type { DemoState, KmpRoute, SiteContent } from "../data/schema";
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
    hint: { touch: string; mouse: string };
    tour: string;
    settingValues: SiteContent["settings"]["values"];
    loading: string;
  };
  const state = createShowcaseState();
  const renderGate = createRenderGate();
  const drift = createDriftTracker();
  let requestedRoute = "",
    requestedRevision = 0,
    rawBridge: Bridge | undefined,
    renderTimeout: ReturnType<typeof setTimeout> | undefined;
  const frame = document.querySelector<HTMLIFrameElement>("#kmp-frame")!;
  const screen = document.querySelector<HTMLElement>(".phone-screen")!;
  const phone = document.querySelector<HTMLElement>("#phone-chassis")!;
  const placeholder = document.querySelector<HTMLElement>("#phone-placeholder")!;
  const recovery = document.querySelector<HTMLButtonElement>("#retry-showcase")!;
  const message = document.querySelector<HTMLElement>("#load-message")!;
  const statusLabel = document.querySelector<HTMLElement>("#demo-status")!;
  // 主要输入为鼠标或触控板时按鼠标提示，触屏按触摸提示；外接或拔出指针设备时随之切换。
  const finePointer = matchMedia("(pointer: fine)");
  let shown: { route: KmpRoute; status: DemoState; scene: string } = { route: "home", status: "idle", scene: "none" };
  // 文案按「 · 」分段，窄屏折行只发生在分隔处，不拆开词语。
  const renderStatus = () => {
    const text = liveStatusText(content, {
      ...shown,
      ready: screen.dataset.ready === "true",
      finePointer: finePointer.matches,
    });
    if (statusLabel.textContent === text) return;
    statusLabel.replaceChildren(
      ...text.split(" · ").flatMap((part, i) => {
        const span = document.createElement("span");
        span.className = "status-part";
        span.textContent = part;
        return i ? [" · ", span] : [span];
      }),
    );
  };
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
  /** 访客操作内屏后，以转场回到当前段落指定的页面并恢复演示状态。 */
  function restore() {
    drift.clear();
    detect.release();
    backendCycle.release();
    if (rawBridge && requestedRoute) rawBridge.setScreen(requestedRoute, requestedRevision, true);
  }
  function show(route: KmpRoute, status: DemoState, effect: string, scene = shown.scene) {
    state.update({ route, status, scene });
    phone.dataset.effect = effect;
    shown = { route, status, scene };
    renderStatus();
  }
  const backendCards = [...document.querySelectorAll<HTMLButtonElement>("[data-backend]")];
  const pressed = () =>
    Math.max(
      0,
      backendCards.findIndex((c) => c.getAttribute("aria-pressed") === "true"),
    );
  const selectBackend = (index: number) => {
    backendCards.forEach((c, i) => c.setAttribute("aria-pressed", String(i === index)));
    show("backend", `backend_${backendCards[index].dataset.backend}` as DemoState, "none");
  };
  // 第一幕在检测中与检测成功间往复，运行方式在三种后端间轮换，均两秒一切换。
  const detect = createAutoCycle({
    count: 2,
    onChange: (i) => show("home", i ? "gaze_detected" : "sampling", i ? "none" : "camera-pulse"),
  });
  const backendCycle = createAutoCycle({ count: backendCards.length, onChange: selectBackend });
  disposers.push(() => {
    detect.stop();
    backendCycle.stop();
  });
  function sync(route: KmpRoute, status: DemoState, effect = "none", scene = "none") {
    if (scene !== shown.scene) {
      // 只停止离开的场景：进入前访客已悬停的卡片保持暂停。
      if (shown.scene === "detect") detect.stop();
      if (shown.scene === "backends") backendCycle.stop();
      if (scene === "detect") detect.start(0);
      if (scene === "backends") backendCycle.start(pressed());
    } else if (scene === "detect" && detect.index() === 1) {
      // 往复期间滚动微调不把检测成功拉回检测中。
      status = "gaze_detected";
      effect = "none";
    }
    show(route, status, effect, scene);
    if (drift.active()) restore();
  }
  const hovering = matchMedia("(hover:hover)");
  backendCards.forEach((card, i) => {
    // 访客选中后暂停轮换：悬停与键盘聚焦期间保持，离开后等待片刻从该项继续；触屏点按同样等待后继续。
    const activate = () => {
      backendCycle.hold(i);
      selectBackend(i);
      if (drift.active()) restore();
    };
    listen(card, "click", () => {
      activate();
      if (!hovering.matches) backendCycle.release();
    });
    listen(card, "focus", () => {
      if (card.matches(":focus-visible")) activate();
    });
    listen(card, "blur", () => backendCycle.release());
    listen(card, "mouseenter", () => {
      if (hovering.matches) activate();
    });
    listen(card, "mouseleave", () => {
      if (hovering.matches) backendCycle.release();
    });
  });
  const settingCards = [...document.querySelectorAll<HTMLElement>("[data-setting]")];
  const resetSettingCards = () => renderSettingCards(settingCards, DEFAULT_SETTINGS, "", content.settingValues);
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
    resetSettingCards();
    clearTimeout(timeout);
    clearTimeout(renderTimeout);
    screen.dataset.ready = "false";
    renderStatus();
    rawBridge = undefined;
    drift.clear();
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
    rawBridge = undefined;
    drift.clear();
    clearTimeout(renderTimeout);
    resetSettingCards();
    recovery.hidden = true;
    screen.dataset.ready = "false";
    renderStatus();
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
    rawBridge = bridge;
    state.attach({
      ...bridge,
      setScreen(route: string) {
        if (requestedRoute === route) return;
        requestedRoute = route;
        const revision = renderGate.request(route);
        requestedRevision = revision;
        bridge.setScreen(route, revision);
        clearTimeout(renderTimeout);
        renderTimeout = setTimeout(() => failed(event.detail.attempt), 8000);
      },
    });
    setAppearance();
    screen.dataset.ready = "true";
    renderStatus();
    recovery.hidden = true;
  }) as EventListener);
  listen(window, "gazekeep:rendered", ((event: CustomEvent<{ attempt: number; route: string; revision: number }>) => {
    const d = event.detail;
    if (!attempts.ready(d?.attempt) || !renderGate.accept(d.route, d.revision)) return;
    clearTimeout(renderTimeout);
    window.dispatchEvent(new Event("gazekeep:scene-ready"));
  }) as EventListener);
  // 设置区卡片随内屏演示同步取值，并标出正在演示的一项。
  listen(window, "gazekeep:demo", ((event: CustomEvent<DemoSettings & { attempt: number; focus: string }>) => {
    const d = event.detail;
    if (!attempts.ready(d?.attempt)) return;
    renderSettingCards(settingCards, d, d.focus, content.settingValues);
  }) as EventListener);
  listen(window, "gazekeep:error", ((event: CustomEvent<{ attempt: number }>) => {
    failed(event.detail?.attempt);
  }) as EventListener);
  listen(document.querySelector("#retry-showcase")!, "click", load);
  listen(finePointer, "change", renderStatus);
  const dim = document.querySelector<HTMLElement>(".screen-dim")!;
  initScreenTouch({
    layer: document.querySelector<HTMLElement>("#screen-touch")!,
    host: document.querySelector<HTMLElement>("#phone-visual")!,
    frame,
    // 黑屏换页、设备槽渐亮前或加载失败时，内屏不可见，不响应点按。
    active: () => screen.dataset.ready === "true" && recovery.hidden && Number(getComputedStyle(dim).opacity) < 0.5,
    // 访客操作内屏期间暂停机外轮换，复位后继续；内屏演示由展示页自行暂停。
    onInteract: () => {
      drift.mark(scrollY);
      detect.hold();
      backendCycle.hold();
    },
    listen,
  });
  listen(
    window,
    "scroll",
    () => {
      if (drift.shouldRestore(scrollY)) restore();
    },
    { passive: true },
  );
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
