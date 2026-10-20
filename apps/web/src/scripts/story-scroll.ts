import {
  sceneState,
  storyPose,
  heroPose,
  selectSection,
  readingOpacity,
  mobileReadingLayout,
  screenDim,
  mobileStoryFollow,
} from "./story-motion.mjs";
import type { DemoState, KmpRoute } from "../data/schema";
import type { PhonePose } from "./phone-display";

/** 同一进度控制路由、姿态、遮罩与阅读停留；绘制回执只延迟揭示，不改变滚动位置。 */
export function initStoryScroll(
  sync: (route: KmpRoute, status: DemoState, effect?: string) => void,
  renderPhone: (pose: PhonePose) => void,
  isReady: (route: KmpRoute) => boolean,
) {
  const root = document.documentElement,
    stage = document.querySelector<HTMLElement>("#phone-stage")!,
    phone = document.querySelector<HTMLElement>("#phone-chassis")!;
  const story = document.querySelector<HTMLElement>(".story-steps")!,
    surface = document.querySelector<HTMLElement>(".story-sticky")!;
  const backendSlot = document.querySelector<HTMLElement>("#backends .device-slot")!;
  const steps = [...document.querySelectorAll<HTMLElement>(".story-step")];
  const hero = document.querySelector<HTMLElement>("#hero")!,
    backend = document.querySelector<HTMLElement>("#backends")!,
    whitelist = document.querySelector<HTMLElement>("#whitelist")!;
  const showcase = document.querySelector<HTMLElement>("#showcase-story")!;
  const reduced = matchMedia("(prefers-reduced-motion: reduce)");
  let compact = false,
    disposed = false,
    raf = 0,
    active = "",
    lastRoute = "home",
    lastDim = 0;
  const top = (e: HTMLElement) => e.getBoundingClientRect().top + scrollY;
  const clamp = (v: number) => Math.max(0, Math.min(1, v));
  function measure() {
    const mobile = innerWidth <= 760;
    let maxCopy = 0;
    for (const step of steps) {
      const probe = step.cloneNode(true) as HTMLElement;
      probe.style.cssText = `position:fixed;left:-10000px;top:0;width:${mobile ? innerWidth - 40 : Math.min(innerWidth, 1200) * 0.43}px;min-height:0;height:auto;padding:0;opacity:1;display:block;visibility:hidden`;
      document.body.append(probe);
      maxCopy = Math.max(maxCopy, probe.scrollHeight);
      probe.remove();
    }
    const layout = mobileReadingLayout(innerHeight, maxCopy);
    const phoneHeight = layout.phoneHeight;
    compact = reduced.matches || innerHeight < 600 || (mobile && layout.compact);
    root.dataset.compact = String(compact);
    root.style.setProperty("--mobile-phone-h", `${Math.max(210, phoneHeight)}px`);
    active = "";
    schedule();
  }
  function render() {
    raf = 0;
    if (disposed) return;
    const mobile = innerWidth <= 760,
      position = scrollY + innerHeight * 0.5;
    const centers = steps.map((e) => top(e) + e.offsetHeight * 0.5);
    let progress =
      mobile && !compact
        ? 0.5 + (scrollY - top(story)) / (innerHeight * 0.95)
        : 1 + (position - centers[0]) / (centers[1] - centers[0]);
    const selected = document.querySelector<HTMLElement>("[data-backend][aria-pressed=true]");
    const header = parseFloat(getComputedStyle(root).getPropertyValue("--header-h")) || 60;
    // 移动端设备槽在各自内容之后：后端槽完全离开视口后才切到范围页，避免可见跳变。
    const inWhitelist =
        mobile && !compact
          ? backendSlot.getBoundingClientRect().bottom < header && position >= top(whitelist) - innerHeight
          : position >= top(whitelist),
      inBackend = position >= top(backend);
    const scene = sceneState(progress, mobile, innerWidth);
    let route: KmpRoute = scene.route as KmpRoute,
      status = scene.status as DemoState,
      effect = scene.effect;
    if (inWhitelist) {
      route = "whitelist";
      status = "idle";
      effect = "none";
    } else if (inBackend || route === "backend") {
      route = "backend";
      status = `backend_${selected?.dataset.backend ?? "lsposed"}` as DemoState;
      effect = "none";
    }
    if (compact) {
      const i = selectSection([top(hero), ...steps.map(top), top(backend), top(whitelist)], position);
      const target = [hero, ...steps, backend, whitelist][i];
      route = target.dataset.route as KmpRoute;
      status = target.dataset.status as DemoState;
      effect = target.dataset.effect ?? "none";
    }
    const key = `${route}:${status}:${effect}`;
    const routeChanged = route !== lastRoute;
    if (key !== active) {
      sync(route, status, effect);
      active = key;
    }
    const ready = isReady(route),
      failed = document.querySelector<HTMLButtonElement>("#retry-showcase")?.hidden === false;
    // 隐私内容真正绘制后，才释放手部和回正设备。
    let pose = scene;
    if (route === "privacy" && progress < 2.15 && !ready && !failed)
      pose = { ...scene, ...storyPose(Math.min(progress, 1.45), mobile, innerWidth) };
    let dim = screenDim({
      progress,
      mobile,
      compact,
      inBackend,
      inWhitelist,
      slotTop: mobile && inBackend ? backendSlot.getBoundingClientRect().top : undefined,
      viewport: innerHeight,
      status,
    });
    const blackSwap = route === "backend" || (route === "privacy" && progress >= 2.15);
    if (!ready && !failed && (blackSwap || (routeChanged && lastDim > 0.95))) dim = 1;
    if (failed && status !== "dim_fallback") dim = 0;
    phone.style.setProperty("--screen-dim", String(dim));
    lastDim = dim;
    lastRoute = route;
    stage.style.setProperty("--device-x", String(mobile ? 0 : pose.x));
    const initial = heroPose((scrollY - hero.offsetHeight * 0.2) / (hero.offsetHeight * 0.8), mobile);
    const phoneHeight =
      mobile || compact
        ? parseFloat(root.style.getPropertyValue("--mobile-phone-h"))
        : Math.min(innerHeight * 0.69, 625);
    // 只写入与滚动无关的坐标：桌面交给 CSS sticky，移动端固定段用视口坐标，其余锚定文档。
    let mode: "sticky" | "fixed" | "absolute" = "absolute",
      y = 0,
      opacity = 1;
    const fade = (v: number) => clamp(v / 120);
    if (!mobile && !compact) mode = "sticky";
    else if (inBackend || inWhitelist) {
      const slot = (inWhitelist ? whitelist : backend).querySelector<HTMLElement>(".device-slot")!;
      const box = slot.getBoundingClientRect();
      y = top(slot) + Math.max(0, (slot.offsetHeight - phoneHeight) / 2);
      opacity = fade(innerHeight - box.top) * fade(box.bottom - header);
    } else if (compact) {
      y = top(document.querySelector<HTMLElement>(".hero-device-slot")!) + 16;
      opacity = position < top(story) ? 1 : 0;
    } else {
      const releaseAt = top(story) + story.offsetHeight - surface.offsetHeight - header;
      const follow = mobileStoryFollow(scrollY, top(story), releaseAt, header);
      if (follow.mode === "fixed") {
        mode = "fixed";
        y = follow.top;
        opacity = fade(scrollY - (top(story) - header));
      } else if (follow.mode === "absolute") y = follow.y;
      else {
        // 首屏设备槽随文档离开，进入导航下方前淡出，三幕面板固定后再淡入。
        const slot = document.querySelector<HTMLElement>(".hero-device-slot")!;
        y = top(slot) + 16;
        opacity = fade(slot.getBoundingClientRect().top + phoneHeight * 0.6 - header);
      }
    }
    if (mode === "sticky") stage.style.position = stage.style.top = "";
    else {
      stage.style.position = mode;
      stage.style.top = `${mode === "fixed" ? y : y - top(showcase)}px`;
    }
    stage.style.opacity = String(opacity);
    stage.style.setProperty("--phone-h", `${phoneHeight}px`);
    renderPhone(
      compact || inBackend || inWhitelist
        ? { rotateX: 0, rotateY: 0, rotateZ: 0 }
        : {
            rotateX: initial.rotateX + pose.rotateX,
            rotateY: initial.rotateY + pose.turn,
            rotateZ: initial.rotateZ + pose.rotateZ,
            gripProgress: pose.scene,
            scale: pose.scale,
          },
    );
    stage.style.setProperty("--grip-opacity", String(compact || inBackend || inWhitelist ? 0 : pose.grip));
    stage.dataset.gripVisible = String(pose.grip > 0.01 && !inBackend && !inWhitelist);
    steps.forEach((step, i) => {
      const alpha = compact ? 1 : readingOpacity(progress, i + 1);
      step.style.setProperty("--copy-opacity", String(alpha));
      step.style.opacity = String(alpha);
      step.inert = !compact && alpha < 0.05;
      step.classList.toggle("is-active", Math.round(progress) === i + 1);
    });
    stage.dataset.scene = String(progress);
  }
  function schedule() {
    if (!disposed && !raf) raf = requestAnimationFrame(render);
  }
  const observer = new ResizeObserver(measure);
  observer.observe(showcase);
  window.addEventListener("scroll", schedule, { passive: true });
  window.addEventListener("resize", measure);
  window.addEventListener("pageshow", measure);
  window.addEventListener("gazekeep:scene-ready", schedule);
  reduced.addEventListener("change", measure);
  document.fonts.ready.then(measure);
  measure();
  return () => {
    disposed = true;
    observer.disconnect();
    cancelAnimationFrame(raf);
    window.removeEventListener("scroll", schedule);
    window.removeEventListener("resize", measure);
    window.removeEventListener("pageshow", measure);
    window.removeEventListener("gazekeep:scene-ready", schedule);
    reduced.removeEventListener("change", measure);
  };
}
