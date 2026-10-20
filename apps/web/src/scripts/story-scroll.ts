import {
  sceneState,
  storyPose,
  heroPose,
  selectSection,
  readingOpacity,
  mobileReadingLayout,
  mobileFeaturePhoneHeight,
  mobileStoryProgress,
  mobileStorySnaps,
  screenDim,
  mobileStoryFollow,
  featurePose,
  featureSpan,
  featureReveal,
  followPose,
  type FollowPose,
} from "./story-motion.ts";
import type { DemoState, KmpRoute } from "../data/schema";
import type { PhonePose } from "./phone-display";

/** 同一进度控制路由、姿态、遮罩与阅读停留；绘制回执只延迟揭示，不改变滚动位置。
 * 文档坐标在尺寸变化时缓存，滚动帧只读 scrollY 并在值变化时写入。 */
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
  const steps = [...document.querySelectorAll<HTMLElement>(".story-step")],
    snapMarks = [...document.querySelectorAll<HTMLElement>(".story-snap")];
  const hero = document.querySelector<HTMLElement>("#hero")!,
    backend = document.querySelector<HTMLElement>("#backends")!,
    whitelist = document.querySelector<HTMLElement>("#whitelist")!;
  const showcase = document.querySelector<HTMLElement>("#showcase-story")!;
  const heroSlot = document.querySelector<HTMLElement>(".hero-device-slot")!,
    whitelistSlot = whitelist.querySelector<HTMLElement>(".device-slot")!,
    backendList = backend.querySelector<HTMLElement>(".backend-list")!,
    whitelistHeading = whitelist.querySelector<HTMLElement>(".section-heading")!,
    whitelistCopy = whitelist.querySelector<HTMLElement>(".feature-copy")!;
  const reduced = matchMedia("(prefers-reduced-motion: reduce)");
  let compact = false,
    disposed = false,
    raf = 0,
    active = "",
    lastRoute = "home",
    lastDim = 0,
    lastTime = 0,
    shown: FollowPose | undefined;
  let geometry = {
    header: 60,
    heroTop: 0,
    heroHeight: 1,
    storyTop: 0,
    storyHeight: 0,
    surfaceHeight: 0,
    showcaseTop: 0,
    backendTop: 0,
    whitelistTop: 0,
    stepTops: [] as number[],
    stepCenters: [] as number[],
    heroSlotTop: 0,
    backendSlotTop: 0,
    backendSlotHeight: 0,
    whitelistSlotTop: 0,
    whitelistSlotHeight: 0,
    backendListBottom: 0,
    whitelistHeadingTop: 0,
    stageTop: 0,
    snapTop: 0,
  };
  const written = new Map<string, string>();
  /** 只在值变化时写入，避免无变化的样式失效。 */
  const write = (key: string, value: string, apply: () => void) => {
    if (written.get(key) === value) return;
    written.set(key, value);
    apply();
  };
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
    root.style.setProperty("--mobile-feature-phone-h", `${mobileFeaturePhoneHeight(innerHeight)}px`);
    const bottom = (e: HTMLElement) => e.getBoundingClientRect().bottom + scrollY;
    // 移动端吸附标记按实际视口高度定位，抵消吸附内边距后停靠点即整数幕。
    const snapPad = parseFloat(getComputedStyle(root).scrollPaddingTop) || 0;
    mobileStorySnaps(innerHeight, snapMarks.length).forEach((offset, i) => {
      snapMarks[i].style.top = `${offset + snapPad}px`;
    });
    hero.style.setProperty("--hero-snap-extend", `${Math.max(0, top(story) - bottom(hero))}px`);
    geometry = {
      header: parseFloat(getComputedStyle(root).getPropertyValue("--header-h")) || 60,
      heroTop: top(hero),
      heroHeight: hero.offsetHeight,
      storyTop: top(story),
      storyHeight: story.offsetHeight,
      surfaceHeight: surface.offsetHeight,
      showcaseTop: top(showcase),
      backendTop: top(backend),
      whitelistTop: top(whitelist),
      stepTops: steps.map(top),
      stepCenters: steps.map((e) => top(e) + e.offsetHeight * 0.5),
      heroSlotTop: top(heroSlot),
      backendSlotTop: top(backendSlot),
      backendSlotHeight: backendSlot.offsetHeight,
      whitelistSlotTop: top(whitelistSlot),
      whitelistSlotHeight: whitelistSlot.offsetHeight,
      backendListBottom: bottom(backendList),
      whitelistHeadingTop: top(whitelistHeading),
      stageTop: parseFloat(getComputedStyle(stage).top) || 0,
      // 吸附停靠时文案距视口顶部的距离；未启用吸附时为 0。
      snapTop: parseFloat(getComputedStyle(root).scrollPaddingTop) || 0,
    };
    active = "";
    written.clear();
    shown = undefined;
    schedule();
  }
  function render(now = performance.now()) {
    raf = 0;
    if (disposed) return;
    const L = geometry,
      mobile = innerWidth <= 760,
      position = scrollY + innerHeight * 0.5,
      header = L.header;
    const centers = L.stepCenters;
    let progress =
      mobile && !compact
        ? mobileStoryProgress(scrollY, L.storyTop, innerHeight)
        : 1 + (position - centers[0]) / (centers[1] - centers[0]);
    const selected = document.querySelector<HTMLElement>("[data-backend][aria-pressed=true]");
    // 移动端设备槽在各自内容之后：后端槽完全离开视口后才切到范围页，避免可见跳变。
    const inWhitelist =
        mobile && !compact
          ? L.backendSlotTop + L.backendSlotHeight - scrollY < header && position >= L.whitelistTop - innerHeight
          : position >= L.whitelistTop,
      inBackend = position >= L.backendTop;
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
      const i = selectSection([L.heroTop, ...L.stepTops, L.backendTop, L.whitelistTop], position);
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
      slotTop: mobile && inBackend ? L.backendSlotTop - scrollY : undefined,
      viewport: innerHeight,
      status,
    });
    const blackSwap = route === "backend" || (route === "privacy" && progress >= 2.15);
    if (!ready && !failed && (blackSwap || (routeChanged && lastDim > 0.95))) dim = 1;
    if (failed && status !== "dim_fallback") dim = 0;
    write("dim", String(dim), () => phone.style.setProperty("--screen-dim", String(dim)));
    lastDim = dim;
    lastRoute = route;
    const desktop = !mobile && !compact;
    const initial = heroPose((scrollY - L.heroHeight * 0.2) / (L.heroHeight * 0.8), mobile);
    // 移动端后端与范围设备槽不与正文同屏，使用更大的槽位高度；切换发生在槽位进入视口前的透明阶段。
    const phoneHeight =
      mobile && !compact && (inBackend || inWhitelist)
        ? parseFloat(root.style.getPropertyValue("--mobile-feature-phone-h"))
        : mobile || compact
          ? parseFloat(root.style.getPropertyValue("--mobile-phone-h"))
          : Math.min(innerHeight * 0.69, 625);
    // 只写入与滚动无关的坐标：桌面交给 CSS sticky，移动端固定段用视口坐标，其余锚定文档。
    let mode: "sticky" | "fixed" | "absolute" = "absolute",
      y = 0,
      opacity = 1;
    const fade = (v: number) => clamp(v / 120);
    // 桌面运行方式到专注范围：运行方式内容离开设备所在高度后起步，范围文案停靠时到达左侧。
    if (desktop && inBackend) {
      const span = featureSpan({
        listBottom: L.backendListBottom,
        headingTop: L.whitelistHeadingTop,
        stageTop: L.stageTop,
        phoneHeight,
        snapTop: L.snapTop,
        viewport: innerHeight,
      });
      pose = { ...pose, ...featurePose((scrollY - span.start) / (span.end - span.start), innerWidth) };
    }
    if (desktop) mode = "sticky";
    else if (inBackend || inWhitelist) {
      const slotTop = inWhitelist ? L.whitelistSlotTop : L.backendSlotTop,
        slotHeight = inWhitelist ? L.whitelistSlotHeight : L.backendSlotHeight;
      y = slotTop + Math.max(0, (slotHeight - phoneHeight) / 2);
      opacity = fade(innerHeight - (slotTop - scrollY)) * fade(slotTop + slotHeight - scrollY - header);
    } else if (compact) {
      y = L.heroSlotTop + 16;
      opacity = position < L.storyTop ? 1 : 0;
    } else {
      const releaseAt = L.storyTop + L.storyHeight - L.surfaceHeight - header;
      const follow = mobileStoryFollow(scrollY, L.storyTop, releaseAt, header);
      if (follow.mode === "fixed") {
        mode = "fixed";
        y = follow.top;
        opacity = fade(scrollY - (L.storyTop - header));
      } else if (follow.mode === "absolute") y = follow.y;
      else {
        // 首屏设备槽随文档离开，进入导航下方前淡出，三幕面板固定后再淡入。
        y = L.heroSlotTop + 16;
        opacity = fade(L.heroSlotTop - scrollY + phoneHeight * 0.6 - header);
      }
    }
    const stageTop = mode === "sticky" ? "" : `${mode === "fixed" ? y : y - L.showcaseTop}px`;
    write("position", `${mode}:${stageTop}`, () => {
      stage.style.position = mode === "sticky" ? "" : mode;
      stage.style.top = stageTop;
    });
    write("opacity", String(opacity), () => (stage.style.opacity = String(opacity)));
    write("phone-h", String(phoneHeight), () => stage.style.setProperty("--phone-h", `${phoneHeight}px`));
    const flat = compact || ((inBackend || inWhitelist) && !desktop);
    const target = flat
      ? { x: 0, turn: 0, rotateX: 0, rotateZ: 0, scale: 1 }
      : inBackend || inWhitelist
        ? { x: pose.x, turn: pose.turn, rotateX: pose.rotateX, rotateZ: pose.rotateZ, scale: pose.scale }
        : {
            x: pose.x,
            turn: initial.rotateY + pose.turn,
            rotateX: initial.rotateX + pose.rotateX,
            rotateZ: initial.rotateZ + pose.rotateZ,
            scale: pose.scale,
          };
    // 横移与旋转短阻尼跟随，抹平滚轮台阶；纵向仍由 sticky 决定。
    // 静止后再次滚动按一帧计时，不因空闲间隔直接对齐；测量后 shown 清空才直接对齐。
    shown = followPose(shown, target, lastTime ? Math.min(now - lastTime, 32) : 16);
    lastTime = now;
    const shift = desktop ? (shown.x * Math.min(innerWidth, 1200)) / 2 : 0;
    // 范围文案跟随手机实际位置淡入，桌面专注范围之外保持样式表默认。
    const reveal = desktop && inBackend ? featureReveal(shown.x, innerWidth).toFixed(3) : "";
    write("whitelist-reveal", reveal, () => (whitelistCopy.style.opacity = reveal));
    write("shift", shift.toFixed(2), () => stage.style.setProperty("--device-shift", `${shift.toFixed(2)}px`));
    const gripOpacity = flat || inBackend || inWhitelist ? 0 : pose.grip;
    renderPhone({
      rotateX: shown.rotateX,
      rotateY: shown.turn,
      rotateZ: shown.rotateZ,
      gripProgress: flat || inBackend || inWhitelist ? undefined : pose.scene,
      scale: shown.scale,
      shift,
    });
    write("grip", String(gripOpacity), () => stage.style.setProperty("--grip-opacity", String(gripOpacity)));
    write("grip-visible", String(gripOpacity > 0.01), () => (stage.dataset.gripVisible = String(gripOpacity > 0.01)));
    steps.forEach((step, i) => {
      const alpha = compact ? 1 : readingOpacity(progress, i + 1);
      write(`step-${i}`, String(alpha), () => {
        step.style.setProperty("--copy-opacity", String(alpha));
        step.style.opacity = String(alpha);
      });
      const inert = !compact && alpha < 0.05;
      if (step.inert !== inert) step.inert = inert;
      step.classList.toggle("is-active", Math.round(progress) === i + 1);
    });
    stage.dataset.scene = String(progress);
    if ((["x", "turn", "rotateX", "rotateZ", "scale"] as const).some((k) => shown![k] !== target[k])) schedule();
  }
  function schedule() {
    if (!disposed && !raf) raf = requestAnimationFrame((now) => render(now));
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
