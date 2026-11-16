import { initLocaleSwitch, type SitePage } from "./locale-switch.ts";
import { initSiteChrome } from "./site-chrome.ts";
import { initThemeSwitch } from "./theme-switch.ts";

let initialized = false;

export function initSubpage(page: SitePage = "changelog") {
  if (initialized) return;
  initialized = true;
  document.documentElement.dataset.phone = "ready";
  const disposers: (() => void)[] = [];
  function listen(target: EventTarget, type: string, fn: EventListener, options?: AddEventListenerOptions) {
    target.addEventListener(type, fn, options);
    disposers.push(() => target.removeEventListener(type, fn, options));
  }
  const themeSwitch = initThemeSwitch({ onChange: () => {} });
  disposers.push(themeSwitch.dispose);
  disposers.push(initSiteChrome(listen));
  disposers.push(
    initLocaleSwitch({
      page,
      onApplied(next) {
        themeSwitch.updateLabels(next.theme);
      },
    }),
  );
  listen(window, "pagehide", ((event: PageTransitionEvent) => {
    if (event.persisted) return;
    disposers.splice(0).forEach((dispose) => dispose());
    initialized = false;
  }) as EventListener);
}
