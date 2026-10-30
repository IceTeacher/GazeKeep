export type Locale = "zh" | "en";
export type SettingId = "lead" | "sensitivity" | "max_hold" | "hide_indicator";
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
  whitelist: { title: string; text: string };
  /** 设置区：机外卡片与内屏演示同步显示当前取值。 */
  settings: {
    title: string;
    text: string;
    items: { id: SettingId; label: string; detail: string }[];
    /** 取值文案；seconds 与 minutes 中的 {n} 替换为数值。 */
    values: { seconds: string; minutes: string; standard: string; loose: string; on: string; off: string };
  };
  privacy: { title: string; text: string; labels: string[]; permission: string };
  start: { title: string; steps: { title: string; text: string }[] };
  faq: { title: string; items: { question: string; answer: string }[] };
  footer: { title: string; text: string; docs: string; license: string };
  demo: string;
  loading: string;
  error: string;
  retry: string;
  status: Record<DemoState, string>;
  /** 首屏替代「准备就绪」，按主要输入方式说明如何操作内屏。 */
  hint: { touch: string; mouse: string };
  /** 工作流标题处内屏自动浏览时手机下方的说明。 */
  tour: string;
  /** 页面结构里的零散短文案：区块眉题、无障碍标签与语言切换。 */
  ui: {
    skip: string;
    productNote: string;
    kickers: { workflow: string; backends: string; whitelist: string; settings: string; privacy: string };
    tagline: string;
    switchLanguage: string;
    languageShort: string;
    languageName: string;
  };
}
