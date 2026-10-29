export interface Bridge {
  /** restore 为 true 时丢弃访客在内屏中的操作，并以转场回到指定页面。 */
  setScreen(route: string, revision?: number, restore?: boolean): void;
  setDemoStatus(status: string): void;
  setLocaleAndTheme(locale: string, dark: boolean): void;
}
export interface ShowcaseSnapshot {
  route: string;
  status: string;
  locale: string;
  dark: boolean;
  phase: "loading" | "ready" | "error";
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
  let value: ShowcaseSnapshot = { route: "home", status: "idle", locale: "zh", dark: true, phase: "loading" };
  let bridge: Bridge | undefined;
  const replay = () => {
    if (!bridge) return;
    bridge.setLocaleAndTheme(value.locale, value.dark);
    bridge.setScreen(value.route);
    bridge.setDemoStatus(value.status);
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
 * 手机下方的状态文案。首屏与工作流标题处（主页、空闲）且内屏可操作时，改为按主要输入方式提示如何操作内屏；
 * 其余阶段与内屏不可用时保留演示状态。
 */
export function liveStatusText(
  labels: { status: Record<string, string>; hint: { touch: string; mouse: string } },
  { route, status, ready, finePointer }: { route: string; status: string; ready: boolean; finePointer: boolean },
) {
  if (ready && route === "home" && status === "idle") return finePointer ? labels.hint.mouse : labels.hint.touch;
  return labels.status[status];
}
