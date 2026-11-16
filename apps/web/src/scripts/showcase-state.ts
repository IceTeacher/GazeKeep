export interface Bridge {
  /** restore 为 true 时丢弃访客在内屏中的操作，并以转场回到指定页面。 */
  setScreen(route: string, revision?: number, restore?: boolean): void;
  setDemoStatus(status: string): void;
  /** 内屏自动演示：tour、privacy、apps、settings 由展示页按脚本操作，其余场景停止并恢复当前页面。 */
  setDemoScene(scene: string): void;
  /** 手机离开视口或标签页隐藏时为 false：演示停在当前一步，回来后从原处继续。 */
  setDemoActive(active: boolean): void;
  setLocaleAndTheme(locale: string, dark: boolean): void;
}
export interface ShowcaseSnapshot {
  route: string;
  status: string;
  scene: string;
  active: boolean;
  locale: string;
  dark: boolean;
  phase: "loading" | "ready" | "error";
}
export interface NetworkInformationLike {
  saveData?: boolean;
  effectiveType?: string;
}

export type ShowcaseLoadMode = "idle" | "demand";

const SLOW_NETWORK_TYPES = new Set(["slow-2g", "2g", "3g"]);
const SHOWCASE_HASHES = new Set(["#workflow", "#backends", "#whitelist", "#settings"]);

/** Save-Data 或 2G/3G 等慢速网络下按需挂载 KMP 内屏，高速或未知网络在首屏空闲后预载。 */
export function resolveShowcaseLoadMode(connection?: NetworkInformationLike | null): ShowcaseLoadMode {
  if (!connection) return "idle";
  if (connection.saveData) return "demand";
  if (connection.effectiveType && SLOW_NETWORK_TYPES.has(connection.effectiveType)) return "demand";
  return "idle";
}

/** 已滚离首屏顶部或通过锚点直达展示区段时，不等待空闲或交互，立即触发 KMP 挂载。 */
export function shouldAutoLoadShowcase({ scrollY, hash }: { scrollY: number; hash: string }): boolean {
  return scrollY > 24 || SHOWCASE_HASHES.has(hash);
}

export const chooseRuntime = (supportsWasm: boolean) => (supportsWasm ? "wasm" : "js");
export function createShowcaseAttempts() {
  let current = 0;
  let failed = false;
  return {
    begin() {
      current += 1;
      failed = false;
      return current;
    },
    ready(attempt: number) {
      return attempt === current && !failed;
    },
    fail(attempt: number) {
      if (attempt !== current || failed) return false;
      failed = true;
      return true;
    },
  };
}
export function createShowcaseState() {
  let value: ShowcaseSnapshot = {
    route: "home",
    status: "idle",
    scene: "none",
    active: true,
    locale: "zh",
    dark: true,
    phase: "loading",
  };
  let bridge: Bridge | undefined;
  const replay = () => {
    if (!bridge) return;
    bridge.setLocaleAndTheme(value.locale, value.dark);
    bridge.setScreen(value.route);
    bridge.setDemoStatus(value.status);
    bridge.setDemoActive(value.active);
    bridge.setDemoScene(value.scene);
  };
  return {
    update(patch: Partial<Omit<ShowcaseSnapshot, "phase">>) {
      value = { ...value, ...patch };
      replay();
    },
    attach(next: Bridge) {
      bridge = next;
      value.phase = "ready";
      replay();
    },
    fail() {
      bridge = undefined;
      value.phase = "error";
    },
    loading() {
      bridge = undefined;
      value.phase = "loading";
    },
    snapshot() {
      return { ...value };
    },
  };
}

/** 仅当前路由请求的绘制回执可以揭开屏幕。 */
export function createRenderGate() {
  let revision = 0,
    route = "",
    rendered = false;
  return {
    request(next: string) {
      route = next;
      rendered = false;
      return ++revision;
    },
    accept(next: string, id: number) {
      if (next !== route || id !== revision) return false;
      rendered = true;
      return true;
    },
    ready(next: string) {
      return rendered && next === route;
    },
    reset() {
      rendered = false;
      revision++;
    },
  };
}

/** 访客操作内屏后记下滚动位置；页面滚动超过阈值时复位一次，避免点按时的细微抖动触发复位。 */
export function createDriftTracker(threshold = 24) {
  let origin: number | undefined;
  return {
    mark(scrollY: number) {
      origin = scrollY;
    },
    active() {
      return origin !== undefined;
    },
    clear() {
      origin = undefined;
    },
    shouldRestore(scrollY: number) {
      if (origin === undefined || Math.abs(scrollY - origin) < threshold) return false;
      origin = undefined;
      return true;
    },
  };
}

/**
 * 手机下方的状态文案。首屏（主页、空闲、无演示）且内屏可操作时，按主要输入方式提示如何操作内屏；
 * 工作流标题处内屏自动浏览，固定显示 tour 文案；其余阶段与内屏不可用时保留演示状态。
 */
export function liveStatusText(
  labels: { status: Record<string, string>; hint: { touch: string; mouse: string }; tour: string },
  {
    route,
    status,
    scene,
    ready,
    finePointer,
  }: { route: string; status: string; scene: string; ready: boolean; finePointer: boolean },
) {
  if (scene === "tour") return labels.tour;
  if (ready && route === "home" && status === "idle" && scene === "none")
    return finePointer ? labels.hint.mouse : labels.hint.touch;
  return labels.status[status];
}
