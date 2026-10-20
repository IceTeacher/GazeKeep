/** @param {boolean} supportsWasm */
export const chooseRuntime = (supportsWasm) => (supportsWasm ? "wasm" : "js");
export function createShowcaseAttempts() {
  let current = 0;
  let failed = false;
  return {
    begin() {
      current += 1;
      failed = false;
      return current;
    },
    ready(attempt) {
      return attempt === current && !failed;
    },
    fail(attempt) {
      if (attempt !== current || failed) return false;
      failed = true;
      return true;
    },
  };
}
export function createShowcaseState() {
  let value = { route: "home", status: "idle", locale: "zh", dark: true, phase: "loading" };
  let bridge;
  const replay = () => {
    if (!bridge) return;
    bridge.setLocaleAndTheme(value.locale, value.dark);
    bridge.setScreen(value.route);
    bridge.setDemoStatus(value.status);
  };
  return {
    update(patch) {
      value = { ...value, ...patch };
      replay();
    },
    attach(next) {
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
    request(next) {
      route = next;
      rendered = false;
      return ++revision;
    },
    accept(next, id) {
      if (next !== route || id !== revision) return false;
      rendered = true;
      return true;
    },
    ready(next) {
      return rendered && next === route;
    },
    reset() {
      rendered = false;
      revision++;
    },
  };
}
