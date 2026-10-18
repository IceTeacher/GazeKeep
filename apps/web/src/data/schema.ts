export type Locale = "zh" | "en";
export type KmpRoute = "home" | "backend" | "settings" | "whitelist" | "privacy";
export type DemoState =
  | "idle"
  | "sampling"
  | "gaze_detected"
  | "dim_fallback"
  | "backend_lsposed"
  | "backend_shizuku"
  | "backend_accessibility";
export interface StoryStep {
  title: string;
  text: string;
  route: KmpRoute;
  state: DemoState;
  effect: "none" | "camera-pulse" | "screen-glow" | "screen-dim";
}
export interface SiteContent {
  locale: Locale;
  title: string;
  description: string;
  nav: string[];
  download: string;
  source: string;
  theme: string;
  menu: string;
  hero: { title: string[]; subtitle: string; pills: string[]; explore: string };
  story: { title: string; intro: string; steps: StoryStep[] };
  backends: {
    title: string;
    intro: string;
    items: {
      id: "lsposed" | "shizuku" | "accessibility";
      name: string;
      tag: string;
      text: string;
      requirement: string;
    }[];
  };
  whitelist: { title: string; text: string; categories: string[]; limit: string; detail: string };
  privacy: { title: string; text: string; labels: string[]; permission: string };
  start: { title: string; steps: { title: string; text: string }[] };
  faq: { title: string; items: { question: string; answer: string }[] };
  footer: { title: string; text: string; docs: string; license: string };
  demo: string;
  loading: string;
  error: string;
  retry: string;
  status: Record<DemoState, string>;
}
