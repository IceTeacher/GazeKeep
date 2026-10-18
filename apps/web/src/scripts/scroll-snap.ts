import { buildSnapStops, nextSnapStop, createWheelGesture, shouldSnap } from "./snap-policy.mjs";

export function initScrollSnap(reduced: () => boolean) {
  const root = document.documentElement;
  const panels = [...document.querySelectorAll<HTMLElement>("#hero, #workflow, .story-step, #backends, #whitelist")];
  const boundary = document.getElementById("privacy")!;
  let snapEnd = 0;
  const markers = document.createElement("div");
  markers.className = "snap-markers";
  markers.setAttribute("aria-hidden", "true");
  document.body.append(markers);
  const cleanup: (() => void)[] = [];
  let stops: number[] = [];
  let moving = false,
    raf = 0,
    layoutRaf = 0,
    disposed = false;
  const gesture = createWheelGesture();
  function on(target: EventTarget, type: string, handler: EventListener, options?: AddEventListenerOptions) {
    target.addEventListener(type, handler, options);
    cleanup.push(() => target.removeEventListener(type, handler, options));
  }
  const syncMode = () => {
    if (scrollY < snapEnd - 2) root.dataset.snapEnabled = "true";
    else delete root.dataset.snapEnabled;
  };
  const measure = () => {
    snapEnd = Math.min(root.scrollHeight - innerHeight, Math.round(boundary.getBoundingClientRect().top + scrollY));
    if (!moving) syncMode();
    const next = buildSnapStops(
      panels.map((panel) => panel.getBoundingClientRect().top + scrollY),
      innerHeight,
      snapEnd,
    );
    if (next.length === stops.length && next.every((value, index) => value === stops[index])) return;
    stops = next;
    markers.replaceChildren(
      ...stops.map((top) => {
        const marker = document.createElement("i");
        marker.style.top = `${top}px`;
        return marker;
      }),
    );
  };
  const schedule = () => {
    cancelAnimationFrame(layoutRaf);
    layoutRaf = requestAnimationFrame(measure);
  };
  measure();
  syncMode();
  const resize = new ResizeObserver(schedule);
  resize.observe(document.body);
  on(window, "resize", schedule);
  on(window, "load", schedule);
  on(
    window,
    "scroll",
    () => {
      if (!moving) syncMode();
    },
    { passive: true },
  );
  function go(top: number) {
    cancelAnimationFrame(raf);
    const start = scrollY,
      distance = top - start;
    if (Math.abs(distance) < 2) {
      moving = false;
      delete root.dataset.snapMoving;
      syncMode();
      return;
    }
    moving = true;
    root.dataset.snapMoving = "true";
    window.dispatchEvent(new CustomEvent("gazekeep:snapstart", { detail: { top } }));
    const started = performance.now();
    const draw = (now: number) => {
      if (disposed) return;
      const p = reduced() ? 1 : Math.min(1, (now - started) / 780);
      const eased = p < 0.5 ? 4 * p * p * p : 1 - Math.pow(-2 * p + 2, 3) / 2;
      window.scrollTo({ top: start + distance * eased, behavior: "instant" });
      if (p < 1) {
        raf = requestAnimationFrame(draw);
        return;
      }
      moving = false;
      syncMode();
      delete root.dataset.snapMoving;
      root.dataset.snapIndex = String(stops.indexOf(top));
      window.dispatchEvent(new CustomEvent("gazekeep:snapend", { detail: { top } }));
    };
    raf = requestAnimationFrame(draw);
  }
  function nestedCanScroll(target: EventTarget | null, direction: number) {
    let element = target instanceof Element ? target : null;
    while (element && element !== document.body && element !== root) {
      const style = getComputedStyle(element);
      if (/auto|scroll/.test(style.overflowY) && element.scrollHeight > element.clientHeight + 1) {
        if (direction > 0 ? element.scrollTop + element.clientHeight < element.scrollHeight - 1 : element.scrollTop > 1)
          return true;
      }
      element = element.parentElement;
    }
    return false;
  }
  on(
    window,
    "wheel",
    ((event: WheelEvent) => {
      if (event.ctrlKey || event.metaKey || Math.abs(event.deltaX) > Math.abs(event.deltaY) || !event.deltaY) return;
      if (!moving && !shouldSnap(scrollY, Math.sign(event.deltaY), snapEnd)) return;
      if (nestedCanScroll(event.target, Math.sign(event.deltaY))) return;
      event.preventDefault();
      const delta = event.deltaY * (event.deltaMode === 1 ? 16 : event.deltaMode === 2 ? innerHeight : 1);
      const direction = gesture.push(delta, performance.now(), moving);
      if (direction) go(nextSnapStop(stops, scrollY, direction));
    }) as EventListener,
    { passive: false },
  );
  on(window, "keydown", ((event: KeyboardEvent) => {
    if (event.defaultPrevented || event.ctrlKey || event.metaKey || event.altKey) return;
    const target = event.target instanceof Element ? event.target : null;
    if (target?.closest("input,textarea,select,[contenteditable=true]")) return;
    if (event.key === " " && target?.closest("button,a,summary")) return;
    const direction = ["ArrowDown", "PageDown", " "].includes(event.key)
      ? event.shiftKey
        ? -1
        : 1
      : ["ArrowUp", "PageUp"].includes(event.key)
        ? -1
        : 0;
    if (!direction && event.key !== "Home" && event.key !== "End") return;
    if (direction && !moving && !shouldSnap(scrollY, direction, snapEnd)) return;
    if (direction && nestedCanScroll(target, direction)) return;
    event.preventDefault();
    if (moving) return;
    go(
      event.key === "Home"
        ? 0
        : event.key === "End"
          ? root.scrollHeight - innerHeight
          : nextSnapStop(stops, scrollY, direction),
    );
  }) as EventListener);
  let touch: { x: number; y: number; target: EventTarget | null; vertical: boolean } | null = null;
  on(
    window,
    "touchstart",
    ((event: TouchEvent) => {
      touch =
        event.touches.length === 1
          ? { x: event.touches[0].clientX, y: event.touches[0].clientY, target: event.target, vertical: false }
          : null;
    }) as EventListener,
    { passive: true },
  );
  on(
    window,
    "touchmove",
    ((event: TouchEvent) => {
      if (!touch || event.touches.length !== 1) return;
      const dy = touch.y - event.touches[0].clientY,
        dx = touch.x - event.touches[0].clientX;
      if (!moving && !shouldSnap(scrollY, Math.sign(dy), snapEnd)) return;
      if (Math.abs(dy) > 10 && Math.abs(dy) > Math.abs(dx) * 1.2 && !nestedCanScroll(touch.target, Math.sign(dy))) {
        touch.vertical = true;
        event.preventDefault();
      }
    }) as EventListener,
    { passive: false },
  );
  on(
    window,
    "touchend",
    ((event: TouchEvent) => {
      if (touch?.vertical && event.changedTouches.length && !moving) {
        const delta = touch.y - event.changedTouches[0].clientY;
        if (Math.abs(delta) > 40) go(nextSnapStop(stops, scrollY, Math.sign(delta)));
      }
      touch = null;
    }) as EventListener,
    { passive: true },
  );
  on(
    window,
    "touchcancel",
    () => {
      touch = null;
    },
    { passive: true },
  );
  on(document, "click", ((event: MouseEvent) => {
    const link = event.target instanceof Element ? event.target.closest<HTMLAnchorElement>('a[href^="#"]') : null;
    if (!link || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
    const target = document.getElementById(decodeURIComponent(link.hash.slice(1)));
    if (!target) return;
    event.preventDefault();
    measure();
    const top = target.getBoundingClientRect().top + scrollY;
    const nearest =
      top >= snapEnd
        ? Math.min(top, root.scrollHeight - innerHeight)
        : stops.reduce((best, value) => (Math.abs(value - top) < Math.abs(best - top) ? value : best), 0);
    history.replaceState(null, "", link.hash);
    go(nearest);
    if (link.classList.contains("skip-link")) {
      target.tabIndex = -1;
      target.focus({ preventScroll: true });
    }
  }) as EventListener);
  return () => {
    disposed = true;
    cancelAnimationFrame(raf);
    cancelAnimationFrame(layoutRaf);
    resize.disconnect();
    cleanup.forEach((fn) => fn());
    markers.remove();
    delete root.dataset.snapEnabled;
    delete root.dataset.snapMoving;
    delete root.dataset.snapIndex;
  };
}
