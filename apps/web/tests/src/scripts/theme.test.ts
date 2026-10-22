import { describe, expect, test } from "vitest";
import {
  THEME_STORAGE_KEY,
  nextPreference,
  readPreference,
  resolveTheme,
  writePreference,
} from "../../../src/scripts/theme.ts";

function memoryStorage(initial: Record<string, string> = {}) {
  const data = new Map(Object.entries(initial));
  return {
    getItem: (key: string) => data.get(key) ?? null,
    setItem: (key: string, value: string) => void data.set(key, value),
    removeItem: (key: string) => void data.delete(key),
    data,
  };
}
const throwing = {
  getItem: () => {
    throw new Error("denied");
  },
  setItem: () => {
    throw new Error("denied");
  },
  removeItem: () => {
    throw new Error("denied");
  },
};

describe("主题偏好", () => {
  test("无记录、非法值或存储不可用时默认跟随系统", () => {
    expect(readPreference(memoryStorage())).toBe("system");
    expect(readPreference(memoryStorage({ [THEME_STORAGE_KEY]: "sepia" }))).toBe("system");
    expect(readPreference(throwing)).toBe("system");
    expect(readPreference(undefined)).toBe("system");
  });

  test("沿用旧版保存的明暗值", () => {
    expect(readPreference(memoryStorage({ [THEME_STORAGE_KEY]: "dark" }))).toBe("dark");
    expect(readPreference(memoryStorage({ [THEME_STORAGE_KEY]: "light" }))).toBe("light");
  });

  test("按 跟随系统 → 浅色 → 深色 → 跟随系统 循环", () => {
    expect(nextPreference("system")).toBe("light");
    expect(nextPreference("light")).toBe("dark");
    expect(nextPreference("dark")).toBe("system");
  });

  test("跟随系统时由系统明暗决定，显式选择不受系统影响", () => {
    expect(resolveTheme("system", true)).toBe("dark");
    expect(resolveTheme("system", false)).toBe("light");
    expect(resolveTheme("light", true)).toBe("light");
    expect(resolveTheme("dark", false)).toBe("dark");
  });

  test("跟随系统不落盘，显式选择写入存储，存储失败不抛出", () => {
    const storage = memoryStorage({ [THEME_STORAGE_KEY]: "dark" });
    writePreference(storage, "system");
    expect(storage.data.has(THEME_STORAGE_KEY)).toBe(false);
    writePreference(storage, "light");
    expect(storage.data.get(THEME_STORAGE_KEY)).toBe("light");
    expect(() => writePreference(throwing, "dark")).not.toThrow();
  });
});
