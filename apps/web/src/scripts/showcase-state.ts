export interface Bridge {
  setScreen(route: string, revision?: number): void;
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
