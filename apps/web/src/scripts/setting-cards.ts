import type { SettingId, SiteContent } from "../data/schema";

/** 与内屏演示的设置字段一致，由展示页的 gazekeep:demo 事件回报。 */
export interface DemoSettings {
  leadSeconds: number;
  loose: boolean;
  holdMinutes: number;
  hideIndicator: boolean;
}

/** App 默认设置；内屏未就绪或加载失败时卡片保持这些取值。 */
export const DEFAULT_SETTINGS: DemoSettings = Object.freeze({
  leadSeconds: 8,
  loose: false,
  holdMinutes: 30,
  hideIndicator: false,
});

type Values = SiteContent["settings"]["values"];

export function settingValue(id: SettingId, s: DemoSettings, values: Values) {
  switch (id) {
    case "lead":
      return values.seconds.replace("{n}", String(s.leadSeconds));
    case "sensitivity":
      return s.loose ? values.loose : values.standard;
    case "max_hold":
      return values.minutes.replace("{n}", String(s.holdMinutes));
    case "hide_indicator":
      return s.hideIndicator ? values.on : values.off;
  }
}

/** 刻度环描绘比例（0–100）：提前量以 10 秒、上限以 60 分钟为满圈，宽松与开启描满。 */
export function settingArc(id: SettingId, s: DemoSettings) {
  const ratio = {
    lead: s.leadSeconds / 10,
    sensitivity: s.loose ? 1 : 0.5,
    max_hold: s.holdMinutes / 60,
    hide_indicator: s.hideIndicator ? 1 : 0,
  }[id];
  return Math.round(Math.min(1, Math.max(0, ratio)) * 100);
}

/** 按内屏回报更新机外卡片：取值、刻度环与正在演示的设置项。 */
export function renderSettingCards(cards: HTMLElement[], s: DemoSettings, focus: string, values: Values) {
  for (const card of cards) {
    const id = card.dataset.setting as SettingId;
    const value = card.querySelector<HTMLElement>(".setting-value")!;
    const text = settingValue(id, s, values);
    if (value.textContent !== text) value.textContent = text;
    card.style.setProperty("--arc", String(settingArc(id, s)));
    card.dataset.active = String(focus === id);
  }
}
