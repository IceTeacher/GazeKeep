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
  /** 分享卡片图片（public/og/og-<locale>.png，1200×630）的替代文本。 */
  ogImageAlt: string;
  nav: string[];
  download: string;
  source: string;
  /** 主题切换按钮：label 为可访问名称前缀，其余为三种偏好的名称。 */
  theme: { label: string; system: string; light: string; dark: string };
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
  whitelist: { title: string; text: string; limit: string; detail: string };
  privacy: { title: string; text: string; labels: string[]; permission: string };
  start: { title: string; steps: { title: string; text: string }[] };
  faq: { title: string; items: { question: string; answer: string }[] };
  footer: { title: string; text: string; docs: string; license: string };
  demo: string;
  loading: string;
  error: string;
  retry: string;
  status: Record<DemoState, string>;
  /** 首屏与工作流标题处替代「准备就绪」，按主要输入方式说明如何操作内屏。 */
  hint: { touch: string; mouse: string };
  /** 页面结构里的零散短文案：区块眉题、无障碍标签与语言切换。 */
  ui: {
    skip: string;
    productNote: string;
    kickers: { workflow: string; backends: string; whitelist: string; privacy: string };
    limitLabel: string;
    limitLink: string;
    tagline: string;
    switchLanguage: string;
    languageShort: string;
    languageName: string;
  };
}
