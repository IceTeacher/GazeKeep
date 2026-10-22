import {
  type Theme,
  type ThemePreference,
  nextPreference,
  readPreference,
  resolveTheme,
  writePreference,
} from "./theme.ts";
import { transitionTheme } from "./theme-transition.ts";

type Labels = { label: string } & Record<ThemePreference, string>;

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
  const labels = JSON.parse(button.dataset.labels!) as Labels;
  const system = matchMedia("(prefers-color-scheme: dark)");
  const reduce = matchMedia("(prefers-reduced-motion: reduce)");
  const metas = document.querySelectorAll<HTMLMetaElement>('meta[name="theme-color"]');
  let preference: ThemePreference = readPreference(storage());

  const current = (): Theme => (root.dataset.theme === "dark" ? "dark" : "light");
  function apply(next: ThemePreference) {
    preference = next;
    root.dataset.themePreference = next;
    root.dataset.theme = resolveTheme(next, system.matches);
    const name = labels[next];
    button.setAttribute("aria-label", `${labels.label}（${name}）`);
    button.title = name;
    const bg = getComputedStyle(root).getPropertyValue("--bg").trim();
    if (bg) metas.forEach((meta) => (meta.content = bg));
    options.onChange();
  }

  const onClick = (event: MouseEvent) => {
    const next = nextPreference(preference);
    writePreference(storage(), next);
    // 键盘触发的点击没有指针坐标，从按钮中心扩散。
    const box = button.getBoundingClientRect();
    const origin =
      event.detail > 0
        ? { x: event.clientX, y: event.clientY }
        : { x: box.left + box.width / 2, y: box.top + box.height / 2 };
    void transitionTheme({
      origin,
      from: current(),
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
  return () => {
    button.removeEventListener("click", onClick);
    system.removeEventListener("change", onSystem);
  };
}
