type Listen = (target: EventTarget, type: string, fn: EventListener, options?: AddEventListenerOptions) => void;

/** 页面外框交互：探索锚点、常见问题、移动端菜单、导航底色分区与重试按钮的命中层。 */
export function initSiteChrome(listen: Listen, onRetry: () => void) {
  const reduce = matchMedia("(prefers-reduced-motion: reduce)");
  listen(document.querySelector(".explore")!, "click", ((event: MouseEvent) => {
    if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
    event.preventDefault();
    if (location.hash !== "#workflow") history.pushState(null, "", "#workflow");
    document
      .querySelector("#workflow")!
      .scrollIntoView({ behavior: reduce.matches ? "instant" : "smooth", block: "start" });
  }) as EventListener);

  document.querySelectorAll<HTMLButtonElement>(".faq-trigger").forEach((button) =>
    listen(button, "click", () => {
      const open = button.getAttribute("aria-expanded") !== "true";
      button.setAttribute("aria-expanded", String(open));
      document.getElementById(button.getAttribute("aria-controls")!)!.inert = !open;
    }),
  );

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

  // 内屏中的重试按钮随 3D 投影变形，另以页面层的固定命中区承接点击。
  const retryHit = document.querySelector<HTMLButtonElement>("#retry-hit")!;
  listen(retryHit, "click", onRetry);
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
  return () => cancelAnimationFrame(hitRaf);
}
