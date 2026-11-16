import {
  type Theme,
  type ThemePreference,
  nextPreference,
  readPreference,
  resolveTheme,
  writePreference,
} from "./theme.ts";
import { clickOrigin } from "./reveal-transition.ts";
import { transitionTheme } from "./theme-transition.ts";

export type ThemeLabels = { label: string } & Record<ThemePreference, string>;

function storage() {
  try {
    return localStorage;
  } catch {
    return undefined;
  }
}

/**
 * 主题按钮在 跟随系统 → 浅色 → 深色 间循环。根元素 data-theme 为实际明暗，
 * data-theme-preference 为偏好；跟随系统时监听系统变化。onChange 在每次实际应用后调用。
 */
export function initThemeSwitch(options: { onChange: () => void; frame?: HTMLIFrameElement | null }) {
  const root = document.documentElement;
  const button = document.querySelector<HTMLButtonElement>("#theme-toggle")!;
  let labels = JSON.parse(button.dataset.labels!) as ThemeLabels;
  const system = matchMedia("(prefers-color-scheme: dark)");
  const reduce = matchMedia("(prefers-reduced-motion: reduce)");
  const metas = document.querySelectorAll<HTMLMetaElement>('meta[name="theme-color"]');
  let preference: ThemePreference = readPreference(storage());

  const current = (): Theme => (root.dataset.theme === "dark" ? "dark" : "light");
  const syncButtonLabel = (next: ThemePreference) => {
    const name = labels[next];
    button.setAttribute("aria-label", `${labels.label}（${name}）`);
    button.title = name;
  };
  function apply(next: ThemePreference) {
    preference = next;
    root.dataset.themePreference = next;
    root.dataset.theme = resolveTheme(next, system.matches);
    syncButtonLabel(next);
    const bg = getComputedStyle(root).getPropertyValue("--bg").trim();
    if (bg) metas.forEach((meta) => (meta.content = bg));
    options.onChange();
  }

  const onClick = (event: MouseEvent) => {
    const next = nextPreference(preference);
    const from = current();
    // 偏好立即生效，连续点击按最新偏好循环；明暗与内屏换肤随过渡应用。
    preference = next;
    root.dataset.themePreference = next;
    writePreference(storage(), next);
    void transitionTheme({
      origin: clickOrigin(button, event),
      from,
      to: resolveTheme(next, system.matches),
      reducedMotion: reduce.matches,
      apply: () => apply(next),
      frame: options.frame,
    });
  };
  const onSystem = () => {
    if (preference === "system") apply("system");
  };
  apply(preference);
  button.addEventListener("click", onClick);
  system.addEventListener("change", onSystem);
  return {
    updateLabels(next: ThemeLabels) {
      labels = next;
      button.dataset.labels = JSON.stringify(next);
      syncButtonLabel(preference);
    },
    dispose() {
      button.removeEventListener("click", onClick);
      system.removeEventListener("change", onSystem);
    },
  };
}
