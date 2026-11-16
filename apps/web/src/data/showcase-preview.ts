import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import type { Locale } from "./schema";

function findRepoRoot(): string {
  for (const seed of [path.dirname(fileURLToPath(import.meta.url)), process.cwd()]) {
    let dir = seed;
    for (;;) {
      if (existsSync(path.join(dir, "apps/app/android/build.gradle.kts"))) return dir;
      const parent = path.dirname(dir);
      if (parent === dir) break;
      dir = parent;
    }
  }
  throw new Error("未找到 GazeKeep 仓库根目录");
}

const repoRoot = findRepoRoot();

/** 从 android/build.gradle.kts 读取与 App、展示入口同源的版本号，避免官网预渲染界面版本号漂移。 */
export function readAppVersionName(): string {
  const gradle = readFileSync(path.join(repoRoot, "apps/app/android/build.gradle.kts"), "utf8");
  const versionName = /versionName\s*=\s*"([^"]+)"/.exec(gradle)?.[1];
  if (!versionName) throw new Error("apps/app/android/build.gradle.kts 缺少 versionName");
  return `v${versionName}`;
}

/** 解析 Android strings.xml 为键值映射。 */
export function parseAndroidStrings(xml: string): Record<string, string> {
  const map: Record<string, string> = {};
  const pattern = /<string\b[^>]*\bname="([^"]+)"[^>]*>([\s\S]*?)<\/string>/g;
  for (const match of xml.matchAll(pattern)) {
    map[match[1]] = match[2]
      .trim()
      .replaceAll("&amp;", "&")
      .replaceAll("&lt;", "<")
      .replaceAll("&gt;", ">")
      .replaceAll("&quot;", '"')
      .replaceAll("\\'", "'");
  }
  return map;
}

function pickString(strings: Record<string, string>, key: string): string {
  const value = strings[key];
  if (!value) throw new Error(`strings.xml 缺少词条：${key}`);
  return value;
}

export interface ShowcaseHomePreview {
  title: string;
  working: string;
  lastDecision: string;
  backendName: string;
  appVersionLabel: string;
  appVersionValue: string;
  backendStatusLabel: string;
  backends: { name: string; status: string }[];
  cameraSelfTestLabel: string;
  cameraChip: { label: string; value: string };
  selfTestChip: { label: string; value: string };
  androidVersionLabel: string;
  deviceModelLabel: string;
  deviceModelValue: string;
  fingerprintLabel: string;
  navHome: string;
  navSettings: string;
}

/** 构建与真实 Compose HomeScreen 一致的首屏即时预渲染数据（直接复用 App 词条与版本单一事实来源）。 */
export function getShowcaseHomePreview(locale: Locale): ShowcaseHomePreview {
  const folder = locale === "en" ? "values-en" : "values";
  const xml = readFileSync(
    path.join(repoRoot, `apps/app/shared/src/commonMain/composeResources/${folder}/strings.xml`),
    "utf8",
  );
  const strings = parseAndroidStrings(xml);
  const ready = pickString(strings, "home_chip_ready");
  const lsposed = pickString(strings, "settings_lsposed");
  return {
    title: pickString(strings, "app_title"),
    working: pickString(strings, "home_status_working"),
    lastDecision: pickString(strings, "home_status_decision").replace(
      "%1$s",
      pickString(strings, "home_decision_pending"),
    ),
    backendName: lsposed,
    appVersionLabel: pickString(strings, "home_info_app_version"),
    appVersionValue: readAppVersionName(),
    backendStatusLabel: pickString(strings, "home_info_backend_status"),
    backends: [
      { name: lsposed, status: ready },
      { name: pickString(strings, "settings_shizuku"), status: ready },
      { name: pickString(strings, "settings_accessibility"), status: ready },
    ],
    cameraSelfTestLabel: pickString(strings, "home_info_camera_selftest"),
    cameraChip: {
      label: pickString(strings, "home_chip_camera"),
      value: pickString(strings, "home_chip_granted"),
    },
    selfTestChip: {
      label: pickString(strings, "home_chip_selftest"),
      value: pickString(strings, "home_chip_passed"),
    },
    androidVersionLabel: pickString(strings, "home_info_android_version"),
    deviceModelLabel: pickString(strings, "home_info_device_model"),
    deviceModelValue: "GazeKeep Demo",
    fingerprintLabel: pickString(strings, "home_info_fingerprint"),
    navHome: pickString(strings, "nav_home"),
    navSettings: pickString(strings, "nav_settings"),
  };
}
