type Listen = (target: EventTarget, type: string, fn: EventListener, options?: AddEventListenerOptions) => void;

/** 页面外框交互：探索锚点、常见问题、移动端菜单、导航底色分区与重试按钮的命中层。 */
export function initSiteChrome(listen: Listen, onRetry?: () => void) {
  const reduce = matchMedia("(prefers-reduced-motion: reduce)");
  const explore = document.querySelector(".explore");
  const workflow = document.querySelector("#workflow");
  if (explore && workflow) {
    listen(explore, "click", ((event: MouseEvent) => {
      if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
      event.preventDefault();
      if (location.hash !== "#workflow") history.pushState(null, "", "#workflow");
      workflow.scrollIntoView({ behavior: reduce.matches ? "instant" : "smooth", block: "start" });
    }) as EventListener);
  }

  document.querySelectorAll<HTMLButtonElement>(".faq-trigger").forEach((button) =>
    listen(button, "click", () => {
      const open = button.getAttribute("aria-expanded") !== "true";
      button.setAttribute("aria-expanded", String(open));
      document.getElementById(button.getAttribute("aria-controls")!)!.inert = !open;
    }),
  );

  const menu = document.querySelector<HTMLButtonElement>("#menu-toggle")!;
  const nav = document.querySelector<HTMLElement>(".nav-wrap")!;
  const mobileMenu = document.querySelector<HTMLElement>("#mobile-menu");
  const setMenuOpen = (open: boolean) => {
    menu.setAttribute("aria-expanded", String(open));
    nav.dataset.menu = String(open);
    if (mobileMenu) mobileMenu.inert = !open;
  };
  const closeMenu = () => setMenuOpen(false);
  closeMenu();

  listen(menu, "click", () => {
    setMenuOpen(menu.getAttribute("aria-expanded") !== "true");
  });
  if (mobileMenu) {
    listen(mobileMenu, "click", ((e: MouseEvent) => {
      if ((e.target as Element | null)?.closest("a")) closeMenu();
    }) as EventListener);
  }
  listen(document, "click", ((e: MouseEvent) => {
    if (nav.dataset.menu === "true" && !nav.contains(e.target as Node)) closeMenu();
  }) as EventListener);
  listen(window, "keydown", ((e: KeyboardEvent) => {
    if (e.key === "Escape" && menu.getAttribute("aria-expanded") === "true") {
      closeMenu();
      menu.focus();
    }
  }) as EventListener);

  // 内屏中的重试按钮随 3D 投影变形，另以页面层的固定命中区承接点击。
  const retryHit = document.querySelector<HTMLButtonElement>("#retry-hit");
  if (retryHit && onRetry) listen(retryHit, "click", onRetry);
  let hitRaf = 0;
  const heroSection = document.getElementById("hero");
  const updateChrome = () => {
    cancelAnimationFrame(hitRaf);
    if (innerWidth > 1050 && nav.dataset.menu === "true") closeMenu();
    nav.dataset.scrolled = String(scrollY > 20);
    // 首屏完全离开导航后才切换为正文区导航底，着色区与底部渐变区都使用渐隐层，非首屏子页固定为正文底。
    nav.dataset.zone =
      heroSection && heroSection.getBoundingClientRect().bottom > nav.offsetHeight ? "hero" : "content";
    const button = document.querySelector<HTMLElement>("#retry-showcase");
    if (!retryHit || !button) return;
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
