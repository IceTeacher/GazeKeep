export type ThemePreference = "system" | "light" | "dark";
export type Theme = "light" | "dark";
type PreferenceStorage = Pick<Storage, "getItem" | "setItem" | "removeItem">;

export const THEME_STORAGE_KEY = "gazekeep-theme";
const ORDER: ThemePreference[] = ["system", "light", "dark"];

/** 读取主题偏好；无记录、非法值或无存储权限时跟随系统。 */
export function readPreference(storage: PreferenceStorage | undefined): ThemePreference {
  try {
    const saved = storage?.getItem(THEME_STORAGE_KEY);
    return saved === "light" || saved === "dark" ? saved : "system";
  } catch {
    return "system";
  }
}

/** 跟随系统不落盘，以便系统切换后仍随之变化。 */
export function writePreference(storage: PreferenceStorage | undefined, preference: ThemePreference) {
  try {
    if (preference === "system") storage?.removeItem(THEME_STORAGE_KEY);
    else storage?.setItem(THEME_STORAGE_KEY, preference);
  } catch {
    /* 无存储权限时仍可切换本页主题。 */
  }
}

export function nextPreference(preference: ThemePreference): ThemePreference {
  return ORDER[(ORDER.indexOf(preference) + 1) % ORDER.length]!;
}

export function resolveTheme(preference: ThemePreference, systemDark: boolean): Theme {
  return preference === "system" ? (systemDark ? "dark" : "light") : preference;
}
